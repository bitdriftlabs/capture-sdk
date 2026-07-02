// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import "PreviousRunInfoRepository.h"

#import <errno.h>
#import <os/lock.h>
#import <string.h>
#import <sys/mman.h>
#import <fcntl.h>
#import <unistd.h>
#import <zlib.h>

enum {
    BDPreviousRunInfoVersion = 1,
    BDPreviousRunInfoStringCapacity = 64,
    BDPreviousRunInfoUUIDCapacity = 40,
};

static const int BDPreviousRunInfoInvalidFileDescriptor = -1;

typedef struct {
    uint32_t version;
    uint8_t is_terminating;
    uint8_t reserved[3];
    uint64_t boot_time;
    char app_version[BDPreviousRunInfoStringCapacity];
    char os_version[BDPreviousRunInfoStringCapacity];
    char binary_uuid[BDPreviousRunInfoUUIDCapacity];
    uint8_t is_initialized;
    uint8_t was_debugger_attached;
    uint8_t reserved2[2];
    uint32_t crc;
} BDPreviousRunInfoRecord;

// Computes CRC32 over the entire record with the crc field zeroed.
static uint32_t bdpri_compute_crc(const BDPreviousRunInfoRecord *record) {
    BDPreviousRunInfoRecord temp = *record;
    temp.crc = 0;
    return (uint32_t)crc32(0, (const Bytef *)&temp, sizeof(temp));
}

static NSString *bdpri_make_string(const char *buffer, size_t capacity) {
    size_t length = strnlen(buffer, capacity);
    NSData *data = [NSData dataWithBytes:buffer length:length];
    NSString *value = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    return value ?: @"";
}

static void bdpri_write_string(char *destination, size_t capacity, NSString *value) {
    memset(destination, 0, capacity);
    NSData *data = [value dataUsingEncoding:NSUTF8StringEncoding allowLossyConversion:NO];
    if (data.length == 0) {
        return;
    }

    size_t bytes_to_copy = MIN((size_t)data.length, capacity - 1);
    memcpy(destination, data.bytes, bytes_to_copy);
}

// Downgrades from NSURLFileProtectionComplete (the default) to CompleteUnlessOpen so the file stays
// writable while the device is locked. Without this, markTerminating() would fail to write to the
// mapped record if any write (like willTerminate) happens while the screen is locked.
static BOOL bdpri_disable_file_protection(NSString *path) {
    NSURL *url = [NSURL fileURLWithPath:path];

    NSURLFileProtectionType protection = nil;
    [url getResourceValue:&protection forKey:NSURLFileProtectionKey error:nil];

    if (![protection isEqualToString:NSURLFileProtectionComplete]) {
        return YES;
    }

    NSError *error = nil;
    return [url setResourceValue:NSURLFileProtectionCompleteUnlessOpen
                          forKey:NSURLFileProtectionKey
                           error:&error];
}

static int bdpri_open_file(NSString *path) {
    int fd = open(path.fileSystemRepresentation, O_RDWR | O_CREAT, 0600);
    if (fd == BDPreviousRunInfoInvalidFileDescriptor) {
        return BDPreviousRunInfoInvalidFileDescriptor;
    }

    if (!bdpri_disable_file_protection(path)) {
        close(fd);
        return BDPreviousRunInfoInvalidFileDescriptor;
    }

    return fd;
}

static void bdpri_close_file_descriptor(int *fd) {
    if (*fd != BDPreviousRunInfoInvalidFileDescriptor) {
        close(*fd);
        *fd = BDPreviousRunInfoInvalidFileDescriptor;
    }
}

static void bdpri_unmap_record(BDPreviousRunInfoRecord **record) {
    if (*record != NULL) {
        munmap(*record, sizeof(BDPreviousRunInfoRecord));
        *record = NULL;
    }
}

static BOOL bdpri_ensure_file_open(NSString *path, int *fd, NSError **error) {
    if (*fd != BDPreviousRunInfoInvalidFileDescriptor) {
        return YES;
    }

    *fd = bdpri_open_file(path);
    if (*fd != BDPreviousRunInfoInvalidFileDescriptor) {
        return YES;
    }

    if (error != nil) {
        *error = [NSError errorWithDomain:NSPOSIXErrorDomain code:errno userInfo:nil];
    }
    return NO;
}

