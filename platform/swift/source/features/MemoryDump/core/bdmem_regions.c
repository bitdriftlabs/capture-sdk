// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#include "bdmem.h"
#include "bdmem_images.h"

#include <mach/mach.h>
#include <mach/vm_region.h>
#include <mach/vm_statistics.h>
#include <string.h>

/// Past this many regions the map is changing faster than it can be read.
#define BDMEM_MAX_REGIONS 200000

#define BDMEM_MAX_SUBMAP_DEPTH 8

/// Images a single region can overlap. The shared cache is one mapping carved into many images.
#define BDMEM_MAX_OVERLAPS 32

/// VM_REGION_SUBMAP_SHORT_INFO_COUNT_64 does not compile as defined by the SDK.
#define BDMEM_SHORT_INFO_COUNT \
    ((mach_msg_type_number_t)(sizeof(vm_region_submap_short_info_data_64_t) / sizeof(natural_t)))

/// Pages other processes map too. A copy-on-write region with several references counts as shared:
/// the shared cache arrives that way, and charging it in full puts the total above the footprint.
static bool bdmem_is_shared(unsigned share_mode, unsigned ref_count) {
    switch (share_mode) {
        case SM_SHARED:
        case SM_TRUESHARED:
        case SM_SHARED_ALIASED:
            return true;
        case SM_COW:
            return ref_count > 1;
        default:
            return false;
    }
}

static void bdmem_attribute_to_images(const bdmem_image_table_t *images,
                                      uintptr_t start, uintptr_t end, uint64_t charged,
                                      bdmem_regions_result_t *out) {
    uint32_t owners[BDMEM_MAX_OVERLAPS];
    uint64_t spans[BDMEM_MAX_OVERLAPS];
    const uint32_t found =
        bdmem_image_overlaps(images, start, end, owners, spans, BDMEM_MAX_OVERLAPS);

    // Which segment the dirty pages landed in is unknown at region granularity, so the charge is
    // prorated by how much of the region each image covers.
    const uint64_t size = (uint64_t)(end - start);
    for (uint32_t index = 0; index < found; index++) {
        if (owners[index] < out->image_count) {
            out->charged_by_image[owners[index]] +=
                (uint64_t)((double)charged * ((double)spans[index] / (double)size));
        }
    }
}

bool bdmem_regions_walk(const bdmem_image_table_t *images, bdmem_regions_result_t *out) {
    if (out == NULL) {
        return false;
    }

    uint64_t *charged_by_image = out->charged_by_image;
    uint32_t image_count = charged_by_image != NULL ? out->image_count : 0;
    memset(out, 0, sizeof(*out));
    out->charged_by_image = charged_by_image;
    out->image_count = image_count;
    if (charged_by_image != NULL) {
        memset(charged_by_image, 0, (size_t)image_count * sizeof(*charged_by_image));
    }

    const task_t task = mach_task_self();
    const uint64_t page_size = (uint64_t)vm_kernel_page_size;

    vm_address_t address = 0;
    // In/out: the kernel reports the depth it found the region at. Resetting it per region makes
    // leaving a submap re-enter it forever.
    natural_t depth = 0;

    while (out->region_count < BDMEM_MAX_REGIONS) {
        vm_size_t size = 0;
        vm_region_submap_short_info_data_64_t short_info;
        mach_msg_type_number_t short_count = BDMEM_SHORT_INFO_COUNT;

        const kern_return_t status = vm_region_recurse_64(
            task, &address, &size, &depth, (vm_region_recurse_info_t)&short_info, &short_count);
        if (status != KERN_SUCCESS) {
            // KERN_INVALID_ADDRESS is the normal end of the map.
            if (status != KERN_INVALID_ADDRESS) {
                out->truncated = true;
            }
            break;
        }
        if (size == 0) {
            out->truncated = true;
            break;
        }

        // A submap is a container: descend without advancing, or its contents count twice.
        if (short_info.is_submap) {
            if (depth + 1 >= BDMEM_MAX_SUBMAP_DEPTH) {
                address += size;
            } else {
                depth++;
            }
            continue;
        }

        unsigned ref_count = short_info.ref_count;
        uint64_t dirty = 0;
        uint64_t compressed = 0;

        // Current protection is not enough: dyld writes into __DATA_CONST and then makes it
        // read-only, and those pages stay dirty.
        const bool could_be_written =
            ((short_info.protection | short_info.max_protection) & VM_PROT_WRITE) != 0;

        if (could_be_written) {
            vm_region_submap_info_data_64_t info;
            mach_msg_type_number_t info_count = VM_REGION_SUBMAP_INFO_COUNT_64;
            vm_address_t probe_address = address;
            vm_size_t probe_size = size;
            natural_t probe_depth = depth;

            const kern_return_t probe_status =
                vm_region_recurse_64(task, &probe_address, &probe_size, &probe_depth,
                                     (vm_region_recurse_info_t)&info, &info_count);
            // The map can change between the two calls; losing a count beats charging it to the
            // wrong region.
            if (probe_status == KERN_SUCCESS && probe_address == address) {
                dirty = (uint64_t)info.pages_dirtied * page_size;
                compressed = (uint64_t)info.pages_swapped_out * page_size;
                ref_count = info.ref_count;
            } else {
                out->truncated = true;
            }
        }

        bdmem_tag_totals_t *totals = &out->by_tag[short_info.user_tag & (BDMEM_TAG_COUNT - 1)];
        totals->regions++;
        totals->virtual_size += (uint64_t)size;
        totals->compressed += compressed;

        const bool shared = bdmem_is_shared(short_info.share_mode, ref_count);
        if (shared) {
            totals->shared_dirty += dirty;
        } else {
            totals->private_dirty += dirty;
        }

        const uint64_t charged = (shared ? 0 : dirty) + compressed;
        if (charged > 0 && charged_by_image != NULL) {
            bdmem_attribute_to_images(images, (uintptr_t)address, (uintptr_t)address + size,
                                      charged, out);
        }

        out->region_count++;
        address += size;
    }

    if (out->region_count >= BDMEM_MAX_REGIONS) {
        out->truncated = true;
    }
    return true;
}

