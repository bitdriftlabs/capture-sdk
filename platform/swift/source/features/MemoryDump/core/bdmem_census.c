// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#include "bdmem_census.h"

#include <mach/mach.h>
#include <objc/runtime.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <time.h>

uint64_t bdmem_now_ns(void) {
    return clock_gettime_nsec_np(CLOCK_UPTIME_RAW);
}

uint32_t bdmem_size_bucket(uint64_t size) {
    uint32_t bucket = 0;
    while (size > 1 && bucket < BDMEM_SIZE_BUCKETS - 1) {
        size >>= 1;
        bucket++;
    }
    return bucket;
}

static int bdmem_compare_pointers(const void *lhs, const void *rhs) {
    const uintptr_t left = *(const uintptr_t *)lhs;
    const uintptr_t right = *(const uintptr_t *)rhs;
    return (left > right) - (left < right);
}

bdmem_census_t *bdmem_census_create(const bdmem_census_options_t *options) {
    if (options == NULL) {
        return NULL;
    }

    unsigned int class_count = 0;
    Class *classes = objc_copyClassList(&class_count);
    if (classes == NULL || class_count == 0) {
        free(classes);
        return NULL;
    }

    bdmem_census_t *census = calloc(1, sizeof(*census));
    if (census == NULL) {
        free(classes);
        return NULL;
    }
    census->options = *options;
    // The names are borrowed from the caller: keep copies instead.
    for (uint32_t index = 0;
         index < options->skipped_zone_count && census->skipped_zone_count < BDMEM_MAX_ZONES;
         index++) {
        if (options->skipped_zone_names != NULL && options->skipped_zone_names[index] != NULL) {
            strlcpy(census->skipped_zone_names[census->skipped_zone_count++],
                    options->skipped_zone_names[index], BDMEM_ZONE_NAME_CAPACITY);
        }
    }
    census->options.skipped_zone_names = NULL;
    census->options.skipped_zone_count = 0;

    // One reservation for every table the run writes into, so enumeration never touches malloc.
    const size_t count = class_count;
    const size_t pointers_size = count * sizeof(uintptr_t);
    const size_t sizes_size = count * sizeof(uint32_t);
    const size_t names_size = count * sizeof(const char *);
    const size_t tallies_size = count * sizeof(uint64_t);
    const size_t candidates_size = BDMEM_CANDIDATE_CAPACITY * sizeof(bdmem_candidate_t);
    const size_t ranges_size = BDMEM_RANGE_CAPACITY * sizeof(bdmem_range_t);
    const size_t total = pointers_size + names_size + tallies_size * 4 + candidates_size +
                         ranges_size + sizes_size;

    void *tables = mmap(NULL, total, PROT_READ | PROT_WRITE, MAP_ANON | MAP_PRIVATE, -1, 0);
    if (tables == MAP_FAILED) {
        free(classes);
        free(census);
        return NULL;
    }
    census->tables = tables;
    census->tables_size = total;

    // Eight-byte fields first so every table stays aligned.
    char *cursor = tables;
    census->class_pointers = (uintptr_t *)cursor;
    cursor += pointers_size;
    census->class_names = (const char **)cursor;
    cursor += names_size;
    census->class_blocks = (uint64_t *)cursor;
    cursor += tallies_size;
    census->class_bytes = (uint64_t *)cursor;
    cursor += tallies_size;
    census->class_owned_blocks = (uint64_t *)cursor;
    cursor += tallies_size;
    census->class_owned_bytes = (uint64_t *)cursor;
    cursor += tallies_size;
    census->candidates = (bdmem_candidate_t *)cursor;
    cursor += candidates_size;
    census->ranges = (bdmem_range_t *)cursor;
    cursor += ranges_size;
    census->class_sizes = (uint32_t *)cursor;

    for (size_t index = 0; index < count; index++) {
        census->class_pointers[index] = (uintptr_t)classes[index];
    }
    free(classes);
    qsort(census->class_pointers, count, sizeof(uintptr_t), bdmem_compare_pointers);

    for (size_t index = 0; index < count; index++) {
        Class cls = (Class)(void *)census->class_pointers[index];
        census->class_sizes[index] = (uint32_t)class_getInstanceSize(cls);
        census->class_names[index] = class_getName(cls);
    }
    census->class_count = (uint32_t)count;

    for (uint32_t index = 0; index < BDMEM_CANDIDATE_CAPACITY; index++) {
        census->candidates[index].kind = BDMEM_CANDIDATE_UNRESOLVED;
    }

    census->images = bdmem_image_table_create();
    return census;
}

void bdmem_census_destroy(bdmem_census_t *census) {
    if (census == NULL) {
        return;
    }
    bdmem_image_table_destroy(census->images);
    if (census->owner_tables != NULL) {
        munmap(census->owner_tables, census->owner_tables_size);
    }
    free(census->names);
    free(census->types);
    if (census->tables != NULL) {
        munmap(census->tables, census->tables_size);
    }
    free(census);
}

