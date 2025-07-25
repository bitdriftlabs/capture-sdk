// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import <MetricKit/MetricKit.h>

typedef NS_OPTIONS(NSUInteger, CAPDiagnosticType) {
    CAPDiagnosticTypeNone         = 0,
    /** Application termination events */
    CAPDiagnosticTypeCrash        = 1 << 0,
    /** Non-fatal app hangs */
    CAPDiagnosticTypeHang         = 1 << 1,
    /** Non-fatal disk write exceptions */
    CAPDiagnosticTypeDiskWrite    = 1 << 2,
    /** Non-fatal CPU usage exceptions */
    CAPDiagnosticTypeCPUException = 1 << 3,
};

@interface DiagnosticEventReporter : NSObject<MXMetricManagerSubscriber>
/**
 * Create a new reporter, including the write directory for any detected reports and library metadata to
 * include in the generated files
 *
 * @param path       destination directory for generated reports
 * @param sdkVersion current version of the Capture SDK
 * @param types      event types to report
 * @param seconds    number of seconds required to report `CAPDiagnosticTypeHang` events
 */
- (instancetype _Nonnull)initWithOutputDir:(NSURL *_Nonnull)path
                                sdkVersion:(NSString *_Nonnull)sdkVersion
                                eventTypes:(CAPDiagnosticType)types
                        minimumHangSeconds:(NSTimeInterval)seconds;

- (void)setMinimumHangSeconds:(NSTimeInterval)seconds;
@end