static BDPreviousRunInfoSnapshot *bdpri_load_previous_run_info(int fd) {
    uint8_t buffer[sizeof(BDPreviousRunInfoRecord) + 1];
    ssize_t bytesRead = pread(fd, buffer, sizeof(buffer), 0);
    if (bytesRead != sizeof(BDPreviousRunInfoRecord)) {
        return nil;
    }

    BDPreviousRunInfoRecord record = {0};
    memcpy(&record, buffer, sizeof(record));

    if (record.version != BDPreviousRunInfoVersion || record.is_initialized != 1) {
        return nil;
    }

    uint32_t expected = record.crc;
    record.crc = 0;
    if ((uint32_t)crc32(0, (const Bytef *)&record, sizeof(record)) != expected) {
        return nil;
    }

    return [[BDPreviousRunInfoSnapshot alloc] initWithAppVersion:bdpri_make_string(record.app_version, sizeof(record.app_version))
                                                       osVersion:bdpri_make_string(record.os_version, sizeof(record.os_version))
                                                      binaryUUID:bdpri_make_string(record.binary_uuid, sizeof(record.binary_uuid))
                                                        bootTime:record.boot_time
                                                    wasCleanExit:record.is_terminating == 1
                                              wasDebuggerAttached:record.was_debugger_attached == 1];
}

// ftruncate grows the file (freshly created files are 0 bytes) to the record size, since mmap
// requires the backing file to already be at least as large as the mapped region.
static BOOL bdpri_resize_and_map_file(
    int fd,
    BDPreviousRunInfoRecord **mappedRecordOut,
    NSError **error
) {
    if (ftruncate(fd, (off_t)sizeof(BDPreviousRunInfoRecord)) != 0) {
        if (error != nil) {
            *error = [NSError errorWithDomain:NSPOSIXErrorDomain code:errno userInfo:nil];
        }
        return NO;
    }

    const int prot = PROT_READ | PROT_WRITE;
    // MAP_FILE: file-backed mapping (the default on Darwin, kept explicit for clarity).
    // MAP_SHARED: writes go back to the file and are visible even if the process dies before
    // munmap/close, which is what lets markTerminating() survive a crash right after it runs.
    const int flags = MAP_FILE | MAP_SHARED;
    void *ptr = mmap(NULL, sizeof(BDPreviousRunInfoRecord), prot, flags, fd, 0);
    if (ptr == MAP_FAILED) {
        if (error != nil) {
            *error = [NSError errorWithDomain:NSPOSIXErrorDomain code:errno userInfo:nil];
        }
        return NO;
    }

    memset(ptr, 0, sizeof(BDPreviousRunInfoRecord));
    *mappedRecordOut = ptr;
    return YES;
}

static BOOL bdpri_prepare_current_record(
    BDPreviousRunInfoRecord *record,
    NSString *appVersion,
    NSString *osVersion,
    NSString *binaryUUID,
    uint64_t bootTime,
    BOOL wasDebuggerAttached
) {
    if (record == NULL) {
        return NO;
    }

    record->boot_time = bootTime;
    record->was_debugger_attached = wasDebuggerAttached ? 1 : 0;
    bdpri_write_string(record->app_version, sizeof(record->app_version), appVersion);
    bdpri_write_string(record->os_version, sizeof(record->os_version), osVersion);
    bdpri_write_string(record->binary_uuid, sizeof(record->binary_uuid), binaryUUID);
    record->version = BDPreviousRunInfoVersion;
    record->is_initialized = 1;
    record->crc = bdpri_compute_crc(record);
    return YES;
}

@implementation BDPreviousRunInfoSnapshot

- (instancetype)initWithAppVersion:(NSString *)appVersion
                         osVersion:(NSString *)osVersion
                        binaryUUID:(NSString *)binaryUUID
                          bootTime:(uint64_t)bootTime
                      wasCleanExit:(BOOL)wasCleanExit
               wasDebuggerAttached:(BOOL)wasDebuggerAttached {
    self = [super init];
    if (self == nil) {
        return nil;
    }

    _appVersion = [appVersion copy];
    _osVersion = [osVersion copy];
    _binaryUUID = [binaryUUID copy];
    _bootTime = bootTime;
    _wasCleanExit = wasCleanExit;
    _wasDebuggerAttached = wasDebuggerAttached;
    return self;
}

