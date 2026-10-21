// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// Owner attribution: an unnamed buffer has no type to read, but usually exactly one live object
// points at it. Every word of every named block is checked against the unnamed blocks. A named
// block pointing at the start of a buffer beats one pointing inside it, and among equals the first
// one found is credited. Blocks above BDMEM_OWNER_INTERIOR_MAX_SIZE need a pointer to their start.
//
// Runs after the locks, so named blocks may have been freed since they were recorded. Each one is
// read through bdmem_safe_read into a scratch copy and the copy is scanned. Known limits:
// - an integer that equals a block address counts as a pointer;
// - a buffer several objects point at equally well goes to the first one scanned;
// - only blocks of classes in the runtime's class list are sources.

#include "bdmem_census.h"
#include "bdmem_safe_read.h"

#include <stdlib.h>
#include <sys/mman.h>

/// Named blocks are copied in windows of adjacent blocks, so a heap of small objects costs one
/// read per window instead of one per object.
#define BDMEM_OWNER_SCRATCH_SIZE (256u * 1024u)

void bdmem_census_reserve_owner_tables(bdmem_census_t *census) {
    const size_t unnamed_size = (size_t)BDMEM_OWNER_UNNAMED_CAPACITY * sizeof(bdmem_unnamed_block_t);
    const size_t named_size = (size_t)BDMEM_OWNER_NAMED_CAPACITY * sizeof(bdmem_named_block_t);
    const size_t total = unnamed_size + named_size;

    void *tables = mmap(NULL, total, PROT_READ | PROT_WRITE, MAP_ANON | MAP_PRIVATE, -1, 0);
    if (tables == MAP_FAILED) {
        // The census still runs; only owner attribution is skipped.
        return;
    }
    census->owner_tables = tables;
    census->owner_tables_size = total;
    census->unnamed = tables;
    census->named = (bdmem_named_block_t *)((char *)tables + unnamed_size);
}

static void bdmem_release_owner_tables(bdmem_census_t *census) {
    if (census->owner_tables != NULL) {
        munmap(census->owner_tables, census->owner_tables_size);
    }
    census->owner_tables = NULL;
    census->owner_tables_size = 0;
    census->unnamed = NULL;
    census->named = NULL;
}

static int bdmem_compare_unnamed(const void *lhs, const void *rhs) {
    const uintptr_t left = ((const bdmem_unnamed_block_t *)lhs)->address;
    const uintptr_t right = ((const bdmem_unnamed_block_t *)rhs)->address;
    return (left > right) - (left < right);
}

static int bdmem_compare_named(const void *lhs, const void *rhs) {
    const uintptr_t left = ((const bdmem_named_block_t *)lhs)->address;
    const uintptr_t right = ((const bdmem_named_block_t *)rhs)->address;
    return (left > right) - (left < right);
}

/// The unnamed block containing `address`, so interior pointers find their buffer.
static bdmem_unnamed_block_t *bdmem_find_unnamed(bdmem_census_t *census, uintptr_t address) {
    uint32_t low = 0;
    uint32_t high = census->unnamed_count;
    while (low < high) {
        const uint32_t middle = low + (high - low) / 2;
        bdmem_unnamed_block_t *block = &census->unnamed[middle];
        if (address < block->address) {
            high = middle;
        } else if (address >= block->address + block->size) {
            low = middle + 1;
        } else {
            return block;
        }
    }
    return NULL;
}

static bool bdmem_candidate_was_resolved(const bdmem_census_t *census, uintptr_t metadata) {
    const bdmem_candidate_t *candidate = bdmem_census_find_candidate(census, metadata);
    return candidate != NULL && candidate->kind != BDMEM_CANDIDATE_UNRESOLVED;
}

static void bdmem_credit_owner(bdmem_census_t *census, bdmem_unnamed_block_t *target,
                               int32_t class_index, bool points_at_start) {
    target->owner = class_index;
    target->owner_points_at_start = points_at_start;
    target->referrers = 1;
    census->class_owned_blocks[class_index]++;
    census->class_owned_bytes[class_index] += target->size;
}

static void bdmem_uncredit_owner(bdmem_census_t *census, bdmem_unnamed_block_t *target) {
    census->class_owned_blocks[target->owner]--;
    census->class_owned_bytes[target->owner] -= target->size;
    if (target->referrers >= 2) {
        census->summary.shared_owned_blocks--;
    }
}

