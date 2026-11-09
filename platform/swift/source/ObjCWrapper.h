// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import <Foundation/Foundation.h>

@interface ObjCWrapper: NSObject

/**
 * Try to execute the block and catch any exceptions.
 */
+ (BOOL)doTry:(void(^)())block error:(NSError **)err;

/**
 * Assign a task delegate without allowing an Objective-C exception to unwind Swift frames.
 * Background session tasks reject this assignment with an exception.
 */
+ (BOOL)setTaskDelegate:(id<NSURLSessionTaskDelegate>)delegate forTask:(NSURLSessionTask *)task;

@end
