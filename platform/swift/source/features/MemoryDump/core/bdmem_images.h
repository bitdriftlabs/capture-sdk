// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#ifndef BDMEM_IMAGES_H
#define BDMEM_IMAGES_H

#include "bdmem.h"

#include <stdint.h>

#define BDMEM_NO_IMAGE UINT32_MAX

/// Index of the image owning `address`, or BDMEM_NO_IMAGE. Does not allocate.
uint32_t bdmem_image_owner(const bdmem_image_table_t *table, uintptr_t address);

/// Splits [start, end) across the images that overlap it, merging segments of the same image.
/// Writes up to `max` image indexes and overlapping byte counts, and returns how many it wrote.
/// Does not allocate.
uint32_t bdmem_image_overlaps(const bdmem_image_table_t *table,
                              uintptr_t start, uintptr_t end,
                              uint32_t *images, uint64_t *bytes, uint32_t max);

#endif /* BDMEM_IMAGES_H */