const char *bdmem_tag_name(uint32_t tag) {
    switch (tag) {
        case 0: return "Untagged";
        case VM_MEMORY_MALLOC: return "MALLOC";
        case VM_MEMORY_MALLOC_SMALL: return "MALLOC_SMALL";
        case VM_MEMORY_MALLOC_LARGE: return "MALLOC_LARGE";
        case VM_MEMORY_MALLOC_HUGE: return "MALLOC_HUGE";
        case VM_MEMORY_SBRK: return "SBRK";
        case VM_MEMORY_REALLOC: return "MALLOC_REALLOC";
        case VM_MEMORY_MALLOC_TINY: return "MALLOC_TINY";
        case VM_MEMORY_MALLOC_LARGE_REUSABLE: return "MALLOC_LARGE_REUSABLE";
        case VM_MEMORY_MALLOC_LARGE_REUSED: return "MALLOC_LARGE_REUSED";
        case VM_MEMORY_ANALYSIS_TOOL: return "Analysis tool";
        case VM_MEMORY_MALLOC_NANO: return "MALLOC_NANO";
        case VM_MEMORY_MALLOC_MEDIUM: return "MALLOC_MEDIUM";
#ifdef VM_MEMORY_MALLOC_PROB_GUARD
        case VM_MEMORY_MALLOC_PROB_GUARD: return "MALLOC_PROB_GUARD";
#endif
        case VM_MEMORY_MACH_MSG: return "Mach message";
        case VM_MEMORY_IOKIT: return "IOKit";
        case VM_MEMORY_STACK: return "Stack";
        case VM_MEMORY_GUARD: return "Guard";
        case VM_MEMORY_SHARED_PMAP: return "Shared pmap";
        case VM_MEMORY_DYLIB: return "dylib";
        case VM_MEMORY_OBJC_DISPATCHERS: return "ObjC dispatchers";
        case VM_MEMORY_UNSHARED_PMAP: return "Shared cache (privatized)";
        case VM_MEMORY_APPKIT: return "AppKit";
        case VM_MEMORY_FOUNDATION: return "Foundation";
        case VM_MEMORY_COREGRAPHICS: return "CoreGraphics";
        case VM_MEMORY_CORESERVICES: return "CoreServices";
        case VM_MEMORY_JAVA: return "Java";
        case VM_MEMORY_COREDATA: return "CoreData";
        case VM_MEMORY_COREDATA_OBJECTIDS: return "CoreData object IDs";
        case VM_MEMORY_ATS: return "ATS (fonts)";
        case VM_MEMORY_LAYERKIT: return "CoreAnimation";
        case VM_MEMORY_CGIMAGE: return "CGImage";
        case VM_MEMORY_TCMALLOC: return "TCMalloc";
        case VM_MEMORY_COREGRAPHICS_DATA: return "CoreGraphics data";
        case VM_MEMORY_COREGRAPHICS_SHARED: return "CoreGraphics shared";
        case VM_MEMORY_COREGRAPHICS_FRAMEBUFFERS: return "CoreGraphics framebuffers";
        case VM_MEMORY_COREGRAPHICS_BACKINGSTORES: return "CoreGraphics backing stores";
        case VM_MEMORY_COREGRAPHICS_XALLOC: return "CoreGraphics xalloc";
        case VM_MEMORY_DYLD: return "dyld";
        case VM_MEMORY_DYLD_MALLOC: return "dyld malloc";
        case VM_MEMORY_SQLITE: return "SQLite";
        case VM_MEMORY_JAVASCRIPT_CORE: return "JavaScriptCore";
        case VM_MEMORY_JAVASCRIPT_JIT_EXECUTABLE_ALLOCATOR: return "JavaScriptCore JIT executable";
        case VM_MEMORY_JAVASCRIPT_JIT_REGISTER_FILE: return "JavaScriptCore JIT register file";
        case VM_MEMORY_GLSL: return "GLSL";
        case VM_MEMORY_OPENCL: return "OpenCL";
        case VM_MEMORY_COREIMAGE: return "CoreImage";
        case VM_MEMORY_WEBCORE_PURGEABLE_BUFFERS: return "WebCore purgeable buffers";
        case VM_MEMORY_IMAGEIO: return "ImageIO";
        case VM_MEMORY_COREPROFILE: return "CoreProfile";
        case VM_MEMORY_ASSETSD: return "assetsd";
        case VM_MEMORY_OS_ALLOC_ONCE: return "os_alloc_once";
        case VM_MEMORY_LIBDISPATCH: return "libdispatch";
        case VM_MEMORY_ACCELERATE: return "Accelerate";
        case VM_MEMORY_COREUI: return "CoreUI";
        case VM_MEMORY_COREUIFILE: return "CoreUI file";
        case VM_MEMORY_GENEALOGY: return "Genealogy";
        case VM_MEMORY_RAWCAMERA: return "RawCamera";
        case VM_MEMORY_CORPSEINFO: return "Corpse info";
        case VM_MEMORY_ASL: return "ASL";
        case VM_MEMORY_SWIFT_RUNTIME: return "Swift runtime";
        case VM_MEMORY_SWIFT_METADATA: return "Swift metadata";
        case VM_MEMORY_DHMM: return "DHMM";
        case VM_MEMORY_SCENEKIT: return "SceneKit";
        case VM_MEMORY_SKYWALK: return "Skywalk";
        case VM_MEMORY_IOSURFACE: return "IOSurface";
        case VM_MEMORY_LIBNETWORK: return "libnetwork";
        case VM_MEMORY_AUDIO: return "Audio";
        case VM_MEMORY_VIDEOBITSTREAM: return "Video bitstream";
        case VM_MEMORY_CM_XPC: return "CoreMedia XPC";
        case VM_MEMORY_CM_RPC: return "CoreMedia RPC";
        case VM_MEMORY_CM_MEMORYPOOL: return "CoreMedia memory pool";
        case VM_MEMORY_CM_READCACHE: return "CoreMedia read cache";
        case VM_MEMORY_CM_CRABS: return "CoreMedia crabs";
        case VM_MEMORY_QUICKLOOK_THUMBNAILS: return "QuickLook thumbnails";
        case VM_MEMORY_ACCOUNTS: return "Accounts";
        case VM_MEMORY_SANITIZER: return "Sanitizer";
        case VM_MEMORY_IOACCELERATOR: return "IOAccelerator";
#ifdef VM_MEMORY_CM_REGWARP
        case VM_MEMORY_CM_REGWARP: return "CoreMedia RegWarp";
#endif
#ifdef VM_MEMORY_EAR_DECODER
        case VM_MEMORY_EAR_DECODER: return "EAR decoder";
#endif
#ifdef VM_MEMORY_COREUI_CACHED_IMAGE_DATA
        case VM_MEMORY_COREUI_CACHED_IMAGE_DATA: return "CoreUI cached image data";
#endif
#ifdef VM_MEMORY_COLORSYNC
        case VM_MEMORY_COLORSYNC: return "ColorSync";
#endif
#ifdef VM_MEMORY_BTINFO
        case VM_MEMORY_BTINFO: return "Backtrace info";
#endif
#ifdef VM_MEMORY_ROSETTA
        case VM_MEMORY_ROSETTA: return "Rosetta";
#endif
        default: return NULL;
    }
}
