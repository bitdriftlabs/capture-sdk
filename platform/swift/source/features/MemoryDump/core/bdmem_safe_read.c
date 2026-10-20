// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#include "bdmem_safe_read.h"

#include <mach/mach.h>
#include <string.h>

bool bdmem_safe_read(uintptr_t address, void *destination, size_t size) {
    if (address == 0 || destination == NULL || size == 0) {
        return false;
    }
    vm_size_t copied = 0;
    const kern_return_t status = vm_read_overwrite(mach_task_self(), (vm_address_t)address,
                                                   (vm_size_t)size, (vm_address_t)destination,
                                                   &copied);
    return status == KERN_SUCCESS && copied == size;
}

bool bdmem_safe_read_word(uintptr_t address, uintptr_t *out) {
    if ((address & (sizeof(uintptr_t) - 1)) != 0) {
        return false;
    }
    return bdmem_safe_read(address, out, sizeof(*out));
}

size_t bdmem_safe_read_string(uintptr_t address, char *destination, size_t capacity) {
    if (destination == NULL || capacity < 2) {
        return 0;
    }

    // Read page by page: a short string near the end of a mapping must not fail because the cap
    // reaches into the next, unmapped page.
    const uintptr_t page_mask = (uintptr_t)vm_page_size - 1;
    size_t copied = 0;
    while (copied < capacity - 1) {
        const uintptr_t at = address + copied;
        size_t chunk = (size_t)(vm_page_size - (at & page_mask));
        if (chunk > capacity - 1 - copied) {
            chunk = capacity - 1 - copied;
        }
        if (!bdmem_safe_read(at, destination + copied, chunk)) {
            return 0;
        }
        const char *terminator = memchr(destination + copied, '\0', chunk);
        if (terminator != NULL) {
            return (size_t)(terminator - destination);
        }
        copied += chunk;
    }
    destination[capacity - 1] = '\0';
    return 0;
}
