//
//  BitdriftKSCrashWrapper.h
//  Capture
//
//  Created by Karl Stenerud on 22.07.25.
//

#pragma once

#import <Foundation/Foundation.h>

@interface BitdriftKSCrashWrapper: NSObject

+ (bool)configureWithBasePath:(NSURL *)basePath;

+ (NSDictionary *)enhancedMetricKitReport:(NSDictionary *)metricKitReport;

+ (bool)start;
+ (void)stop;

@end
