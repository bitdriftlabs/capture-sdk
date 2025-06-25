// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import <MetricKit/MetricKit.h>

@interface DiagnosticEventReporter : NSObject<MXMetricManagerSubscriber>
/**
 * Create a new reporter, including the write directory for any detected reports and library metadata to
 * include in the generated files
 */
- (instancetype _Nonnull)initWithOutputDir:(NSURL *_Nonnull)path
                                sdkVersion:(NSString *_Nonnull)sdkVersion
                        minimumHangSeconds:(NSTimeInterval)seconds;

- (void)setMinimumHangSeconds:(NSTimeInterval)seconds;
@end