@end

@interface BDPreviousRunInfoRepository () {
    os_unfair_lock _lock;
    NSString *_filePath;
    int _fileDescriptor;
    BDPreviousRunInfoRecord *_mappedRecord;
    BDPreviousRunInfoSnapshot *_previousRunInfo;
}
@end

@implementation BDPreviousRunInfoRepository

- (instancetype)initWithDirectory:(NSURL *)directory error:(NSError **)error {
    self = [super init];
    if (self == nil) {
        return nil;
    }

    if (![NSFileManager.defaultManager createDirectoryAtURL:directory
                                withIntermediateDirectories:YES
                                                 attributes:nil
                                                      error:error]) {
        return nil;
    }

    _lock = OS_UNFAIR_LOCK_INIT;
    _filePath = [[directory.path stringByAppendingPathComponent:@"previous_run_info.bin"] copy];
    _fileDescriptor = BDPreviousRunInfoInvalidFileDescriptor;
    _mappedRecord = NULL;
    return self;
}

- (void)dealloc {
    os_unfair_lock_lock(&_lock);
    bdpri_unmap_record(&_mappedRecord);
    bdpri_close_file_descriptor(&_fileDescriptor);
    os_unfair_lock_unlock(&_lock);
}

- (BDPreviousRunInfoSnapshot *)loadPreviousRunInfoAndReturnError:(NSError **)error {
    os_unfair_lock_lock(&_lock);
    if (_previousRunInfo != nil) {
        BDPreviousRunInfoSnapshot *snapshot = _previousRunInfo;
        os_unfair_lock_unlock(&_lock);
        return snapshot;
    }

    if (!bdpri_ensure_file_open(_filePath, &_fileDescriptor, error)) {
        os_unfair_lock_unlock(&_lock);
        return nil;
    }

    _previousRunInfo = bdpri_load_previous_run_info(_fileDescriptor);
    BDPreviousRunInfoSnapshot *snapshot = _previousRunInfo;
    os_unfair_lock_unlock(&_lock);
    return snapshot;
}

- (BOOL)prepareCurrentRunInfoWithAppVersion:(NSString *)appVersion
                                  osVersion:(NSString *)osVersion
                                 binaryUUID:(NSString *)binaryUUID
                                   bootTime:(uint64_t)bootTime
                        wasDebuggerAttached:(BOOL)wasDebuggerAttached
                                      error:(NSError **)error {
    os_unfair_lock_lock(&_lock);
    if (_mappedRecord != NULL) {
        os_unfair_lock_unlock(&_lock);
        return YES;
    }

    if (!bdpri_ensure_file_open(_filePath, &_fileDescriptor, error)) {
        os_unfair_lock_unlock(&_lock);
        return NO;
    }

    if (!bdpri_resize_and_map_file(_fileDescriptor, &_mappedRecord, error)) {
        bdpri_close_file_descriptor(&_fileDescriptor);
        os_unfair_lock_unlock(&_lock);
        return NO;
    }

    BOOL prepared = bdpri_prepare_current_record(
        _mappedRecord,
        appVersion,
        osVersion,
        binaryUUID,
        bootTime,
        wasDebuggerAttached
    );
    if (!prepared) {
        bdpri_unmap_record(&_mappedRecord);
        bdpri_close_file_descriptor(&_fileDescriptor);
        os_unfair_lock_unlock(&_lock);
        return NO;
    }

    bdpri_close_file_descriptor(&_fileDescriptor);
    os_unfair_lock_unlock(&_lock);
    return YES;
}

- (void)markTerminating {
    os_unfair_lock_lock(&_lock);
    if (_mappedRecord != NULL) {
        _mappedRecord->is_terminating = 1;
        _mappedRecord->crc = bdpri_compute_crc(_mappedRecord);
    }
    os_unfair_lock_unlock(&_lock);
}

@end
