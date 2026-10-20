// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// Reads of memory that may have been freed or unmapped since it was surveyed. Every read of heap
// memory outside the zone locks goes through here: a failed read returns false instead of
// faulting.

#ifndef BDMEM_SAFE_READ_H
#define BDMEM_SAFE_READ_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/// Copies `size` bytes at `address` into `destination`. Does not allocate.
bool bdmem_safe_read(uintptr_t address, void *destination, size_t size);

/// Reads one pointer-sized word at an 8-byte aligned `address`.
bool bdmem_safe_read_word(uintptr_t address, uintptr_t *out);

/// Copies the NUL-terminated string at `address` into `destination`, reading at most
/// `capacity - 1` bytes. Returns the string's length, or 0 when it is unreadable or does not
/// terminate within the cap.
size_t bdmem_safe_read_string(uintptr_t address, char *destination, size_t capacity);

#endif /* BDMEM_SAFE_READ_H */