void bdmem_census_survey_ranges(bdmem_census_t *census) {
    census->range_count = 0;

    const task_t task = mach_task_self();
    vm_address_t address = 0;
    natural_t depth = 0;

    while (census->range_count < BDMEM_RANGE_CAPACITY) {
        vm_size_t size = 0;
        vm_region_submap_short_info_data_64_t info;
        mach_msg_type_number_t count =
            (mach_msg_type_number_t)(sizeof(info) / sizeof(natural_t));

        if (vm_region_recurse_64(task, &address, &size, &depth, (vm_region_recurse_info_t)&info,
                                 &count) != KERN_SUCCESS ||
            size == 0) {
            break;
        }
        if (info.is_submap) {
            depth++;
            continue;
        }

        if ((info.protection & VM_PROT_READ) != 0) {
            const uintptr_t start = (uintptr_t)address;
            const uintptr_t end = start + (uintptr_t)size;
            bdmem_range_t *last =
                census->range_count > 0 ? &census->ranges[census->range_count - 1] : NULL;
            if (last != NULL && last->end == start) {
                last->end = end;
            } else {
                census->ranges[census->range_count++] = (bdmem_range_t){start, end};
            }
        }
        address += size;
    }
}

uintptr_t bdmem_census_readable_end(const bdmem_census_t *census, uintptr_t address) {
    uint32_t low = 0;
    uint32_t high = census->range_count;
    while (low < high) {
        const uint32_t middle = low + (high - low) / 2;
        const bdmem_range_t *range = &census->ranges[middle];
        if (address < range->start) {
            high = middle;
        } else if (address >= range->end) {
            low = middle + 1;
        } else {
            return range->end;
        }
    }
    return 0;
}

int32_t bdmem_census_find_class(const bdmem_census_t *census, uintptr_t pointer) {
    uint32_t low = 0;
    uint32_t high = census->class_count;
    while (low < high) {
        const uint32_t middle = low + (high - low) / 2;
        const uintptr_t value = census->class_pointers[middle];
        if (value == pointer) {
            return (int32_t)middle;
        }
        if (value < pointer) {
            low = middle + 1;
        } else {
            high = middle;
        }
    }
    return -1;
}

/// Lists the classes and candidates that ended up with a name, for indexed reads from Swift.
static bool bdmem_census_index_types(bdmem_census_t *census) {
    size_t count = 0;
    for (uint32_t index = 0; index < census->class_count; index++) {
        count += census->class_blocks[index] > 0;
    }
    for (uint32_t index = 0; index < BDMEM_CANDIDATE_CAPACITY; index++) {
        count += census->candidates[index].kind != BDMEM_CANDIDATE_UNRESOLVED;
    }

    census->types = calloc(count > 0 ? count : 1, sizeof(*census->types));
    if (census->types == NULL) {
        return false;
    }
    for (uint32_t index = 0; index < census->class_count; index++) {
        if (census->class_blocks[index] > 0) {
            census->types[census->type_count++] = (bdmem_type_ref_t){index, false};
        }
    }
    for (uint32_t index = 0; index < BDMEM_CANDIDATE_CAPACITY; index++) {
        if (census->candidates[index].kind != BDMEM_CANDIDATE_UNRESOLVED) {
            census->types[census->type_count++] = (bdmem_type_ref_t){index, true};
        }
    }
    return true;
}

bool bdmem_census_run(bdmem_census_t *census) {
    if (census == NULL || census->has_run) {
        return false;
    }
    census->has_run = true;

    // Syscalls and allocations happen here, before any zone is locked: the recorder filters
    // candidates against this survey and writes into these tables without making any.
    bdmem_census_survey_ranges(census);
    if (census->options.attribute_owners) {
        bdmem_census_reserve_owner_tables(census);
    }
    bdmem_census_enumerate(census);
    bdmem_census_resolve(census);
    bdmem_census_attribute_owners(census);
    return bdmem_census_index_types(census);
}

size_t bdmem_census_type_count(const bdmem_census_t *census) {
    return census != NULL ? census->type_count : 0;
}

bool bdmem_census_type_at(const bdmem_census_t *census, size_t index, bdmem_type_entry_t *out) {
    if (census == NULL || out == NULL || index >= census->type_count) {
        return false;
    }

    const bdmem_type_ref_t ref = census->types[index];
    if (!ref.is_candidate) {
        *out = (bdmem_type_entry_t){
            .kind = BDMEM_TYPE_CLASS,
            .name = census->class_names[ref.index],
            .class_pointer = census->class_pointers[ref.index],
            .blocks = census->class_blocks[ref.index],
            .bytes = census->class_bytes[ref.index],
            .owned_blocks = census->class_owned_blocks[ref.index],
            .owned_bytes = census->class_owned_bytes[ref.index],
        };
        return true;
    }

    const bdmem_candidate_t *candidate = &census->candidates[ref.index];
    *out = (bdmem_type_entry_t){
        .kind = (bdmem_type_kind_t)candidate->kind,
        .name = candidate->kind == BDMEM_TYPE_CXX ? census->names + candidate->name_offset
                                                  : candidate->name,
        .class_pointer = candidate->kind == BDMEM_TYPE_CLASS ? candidate->metadata : 0,
        .blocks = candidate->blocks,
        .bytes = candidate->bytes,
    };
    return true;
}

const bdmem_census_summary_t *bdmem_census_summary(const bdmem_census_t *census) {
    return census != NULL ? &census->summary : NULL;
}
