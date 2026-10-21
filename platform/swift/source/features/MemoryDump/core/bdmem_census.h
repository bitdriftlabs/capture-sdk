// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// State shared by the census phases: bdmem_census_enumerate.c runs under the zone locks,
// bdmem_census_resolve.c and bdmem_census_owners.c after them.

#ifndef BDMEM_CENSUS_H
#define BDMEM_CENSUS_H

#include "bdmem.h"

#include <TargetConditionals.h>

/// Mask that extracts the class pointer from an isa. The simulator shares the macOS address
/// space, where Swift generic metadata lives above the 36 bits a device uses.
#if defined(__arm64__) && !TARGET_OS_SIMULATOR && !TARGET_OS_OSX
#define BDMEM_ISA_MASK ((uintptr_t)0x0000000ffffffff8ULL)
#else
#define BDMEM_ISA_MASK ((uintptr_t)0x00007ffffffffff8ULL)
#endif

/// Clears pointer authentication bits without touching alignment: a string pointer can land on
/// any byte.
#define BDMEM_ADDRESS_MASK (BDMEM_ISA_MASK | (uintptr_t)0x7)

/// Clears only the top byte, which the hardware ignores and memory tagging uses. Owner attribution
/// keeps every other bit: masking a word down to 36 bits turns string bytes and byte counts into
/// heap addresses.
#define BDMEM_TOP_BYTE_MASK ((uintptr_t)0x00ffffffffffffffULL)

/// Swift metadata kinds of heap objects that have no class.
#define BDMEM_SWIFT_KIND_HEAP_LOCAL_VARIABLE 0x400
#define BDMEM_SWIFT_KIND_HEAP_GENERIC_LOCAL_VARIABLE 0x500
#define BDMEM_SWIFT_KIND_ERROR_OBJECT 0x501

/// First words below this are Swift metadata kinds rather than pointers.
#define BDMEM_METADATA_KIND_MAX 0x800

/// Open-addressed table of first words that did not match a known class. Sized for the tens of
/// thousands of distinct candidates a large app produces; mmap commits only the pages touched.
#define BDMEM_CANDIDATE_CAPACITY_BITS 17
#define BDMEM_CANDIDATE_CAPACITY (1u << BDMEM_CANDIDATE_CAPACITY_BITS)
#define BDMEM_CANDIDATE_PROBE_LIMIT 32

#define BDMEM_RANGE_CAPACITY 8192

/// Owner attribution tables, reserved per run. mmap commits only the pages touched.
#define BDMEM_OWNER_UNNAMED_CAPACITY (1u << 20)
#define BDMEM_OWNER_NAMED_CAPACITY (1u << 19)

/// Above this size an unnamed block is credited only to an object pointing at its start. A large
/// block spans so much of the address space that any number in a named object lands inside one.
#define BDMEM_OWNER_INTERIOR_MAX_SIZE (16u * 1024u)

typedef struct {
    uintptr_t start;
    uintptr_t end;
} bdmem_range_t;

typedef struct {
    uintptr_t metadata;
    uint64_t blocks;
    uint64_t bytes;
    /// BDMEM_TYPE_* once resolved, or BDMEM_CANDIDATE_UNRESOLVED.
    int32_t kind;
    /// Runtime or dyld string for classes and images; offset into the name buffer for C++.
    const char *name;
    uint32_t name_offset;
} bdmem_candidate_t;

#define BDMEM_CANDIDATE_UNRESOLVED (-1)

/// A block with no class: a possible target of owner attribution.
typedef struct {
    uintptr_t address;
    uint64_t size;
    /// The candidate it was filed under, or 0. A candidate resolution named belongs to another
    /// bucket and must not also be credited to an owner.
    uintptr_t candidate;
    /// The named block last credited, so an object pointing twice at a buffer counts once.
    uintptr_t referrer;
    int32_t owner;
    /// Named objects that pointed at the block as well as the owner did.
    uint32_t referrers;
    /// The owner points at the start of the block rather than inside it.
    bool owner_points_at_start;
} bdmem_unnamed_block_t;

/// A block of a known class: a source of owner attribution.
typedef struct {
    uintptr_t address;
    uint32_t size;
    int32_t class_index;
} bdmem_named_block_t;

/// Index into the class table or the candidate table, built after resolution.
typedef struct {
    uint32_t index;
    bool is_candidate;
} bdmem_type_ref_t;

struct bdmem_census {
    bdmem_census_options_t options;
    bool has_run;

    // Sorted class pointers, their instance sizes and runtime names, and per-class tallies.
    uintptr_t *class_pointers;
    uint32_t *class_sizes;
    const char **class_names;
    uint64_t *class_blocks;
    uint64_t *class_bytes;
    uint64_t *class_owned_blocks;
    uint64_t *class_owned_bytes;
    uint32_t class_count;

    bdmem_candidate_t *candidates;

    /// Readable ranges of the address space, sorted and merged.
    bdmem_range_t *ranges;
    uint32_t range_count;

    void *tables;
    size_t tables_size;

    const bdmem_image_table_t *images;

    /// C++ names copied out of foreign memory.
    char *names;
    size_t names_used;
    size_t names_capacity;

    bdmem_type_ref_t *types;
    size_t type_count;

    bdmem_census_summary_t summary;


    // NULL unless owners are attributed; valid from before the locks until the scan ends.
    bdmem_unnamed_block_t *unnamed;
    uint32_t unnamed_count;
    bdmem_named_block_t *named;
    uint32_t named_count;
    void *owner_tables;
    size_t owner_tables_size;

    // Live only while a zone is enumerated.
    uint32_t current_zone;
    uint64_t deadline_ns;
    bool over_budget;
};

static inline uint32_t bdmem_candidate_slot(uintptr_t metadata) {
    return (uint32_t)((metadata * 0x9E3779B97F4A7C15ull) >> (64 - BDMEM_CANDIDATE_CAPACITY_BITS));
}

uint64_t bdmem_now_ns(void);

/// Surveys the readable ranges. Makes syscalls, so never call it with a zone locked.
void bdmem_census_survey_ranges(bdmem_census_t *census);

/// End of the readable range containing `address`, or 0 when it is not mapped. No syscalls.
uintptr_t bdmem_census_readable_end(const bdmem_census_t *census, uintptr_t address);

/// Index of the class at `pointer`, or -1. No allocations.
int32_t bdmem_census_find_class(const bdmem_census_t *census, uintptr_t pointer);

/// The candidate recorded for `metadata`, or NULL. No allocations.
const bdmem_candidate_t *bdmem_census_find_candidate(const bdmem_census_t *census,
                                                     uintptr_t metadata);

uint32_t bdmem_size_bucket(uint64_t size);

/// Locks each zone in turn and records its live blocks.
void bdmem_census_enumerate(bdmem_census_t *census);

/// Names the candidates recorded under the locks. Must run with no zone locked.
void bdmem_census_resolve(bdmem_census_t *census);

/// Reserves the owner tables. Allocates, so it runs before any zone is locked.
void bdmem_census_reserve_owner_tables(bdmem_census_t *census);

/// Credits unnamed blocks to the named blocks pointing at them, then releases the owner tables.
/// Must run after resolution, with no zone locked.
void bdmem_census_attribute_owners(bdmem_census_t *census);

#endif /* BDMEM_CENSUS_H */
