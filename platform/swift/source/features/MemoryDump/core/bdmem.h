// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// Memory diagnostics for the `ios_memory_dump` command.
//
// C counts and classifies; sorting, naming and encoding happen in Swift once C returns. Functions
// documented as not allocating may run while the process is under memory pressure.

#ifndef BDMEM_H
#define BDMEM_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#pragma mark - Loaded images

/// Segment map of the images loaded when the table was created, searched by bisection.
typedef struct bdmem_image_table bdmem_image_table_t;

/// Surveys the segments of every loaded image. Allocates. Returns NULL on failure.
bdmem_image_table_t *bdmem_image_table_create(void);
void bdmem_image_table_destroy(bdmem_image_table_t *table);

uint32_t bdmem_image_table_image_count(const bdmem_image_table_t *table);

/// Path of the image at `index`, borrowed from dyld, or NULL when out of range.
const char *bdmem_image_table_image_name(const bdmem_image_table_t *table, uint32_t index);

#pragma mark - Stage 2: VM regions

/// Mach's `user_tag` ranges from 0 to 255.
#define BDMEM_TAG_COUNT 256

typedef struct {
    /// Dirty bytes in regions only this process maps.
    uint64_t private_dirty;
    /// Dirty bytes in regions other processes map too. The kernel prorates them, so they are not
    /// charged to this process.
    uint64_t shared_dirty;
    /// Bytes taken by the compressor. Charged to this process whether shared or not.
    uint64_t compressed;
    uint64_t virtual_size;
    uint32_t regions;
} bdmem_tag_totals_t;

typedef struct {
    bdmem_tag_totals_t by_tag[BDMEM_TAG_COUNT];
    /// Caller-provided, `image_count` entries indexed like the image table, or NULL. Receives the
    /// charged bytes (private dirty plus compressed) of the regions overlapping each image, prorated
    /// by overlap when a region spans several.
    uint64_t *charged_by_image;
    uint32_t image_count;
    uint32_t region_count;
    /// The map changed while it was being read, so the totals are approximate.
    bool truncated;
} bdmem_regions_result_t;

/// Walks this process's VM map. Takes no locks and does not allocate. Pages are counted only for
/// regions whose current or maximum protection allows writing: a region that could never be
/// written cannot hold a dirty page.
///
/// `out->charged_by_image` and `out->image_count` are read on entry; every other field is
/// overwritten. `images` may be NULL, in which case nothing is attributed to images.
bool bdmem_regions_walk(const bdmem_image_table_t *images, bdmem_regions_result_t *out);

/// Readable name for a Mach `user_tag`, or NULL when the tag is unknown.
const char *bdmem_tag_name(uint32_t tag);

#pragma mark - Stage 3: heap census

/// Power-of-two size buckets for the blocks that could not be named.
#define BDMEM_SIZE_BUCKETS 32

#define BDMEM_MAX_ZONES 32

typedef struct {
    /// Time the malloc zone locks may be held, shared among the enumerated zones. A zone that
    /// spends its share stops recording and the summary is marked truncated; time a zone leaves
    /// unused goes to the zones after it.
    uint64_t budget_ms;
    /// Credit unnamed blocks to the named object that points at them.
    bool attribute_owners;
    /// Test seam, NULL in production: called after the locks are released and the readable ranges
    /// are surveyed, right before candidates are read.
    void (*before_resolve)(void *context);
    void *before_resolve_context;
} bdmem_census_options_t;

typedef enum {
    /// An ObjC or Swift class. The name is the runtime's, mangled for Swift classes.
    BDMEM_TYPE_CLASS = 0,
    /// A polymorphic C++ type named through RTTI. The name is Itanium-mangled.
    BDMEM_TYPE_CXX = 1,
    /// No type, only the image owning the block's first word. The name is the image path.
    BDMEM_TYPE_IMAGE = 2,
} bdmem_type_kind_t;

typedef struct {
    bdmem_type_kind_t kind;
    /// Valid while the census is alive.
    const char *name;
    /// For BDMEM_TYPE_CLASS, a class pointer validated against the runtime, so Swift can reflect
    /// it. Zero otherwise.
    uintptr_t class_pointer;
    uint64_t blocks;
    uint64_t bytes;
    /// Unnamed blocks this type was the first found pointing at. Ownership, not type: these bytes
    /// are already counted as unclassified.
    uint64_t owned_blocks;
    uint64_t owned_bytes;
} bdmem_type_entry_t;


typedef struct {
    char name[64];
    uint64_t blocks;
    uint64_t bytes;
    uint64_t lock_held_ns;
    /// The zone was locked and enumerated.
    bool enumerated;
    /// The zone was skipped by name and never locked.
    bool skipped;
    /// The zone spent its budget share before its enumeration finished.
    bool truncated;
    /// The enumerator reported at least one batch. A zone enumerated without being seen keeps its
    /// blocks out of the census.
    bool seen;
} bdmem_zone_summary_t;

typedef struct {
    uint64_t total_blocks;
    uint64_t total_bytes;
    /// Blocks named as an ObjC, Swift or C++ type.
    uint64_t named_blocks;
    uint64_t named_bytes;
    /// Swift heap objects without a class: closure boxes, existentials, errors.
    uint64_t swift_box_blocks;
    uint64_t swift_box_bytes;
    /// Blocks attributed only to the image owning their first word.
    uint64_t image_blocks;
    uint64_t image_bytes;
    uint64_t unclassified_blocks;
    uint64_t unclassified_bytes;
    uint64_t unclassified_histogram[BDMEM_SIZE_BUCKETS];
    uint64_t largest_block_bytes;
    /// Candidates that did not fit the candidate table and stayed unclassified.
    uint64_t candidate_overflow;
    /// Candidates whose memory could not be read after the locks were released.
    uint64_t unreadable_candidates;
    /// The lock budget ran out: the figures are a floor, not an inventory.
    bool truncated;
    /// Blocks reported by the enumerators after the budget ran out.
    uint64_t skipped_blocks;
    uint64_t lock_wait_ns;
    uint64_t lock_held_ns;
    uint64_t resolve_ns;
    /// Unnamed blocks credited to an owner, a subset of the unclassified ones.
    uint64_t owned_blocks;
    uint64_t owned_bytes;
    /// Owned blocks more than one named object pointed at equally well; credited to the first one
    /// found.
    uint64_t shared_owned_blocks;
    /// Blocks that did not fit the owner tables. Above zero, the owner figures are a floor.
    uint64_t owner_table_overflow;
    uint64_t owners_ns;
    bdmem_zone_summary_t zones[BDMEM_MAX_ZONES];
    uint32_t zone_count;
} bdmem_census_summary_t;

typedef struct bdmem_census bdmem_census_t;

/// Builds the class table and reserves every table the run writes into. Allocates. Returns NULL
/// on failure. `images` is borrowed and must outlive the census; when NULL, no block is
/// attributed to an image.
bdmem_census_t *bdmem_census_create(const bdmem_census_options_t *options,
                                    const bdmem_image_table_t *images);

/// Enumerates the live heap blocks one malloc zone at a time, then resolves what could not be
/// named under the locks. Nothing allocates while a zone is locked. Runs once per census.
bool bdmem_census_run(bdmem_census_t *census);

size_t bdmem_census_type_count(const bdmem_census_t *census);
bool bdmem_census_type_at(const bdmem_census_t *census, size_t index, bdmem_type_entry_t *out);
const bdmem_census_summary_t *bdmem_census_summary(const bdmem_census_t *census);
void bdmem_census_destroy(bdmem_census_t *census);

#ifdef __cplusplus
}
#endif

#endif /* BDMEM_H */
