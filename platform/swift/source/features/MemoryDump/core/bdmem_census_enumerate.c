// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// The only census code that runs with a malloc zone locked. Nothing here may allocate, take a
// lock, call into the ObjC runtime or make a syscall: an allocation from the locked zone
// deadlocks the process.

#include "bdmem_census.h"

#include <malloc/malloc.h>
#include <mach/mach.h>
#include <string.h>

/// The enumerated addresses are this process's own, so no copy is needed.
/// Zones that are never locked. With a web view open, WebKit's zone stayed locked for 9.9 s and
/// its enumerator reported nothing: the zone is private to WebKit.
static const char *const bdmem_skipped_zones[] = {"WebKit Malloc"};

static kern_return_t bdmem_identity_reader(task_t task, vm_address_t remote_address,
                                           vm_size_t size, void **local_memory) {
    (void)task;
    (void)size;
    *local_memory = (void *)remote_address;
    return KERN_SUCCESS;
}

static void bdmem_record_unclassified(bdmem_census_summary_t *summary, uint64_t size) {
    summary->unclassified_blocks++;
    summary->unclassified_bytes += size;
}

/// Accumulates a first word that matched no known class. Returns false when the table is full.
static bool bdmem_record_candidate(bdmem_census_t *census, uintptr_t metadata, uint64_t size) {
    uint32_t index = bdmem_candidate_slot(metadata);
    // A hard probe limit keeps a saturated table from turning each insert into a full scan while
    // the zone is locked.
    for (uint32_t probe = 0; probe < BDMEM_CANDIDATE_PROBE_LIMIT; probe++) {
        bdmem_candidate_t *candidate = &census->candidates[index];
        if (candidate->metadata == metadata) {
            candidate->blocks++;
            candidate->bytes += size;
            return true;
        }
        if (candidate->metadata == 0) {
            candidate->metadata = metadata;
            candidate->blocks = 1;
            candidate->bytes = size;
            return true;
        }
        index = (index + 1) & (BDMEM_CANDIDATE_CAPACITY - 1);
    }
    return false;
}

/// Remembers a named block as an owner source. Its memory is read only after the locks.
static void bdmem_record_named(bdmem_census_t *census, uintptr_t address, uint64_t size,
                               int32_t class_index) {
    if (census->named == NULL) {
        return;
    }
    if (census->named_count == BDMEM_OWNER_NAMED_CAPACITY) {
        census->summary.owner_table_overflow++;
        return;
    }
    census->named[census->named_count++] = (bdmem_named_block_t){
        .address = address,
        .size = size > UINT32_MAX ? UINT32_MAX : (uint32_t)size,
        .class_index = class_index,
    };
}

static bdmem_unnamed_block_t *bdmem_record_unnamed(bdmem_census_t *census, uintptr_t address,
                                                   uint64_t size) {
    if (census->unnamed == NULL) {
        return NULL;
    }
    if (census->unnamed_count == BDMEM_OWNER_UNNAMED_CAPACITY) {
        census->summary.owner_table_overflow++;
        return NULL;
    }
    bdmem_unnamed_block_t *block = &census->unnamed[census->unnamed_count++];
    *block = (bdmem_unnamed_block_t){.address = address, .size = size, .owner = -1};
    return block;
}

static void bdmem_record_block(bdmem_census_t *census, uintptr_t address, uint64_t size) {
    bdmem_census_summary_t *summary = &census->summary;
    summary->total_blocks++;
    summary->total_bytes += size;
    if (size > summary->largest_block_bytes) {
        summary->largest_block_bytes = size;
    }
    if (census->current_zone < BDMEM_MAX_ZONES) {
        summary->zones[census->current_zone].blocks++;
        summary->zones[census->current_zone].bytes += size;
    }

    if (size < sizeof(uintptr_t) || address == 0) {
        bdmem_record_unclassified(summary, size);
        summary->unclassified_histogram[bdmem_size_bucket(size)]++;
        return;
    }

    // The block is live while its zone is locked, so its first word can be read directly.
    const uintptr_t first_word = *(const uintptr_t *)address;
    const uintptr_t candidate = first_word & BDMEM_ISA_MASK;

    const int32_t class_index = bdmem_census_find_class(census, candidate);
    // A block smaller than its class cannot be an instance of it.
    if (class_index >= 0 && census->class_sizes[class_index] <= size) {
        census->class_blocks[class_index]++;
        census->class_bytes[class_index] += size;
        summary->named_blocks++;
        summary->named_bytes += size;
        bdmem_record_named(census, address, size, class_index);
        return;
    }

    if (first_word == BDMEM_SWIFT_KIND_HEAP_LOCAL_VARIABLE ||
        first_word == BDMEM_SWIFT_KIND_HEAP_GENERIC_LOCAL_VARIABLE ||
        first_word == BDMEM_SWIFT_KIND_ERROR_OBJECT) {
        summary->swift_box_blocks++;
        summary->swift_box_bytes += size;
        return;
    }

    bdmem_record_unclassified(summary, size);
    bdmem_unnamed_block_t *unnamed = bdmem_record_unnamed(census, address, size);

    // Only first words pointing into readable memory are worth resolving; anything else would
    // saturate the table with buffer contents.
    const bool plausible = candidate > 0x1000 && (candidate & 0x7) == 0 &&
                           bdmem_census_readable_end(census, candidate) != 0;
    if (plausible) {
        if (bdmem_record_candidate(census, candidate, size)) {
            if (unnamed != NULL) {
                unnamed->candidate = candidate;
            }
            // The histogram is completed after resolution, from the candidates left unnamed.
            return;
        }
        summary->candidate_overflow++;
    }
    summary->unclassified_histogram[bdmem_size_bucket(size)]++;
}

