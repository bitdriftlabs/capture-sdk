// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// Names the candidates recorded under the locks. Runs with no zone locked, but the memory it reads
// may have been freed since it was recorded, so every read goes through bdmem_safe_read.

#include "bdmem_census.h"
#include "bdmem_images.h"
#include "bdmem_safe_read.h"

#include <objc/runtime.h>
#include <stdlib.h>
#include <string.h>

#define BDMEM_CXX_NAME_CAPACITY 256

/// Hops from a class to the root metaclass, which points at itself.
#define BDMEM_ISA_CHAIN_HOPS 3

#define BDMEM_SUPERCLASS_CHAIN_HOPS 16

static inline uintptr_t bdmem_strip_pointer(uintptr_t pointer) {
    return pointer & BDMEM_ADDRESS_MASK;
}

/// Reads a word, rejecting addresses outside the surveyed readable ranges before paying a syscall.
static bool bdmem_read_word(const bdmem_census_t *census, uintptr_t address, uintptr_t *out) {
    if (address == 0 || bdmem_census_readable_end(census, address) == 0) {
        return false;
    }
    return bdmem_safe_read_word(address, out);
}

/// A class's isa chain reaches the root metaclass, which points at itself.
static bool bdmem_isa_chain_is_consistent(const bdmem_census_t *census, uintptr_t candidate) {
    uintptr_t current = candidate;
    for (int hop = 0; hop < BDMEM_ISA_CHAIN_HOPS; hop++) {
        uintptr_t isa = 0;
        if (!bdmem_read_word(census, current, &isa)) {
            return false;
        }
        const uintptr_t next = isa & BDMEM_ISA_MASK;
        if (next == 0 || (next & 0x7) != 0) {
            return false;
        }
        if (next == current) {
            return hop > 0;
        }
        current = next;
    }
    return false;
}

/// Every real class, including Swift generic instantiations the runtime does not list, inherits
/// from a registered class. The superclass is the second word of objc_class.
static bool bdmem_superclass_chain_reaches_known(const bdmem_census_t *census,
                                                 uintptr_t candidate) {
    uintptr_t current = candidate;
    for (int hop = 0; hop < BDMEM_SUPERCLASS_CHAIN_HOPS; hop++) {
        uintptr_t superclass = 0;
        if (!bdmem_read_word(census, current + sizeof(uintptr_t), &superclass)) {
            return false;
        }
        superclass = bdmem_strip_pointer(superclass) & BDMEM_ISA_MASK;
        if (superclass == 0 || superclass == current) {
            return false;
        }
        if (bdmem_census_find_class(census, superclass) >= 0) {
            return true;
        }
        current = superclass;
    }
    return false;
}

static bool bdmem_store_name(bdmem_census_t *census, const char *name, size_t length,
                             uint32_t *offset) {
    if (census->names_used + length + 1 > census->names_capacity) {
        size_t capacity = census->names_capacity > 0 ? census->names_capacity * 2 : 16384;
        while (capacity < census->names_used + length + 1) {
            capacity *= 2;
        }
        char *names = realloc(census->names, capacity);
        if (names == NULL) {
            return false;
        }
        census->names = names;
        census->names_capacity = capacity;
    }
    *offset = (uint32_t)census->names_used;
    memcpy(census->names + census->names_used, name, length + 1);
    census->names_used += length + 1;
    return true;
}

