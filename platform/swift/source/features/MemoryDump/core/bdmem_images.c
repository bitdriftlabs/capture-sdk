// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#include "bdmem_images.h"

#include <mach-o/dyld.h>
#include <mach-o/loader.h>
#include <stdlib.h>

typedef struct {
    uintptr_t start;
    uintptr_t end;
    uint32_t image;
} bdmem_segment_t;

struct bdmem_image_table {
    bdmem_segment_t *segments;
    uint32_t segment_count;
    const char **names;
    uint32_t image_count;
};

typedef void (*bdmem_segment_visitor_t)(bdmem_image_table_t *table, uint32_t image,
                                        uintptr_t start, uintptr_t end, uint32_t *count);

static void bdmem_count_segment(bdmem_image_table_t *table, uint32_t image,
                                uintptr_t start, uintptr_t end, uint32_t *count) {
    (void)table;
    (void)image;
    (void)start;
    (void)end;
    (*count)++;
}

static void bdmem_store_segment(bdmem_image_table_t *table, uint32_t image,
                                uintptr_t start, uintptr_t end, uint32_t *count) {
    // Images loaded between the counting pass and this one do not fit and are left out.
    if (*count >= table->segment_count) {
        return;
    }
    table->segments[(*count)++] = (bdmem_segment_t){.start = start, .end = end, .image = image};
}

static void bdmem_visit_segments(bdmem_image_table_t *table, uint32_t image_count,
                                 bdmem_segment_visitor_t visitor, uint32_t *count) {
    for (uint32_t image = 0; image < image_count; image++) {
        const struct mach_header_64 *header =
            (const struct mach_header_64 *)_dyld_get_image_header(image);
        if (header == NULL || header->magic != MH_MAGIC_64) {
            continue;
        }
        const uintptr_t slide = (uintptr_t)_dyld_get_image_vmaddr_slide(image);
        const struct load_command *command =
            (const struct load_command *)((const char *)header + sizeof(*header));

        for (uint32_t index = 0; index < header->ncmds; index++) {
            if (command->cmd == LC_SEGMENT_64) {
                const struct segment_command_64 *segment = (const struct segment_command_64 *)command;
                // __PAGEZERO has no file content; including it would make null pointers belong to
                // the executable.
                if (segment->vmsize > 0 && segment->filesize > 0) {
                    const uintptr_t start = (uintptr_t)segment->vmaddr + slide;
                    visitor(table, image, start, start + (uintptr_t)segment->vmsize, count);
                }
            }
            command = (const struct load_command *)((const char *)command + command->cmdsize);
        }
    }
}

static int bdmem_compare_segments(const void *lhs, const void *rhs) {
    const uintptr_t left = ((const bdmem_segment_t *)lhs)->start;
    const uintptr_t right = ((const bdmem_segment_t *)rhs)->start;
    return (left > right) - (left < right);
}

bdmem_image_table_t *bdmem_image_table_create(void) {
    bdmem_image_table_t *table = calloc(1, sizeof(*table));
    if (table == NULL) {
        return NULL;
    }

    const uint32_t image_count = _dyld_image_count();
    uint32_t segment_count = 0;
    bdmem_visit_segments(table, image_count, bdmem_count_segment, &segment_count);

    table->names = calloc(image_count > 0 ? image_count : 1, sizeof(*table->names));
    table->segments = calloc(segment_count > 0 ? segment_count : 1, sizeof(*table->segments));
    if (table->names == NULL || table->segments == NULL) {
        bdmem_image_table_destroy(table);
        return NULL;
    }

    table->image_count = image_count;
    for (uint32_t image = 0; image < image_count; image++) {
        table->names[image] = _dyld_get_image_name(image);
    }

    table->segment_count = segment_count;
    uint32_t stored = 0;
    bdmem_visit_segments(table, image_count, bdmem_store_segment, &stored);
    table->segment_count = stored;

    qsort(table->segments, table->segment_count, sizeof(*table->segments), bdmem_compare_segments);
    return table;
}

void bdmem_image_table_destroy(bdmem_image_table_t *table) {
    if (table == NULL) {
        return;
    }
    free(table->segments);
    free(table->names);
    free(table);
}

uint32_t bdmem_image_table_image_count(const bdmem_image_table_t *table) {
    return table != NULL ? table->image_count : 0;
}

const char *bdmem_image_table_image_name(const bdmem_image_table_t *table, uint32_t index) {
    if (table == NULL || index >= table->image_count) {
        return NULL;
    }
    return table->names[index];
}

uint32_t bdmem_image_owner(const bdmem_image_table_t *table, uintptr_t address) {
    if (table == NULL) {
        return BDMEM_NO_IMAGE;
    }
    uint32_t low = 0;
    uint32_t high = table->segment_count;
    while (low < high) {
        const uint32_t middle = low + (high - low) / 2;
        const bdmem_segment_t *segment = &table->segments[middle];
        if (address < segment->start) {
            high = middle;
        } else if (address >= segment->end) {
            low = middle + 1;
        } else {
            return segment->image;
        }
    }
    return BDMEM_NO_IMAGE;
}

uint32_t bdmem_image_overlaps(const bdmem_image_table_t *table,
                              uintptr_t start, uintptr_t end,
                              uint32_t *images, uint64_t *bytes, uint32_t max) {
    if (table == NULL || images == NULL || bytes == NULL || max == 0 || end <= start) {
        return 0;
    }

    // First segment whose end lies past `start`.
    uint32_t low = 0;
    uint32_t high = table->segment_count;
    while (low < high) {
        const uint32_t middle = low + (high - low) / 2;
        if (table->segments[middle].end <= start) {
            low = middle + 1;
        } else {
            high = middle;
        }
    }

    uint32_t written = 0;
    for (uint32_t index = low; index < table->segment_count; index++) {
        const bdmem_segment_t *segment = &table->segments[index];
        if (segment->start >= end) {
            break;
        }
        const uintptr_t from = segment->start > start ? segment->start : start;
        const uintptr_t to = segment->end < end ? segment->end : end;
        if (to <= from) {
            continue;
        }

        bool merged = false;
        for (uint32_t entry = 0; entry < written; entry++) {
            if (images[entry] == segment->image) {
                bytes[entry] += (uint64_t)(to - from);
                merged = true;
                break;
            }
        }
        if (!merged) {
            if (written == max) {
                break;
            }
            images[written] = segment->image;
            bytes[written] = (uint64_t)(to - from);
            written++;
        }
    }
    return written;
}