static void bdmem_scan_words(bdmem_census_t *census, uintptr_t source, const uint8_t *copy,
                             size_t size, int32_t class_index, uintptr_t low_bound,
                             uintptr_t high_bound) {
    bdmem_census_summary_t *summary = &census->summary;

    // Word zero is the isa, which points at a class and never at storage.
    for (size_t offset = sizeof(uintptr_t); offset + sizeof(uintptr_t) <= size;
         offset += sizeof(uintptr_t)) {
        const uintptr_t word = *(const uintptr_t *)(copy + offset) & BDMEM_TOP_BYTE_MASK;
        if (word < low_bound || word >= high_bound) {
            continue;
        }
        bdmem_unnamed_block_t *target = bdmem_find_unnamed(census, word);
        if (target == NULL || target->referrer == source) {
            continue;
        }
        const bool points_at_start = word == target->address;
        if (!points_at_start && target->size > BDMEM_OWNER_INTERIOR_MAX_SIZE) {
            continue;
        }
        // Resolution already moved this block to a C++ type or an image.
        if (target->candidate != 0 && bdmem_candidate_was_resolved(census, target->candidate)) {
            continue;
        }
        target->referrer = source;

        if (target->owner < 0) {
            bdmem_credit_owner(census, target, class_index, points_at_start);
            summary->owned_blocks++;
            summary->owned_bytes += target->size;
        } else if (points_at_start && !target->owner_points_at_start) {
            bdmem_uncredit_owner(census, target);
            bdmem_credit_owner(census, target, class_index, points_at_start);
        } else if (points_at_start == target->owner_points_at_start) {
            target->referrers++;
            if (target->referrers == 2) {
                summary->shared_owned_blocks++;
            }
        }
    }
}


/// Copies a single named block and scans the copy.
static void bdmem_scan_block(bdmem_census_t *census, const bdmem_named_block_t *block,
                             uint8_t *scratch, uintptr_t low_bound, uintptr_t high_bound) {
    const size_t size = block->size < BDMEM_OWNER_SCRATCH_SIZE ? block->size : BDMEM_OWNER_SCRATCH_SIZE;
    if (bdmem_safe_read(block->address, scratch, size)) {
        bdmem_scan_words(census, block->address, scratch, size, block->class_index, low_bound,
                         high_bound);
    }
}

void bdmem_census_attribute_owners(bdmem_census_t *census) {
    if (census->owner_tables == NULL) {
        return;
    }
    if (census->unnamed_count == 0 || census->named_count == 0) {
        bdmem_release_owner_tables(census);
        return;
    }

    const uint64_t started = bdmem_now_ns();
    uint8_t *scratch = mmap(NULL, BDMEM_OWNER_SCRATCH_SIZE, PROT_READ | PROT_WRITE,
                            MAP_ANON | MAP_PRIVATE, -1, 0);
    if (scratch == MAP_FAILED) {
        bdmem_release_owner_tables(census);
        return;
    }

    qsort(census->unnamed, census->unnamed_count, sizeof(*census->unnamed), bdmem_compare_unnamed);
    qsort(census->named, census->named_count, sizeof(*census->named), bdmem_compare_named);

    // Every target lies between the lowest and highest unnamed block, which rejects most words
    // before any search.
    const uintptr_t low_bound = census->unnamed[0].address;
    const bdmem_unnamed_block_t *highest = &census->unnamed[census->unnamed_count - 1];
    const uintptr_t high_bound = highest->address + highest->size;

    uint32_t index = 0;
    while (index < census->named_count) {
        const bdmem_named_block_t *first = &census->named[index];
        const uintptr_t window_start = first->address;
        const uintptr_t readable_end = bdmem_census_readable_end(census, window_start);
        if (readable_end == 0) {
            index++;
            continue;
        }

        // Extend the window over the following blocks while they fit in the scratch buffer and
        // the same readable range.
        uint32_t last = index;
        while (last + 1 < census->named_count) {
            const bdmem_named_block_t *next = &census->named[last + 1];
            const uintptr_t next_end = next->address + next->size;
            if (next_end - window_start > BDMEM_OWNER_SCRATCH_SIZE || next_end > readable_end) {
                break;
            }
            last++;
        }

        const bdmem_named_block_t *tail = &census->named[last];
        const uintptr_t window_end = tail->address + tail->size;
        const bool window_fits = window_end - window_start <= BDMEM_OWNER_SCRATCH_SIZE &&
                                 window_end <= readable_end;

        if (window_fits && bdmem_safe_read(window_start, scratch, window_end - window_start)) {
            for (uint32_t block = index; block <= last; block++) {
                const bdmem_named_block_t *named = &census->named[block];
                bdmem_scan_words(census, named->address, scratch + (named->address - window_start),
                                 named->size, named->class_index, low_bound, high_bound);
            }
        } else {
            // Part of the window went away, or a single block is larger than the scratch buffer.
            for (uint32_t block = index; block <= last; block++) {
                bdmem_scan_block(census, &census->named[block], scratch, low_bound, high_bound);
            }
        }
        index = last + 1;
    }

    munmap(scratch, BDMEM_OWNER_SCRATCH_SIZE);
    bdmem_release_owner_tables(census);
    census->summary.owners_ns = bdmem_now_ns() - started;
}