/// Itanium ABI: a polymorphic object's first word points into its vtable, whose previous slot is
/// the type_info pointer; the type_info's second word is the mangled name.
static bool bdmem_resolve_cxx(bdmem_census_t *census, bdmem_candidate_t *candidate) {
    const uintptr_t vtable = candidate->metadata;
    uintptr_t type_info = 0;
    uintptr_t name_pointer = 0;
    if (vtable < 2 * sizeof(uintptr_t) ||
        !bdmem_read_word(census, vtable - sizeof(uintptr_t), &type_info) ||
        !bdmem_read_word(census, bdmem_strip_pointer(type_info) + sizeof(uintptr_t),
                         &name_pointer)) {
        return false;
    }
    name_pointer = bdmem_strip_pointer(name_pointer);
    if (bdmem_census_readable_end(census, name_pointer) == 0) {
        return false;
    }

    char name[BDMEM_CXX_NAME_CAPACITY];
    const size_t length = bdmem_safe_read_string(name_pointer, name, sizeof(name));
    if (length < 2) {
        return false;
    }
    // A mangled name starts with a length, a nested-name marker or a pointer modifier.
    const char first = name[0];
    const bool plausible = (first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z') ||
                           (first >= '0' && first <= '9') || first == '_' || first == '*';
    if (!plausible || !bdmem_store_name(census, name, length, &candidate->name_offset)) {
        return false;
    }
    candidate->kind = BDMEM_TYPE_CXX;
    return true;
}

static void bdmem_resolve_candidate(bdmem_census_t *census, bdmem_candidate_t *candidate) {
    bdmem_census_summary_t *summary = &census->summary;

    uintptr_t first_word = 0;
    if (!bdmem_read_word(census, candidate->metadata, &first_word)) {
        summary->unreadable_candidates++;
        return;
    }
    // Struct, enum and tuple metadata: not a heap object type.
    if (first_word < BDMEM_METADATA_KIND_MAX) {
        return;
    }

    if (bdmem_isa_chain_is_consistent(census, candidate->metadata) &&
        bdmem_superclass_chain_reaches_known(census, candidate->metadata)) {
        // Validated as a class, so the runtime can name it.
        const char *name = class_getName((Class)(void *)candidate->metadata);
        if (name != NULL && name[0] != '\0') {
            candidate->kind = BDMEM_TYPE_CLASS;
            candidate->name = name;
            summary->named_blocks += candidate->blocks;
            summary->named_bytes += candidate->bytes;
            summary->unclassified_blocks -= candidate->blocks;
            summary->unclassified_bytes -= candidate->bytes;
        }
        return;
    }

    if (bdmem_resolve_cxx(census, candidate)) {
        summary->named_blocks += candidate->blocks;
        summary->named_bytes += candidate->bytes;
        summary->unclassified_blocks -= candidate->blocks;
        summary->unclassified_bytes -= candidate->bytes;
        return;
    }

    const uint32_t image = bdmem_image_owner(census->images, candidate->metadata);
    const char *image_name = bdmem_image_table_image_name(census->images, image);
    if (image_name != NULL) {
        candidate->kind = BDMEM_TYPE_IMAGE;
        candidate->name = image_name;
        summary->image_blocks += candidate->blocks;
        summary->image_bytes += candidate->bytes;
        summary->unclassified_blocks -= candidate->blocks;
        summary->unclassified_bytes -= candidate->bytes;
    }
}

void bdmem_census_resolve(bdmem_census_t *census) {
    bdmem_census_summary_t *summary = &census->summary;
    const uint64_t started = bdmem_now_ns();

    // Regions may have been unmapped since the pre-lock survey.
    bdmem_census_survey_ranges(census);
    if (census->options.before_resolve != NULL) {
        census->options.before_resolve(census->options.before_resolve_context);
    }

    for (uint32_t index = 0; index < BDMEM_CANDIDATE_CAPACITY; index++) {
        bdmem_candidate_t *candidate = &census->candidates[index];
        if (candidate->metadata == 0) {
            continue;
        }
        bdmem_resolve_candidate(census, candidate);
        if (candidate->kind == BDMEM_CANDIDATE_UNRESOLVED) {
            // No per-candidate histogram is kept; the average size places its blocks.
            summary->unclassified_histogram[bdmem_size_bucket(candidate->bytes / candidate->blocks)] +=
                candidate->blocks;
        }
    }

    summary->resolve_ns = bdmem_now_ns() - started;
}