static void bdmem_range_recorder(task_t task, void *context, unsigned type, vm_range_t *ranges,
                                 unsigned count) {
    (void)task;
    bdmem_census_t *census = context;
    bdmem_census_summary_t *summary = &census->summary;

    if (census->current_zone < BDMEM_MAX_ZONES) {
        summary->zones[census->current_zone].seen = true;
    }
    if (ranges == NULL) {
        return;
    }

    // Checked per batch: batches carry hundreds of blocks.
    if (!census->over_budget && bdmem_now_ns() >= census->deadline_ns) {
        census->over_budget = true;
        summary->truncated = true;
        if (census->current_zone < BDMEM_MAX_ZONES) {
            summary->zones[census->current_zone].truncated = true;
        }
    }
    if (census->over_budget) {
        summary->skipped_blocks += count;
        return;
    }

    // Bitwise: large allocations report MALLOC_PTR_IN_USE_RANGE_TYPE | MALLOC_PTR_REGION_RANGE_TYPE,
    // and comparing for equality drops every block above ~16 KB.
    if ((type & MALLOC_PTR_IN_USE_RANGE_TYPE) == 0) {
        return;
    }
    for (unsigned index = 0; index < count; index++) {
        bdmem_record_block(census, (uintptr_t)ranges[index].address, (uint64_t)ranges[index].size);
    }
}

static bool bdmem_zone_is_skipped(const char *name) {
    for (size_t index = 0; index < sizeof(bdmem_skipped_zones) / sizeof(*bdmem_skipped_zones);
         index++) {
        if (strcmp(bdmem_skipped_zones[index], name) == 0) {
            return true;
        }
    }
    return false;
}

static bool bdmem_zone_is_enumerable(const malloc_zone_t *zone) {
    return zone != NULL && zone->introspect != NULL && zone->introspect->enumerator != NULL &&
           zone->introspect->force_lock != NULL && zone->introspect->force_unlock != NULL;
}

void bdmem_census_enumerate(bdmem_census_t *census) {
    bdmem_census_summary_t *summary = &census->summary;

    vm_address_t *zone_addresses = NULL;
    unsigned zone_count = 0;
    if (malloc_get_all_zones(mach_task_self(), bdmem_identity_reader, &zone_addresses,
                             &zone_count) != KERN_SUCCESS) {
        return;
    }
    if (zone_count > BDMEM_MAX_ZONES) {
        zone_count = BDMEM_MAX_ZONES;
    }
    summary->zone_count = zone_count;

    uint32_t zones_left = 0;
    for (unsigned index = 0; index < zone_count; index++) {
        malloc_zone_t *zone = (malloc_zone_t *)zone_addresses[index];
        const char *name = zone != NULL && zone->zone_name != NULL ? zone->zone_name : "(unnamed)";
        strlcpy(summary->zones[index].name, name, sizeof(summary->zones[index].name));
        summary->zones[index].skipped = bdmem_zone_is_skipped(summary->zones[index].name);
        zones_left += !summary->zones[index].skipped && bdmem_zone_is_enumerable(zone);
    }

    // Each zone gets an equal share of what is left. A zone that overruns its share cannot be
    // interrupted, but the overrun is not taken from the zones after it.
    uint64_t budget_left_ns = census->options.budget_ms * 1000000ull;

    // One zone locked at a time. A zone's enumerator may allocate from another zone (e.g. the nano
    // zone does), and with that zone locked too the process aborts on a recursive lock.
    // This may cause some allocations to be lost (pre or after locking that zone), but it's safer
    for (unsigned index = 0; index < zone_count; index++) {
        malloc_zone_t *zone = (malloc_zone_t *)zone_addresses[index];
        bdmem_zone_summary_t *zone_summary = &summary->zones[index];
        if (zone_summary->skipped || !bdmem_zone_is_enumerable(zone)) {
            continue;
        }

        const uint64_t share_ns = budget_left_ns / zones_left;
        zones_left--;
        if (share_ns == 0) {
            zone_summary->truncated = true;
            summary->truncated = true;
            continue;
        }

        census->current_zone = index;
        census->over_budget = false;
        const uint64_t wait_started = bdmem_now_ns();
        zone->introspect->force_lock(zone);
        const uint64_t held_from = bdmem_now_ns();
        census->deadline_ns = held_from + share_ns;

        zone->introspect->enumerator(mach_task_self(), census, MALLOC_PTR_IN_USE_RANGE_TYPE,
                                     (vm_address_t)zone, bdmem_identity_reader,
                                     bdmem_range_recorder);

        zone->introspect->force_unlock(zone);
        const uint64_t held_ns = bdmem_now_ns() - held_from;

        budget_left_ns -= held_ns < share_ns ? held_ns : share_ns;
        summary->lock_wait_ns += held_from - wait_started;
        summary->lock_held_ns += held_ns;
        zone_summary->lock_held_ns = held_ns;
        zone_summary->enumerated = true;
    }
}
