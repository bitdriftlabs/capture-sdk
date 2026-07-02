// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import "PreviousRunStateCaptureSupport.h"

#import <mach-o/dyld.h>
#import <mach-o/loader.h>
#import <sys/sysctl.h>

static const struct mach_header *bdprcs_image_header_for_path(NSString *path) {
    for (uint32_t idx = 0; idx < _dyld_image_count(); idx++) {
        const char *imageName = _dyld_get_image_name(idx);
        const struct mach_header *header = _dyld_get_image_header(idx);
        if (header == NULL || imageName == NULL) {
            continue;
        }

        NSString *candidate = [NSString stringWithUTF8String:imageName];
        if ([candidate isEqualToString:path]) {
            return header;
        }
    }

    return NULL;
}

static const struct mach_header *bdprcs_main_image_header(void) {
    NSString *executablePath = NSBundle.mainBundle.executablePath;
    const struct mach_header *header = bdprcs_image_header_for_path(executablePath);
    if (header != NULL) {
        return header;
    }

    // Fall back to the first loaded image if the main bundle path didn't match.
    return _dyld_image_count() > 0 ? _dyld_get_image_header(0) : NULL;
}

static NSString *bdprcs_uuid_from_header(const struct mach_header *header) {
    if (header == NULL) {
        return nil;
    }

    uintptr_t cursor = 0;
    uint32_t commandCount = 0;
    if (header->magic == MH_MAGIC_64 || header->magic == MH_CIGAM_64) {
        const struct mach_header_64 *header64 = (const struct mach_header_64 *)header;
        cursor = (uintptr_t)(header64 + 1);
        commandCount = header64->ncmds;
    } else {
        cursor = (uintptr_t)(header + 1);
        commandCount = header->ncmds;
    }

    for (uint32_t idx = 0; idx < commandCount; idx++) {
        const struct load_command *command = (const struct load_command *)cursor;
        if (command->cmd == LC_UUID) {
            const struct uuid_command *uuidCommand = (const struct uuid_command *)command;
            NSUUID *uuid = [[NSUUID alloc] initWithUUIDBytes:uuidCommand->uuid];
            return uuid.UUIDString.lowercaseString;
        }
        cursor += command->cmdsize;
    }

    return nil;
}

@implementation BDPreviousRunStateCaptureSupport

+ (NSString *)mainBinaryUUID {
    return bdprcs_uuid_from_header(bdprcs_main_image_header());
}

+ (uint64_t)systemBootTime {
    struct timeval bootTime = {0};
    size_t size = sizeof(bootTime);
    int mib[2] = {CTL_KERN, KERN_BOOTTIME};
    if (sysctl(mib, 2, &bootTime, &size, NULL, 0) != 0) {
        return 0;
    }

    return ((uint64_t)bootTime.tv_sec * 1000000ULL) + (uint64_t)bootTime.tv_usec;
}

+ (nullable NSString *)osBuildVersion {
    static NSString *cached = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        char build[64] = {0};
        size_t size = sizeof(build);
        if (sysctlbyname("kern.osversion", build, &size, NULL, 0) == 0 && size > 1) {
            cached = [NSString stringWithUTF8String:build];
        }
    });
    return cached;
}

@end
