// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import "MetricKitDiagnosticParsing.h"

#import <mach/exception_types.h>
#import <signal.h>

static NSString *trimmed_value_after_prefix(NSString *line, NSString *prefix) {
  if (![line hasPrefix:prefix]) {
    return nil;
  }

  NSString *value = [line substringFromIndex:prefix.length];
  return [value stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
}

@implementation MetricKitDiagnosticParsing

- (NSString *)nameForReportType:(ReportType)reportType {
  switch (reportType) {
    case ReportTypeNativeCrash:
      return @"crash";
    case ReportTypeAppNotResponding:
      return @"anr";
    case ReportTypeNone:
    default:
      return @"unknown";
  }
}

#define print_case(name) case name: return @#name

- (NSString *)nameForExceptionType:(int32_t)exceptionType {
  switch (exceptionType) {
    print_case(EXC_BAD_ACCESS);
    print_case(EXC_BAD_INSTRUCTION);
    print_case(EXC_SYSCALL);
    print_case(EXC_MACH_SYSCALL);
    print_case(EXC_CRASH);
    print_case(EXC_RESOURCE);
    print_case(EXC_GUARD);
    print_case(EXC_CORPSE_NOTIFY);
    print_case(EXC_ARITHMETIC);
    print_case(EXC_EMULATION);
    print_case(EXC_SOFTWARE);
    print_case(EXC_BREAKPOINT);
    default:
      return nil;
  }
}

- (NSString *)nameForSignal:(int32_t)signal {
  switch (signal) {
    print_case(SIGHUP);
    print_case(SIGINT);
    print_case(SIGQUIT);
    print_case(SIGILL);
    print_case(SIGTRAP);
    print_case(SIGABRT);
    print_case(SIGFPE);
    print_case(SIGKILL);
    print_case(SIGBUS);
    print_case(SIGSEGV);
    print_case(SIGSYS);
    print_case(SIGPIPE);
    print_case(SIGALRM);
    print_case(SIGTERM);
    print_case(SIGURG);
    print_case(SIGSTOP);
    print_case(SIGTSTP);
    print_case(SIGCONT);
    print_case(SIGCHLD);
    print_case(SIGTTIN);
    print_case(SIGTTOU);
    print_case(SIGXCPU);
    print_case(SIGXFSZ);
    print_case(SIGVTALRM);
    print_case(SIGPROF);
    print_case(SIGWINCH);
    print_case(SIGINFO);
    print_case(SIGUSR1);
    print_case(SIGUSR2);
    default:
      return nil;
  }
}

#undef print_case

- (NSString *)nameForExceptionType:(int32_t)exceptionType signal:(int32_t)signal {
  return [self nameForExceptionType:exceptionType] ?: [self nameForSignal:signal];
}

- (NSString *)reasonForCrashWithName:(NSString *)name
                  metricKitReasonName:(NSString *)exceptionReasonName
       metricKitReasonComposedMessage:(NSString *)exceptionReasonComposedMessage
                    capturedCrashName:(NSString *)capturedCrashName
                  capturedCrashReason:(NSString *)capturedCrashReason
                    terminationReason:(NSString *)terminationReason
              virtualMemoryRegionInfo:(NSString *)vmRegionInfo
                        exceptionCode:(NSNumber *)exceptionCode
                               signal:(int32_t)signal {
  NSMutableArray<NSString *> *components = [NSMutableArray new];
  BOOL hasMetricKitExceptionReason = exceptionReasonName != nil;
  if (hasMetricKitExceptionReason) {
    [components addObject:[NSString stringWithFormat:@"%@: %@", exceptionReasonName, exceptionReasonComposedMessage]];
  }
  if (!hasMetricKitExceptionReason && capturedCrashName != nil && capturedCrashReason != nil) {
    [components addObject:[NSString stringWithFormat:@"%@: %@", capturedCrashName, capturedCrashReason]];
  }
  if (terminationReason != nil) {
    [components addObject:terminationReason];
  }
  if (vmRegionInfo != nil) {
    [components addObject:vmRegionInfo];
  }
  if (components.count > 0) {
    return [components componentsJoinedByString:@".\n"];
  }
  if (exceptionCode != nil) {
    return [NSString stringWithFormat:@"code: %ld, signal: %@", exceptionCode.longValue, [self nameForSignal:signal]];
  }

  NSString *reason = [self nameForSignal:signal];
  return [reason isEqualToString:name] ? nil : reason;
}

- (BOOL)isWatchdogHangTerminationWithExceptionType:(NSNumber *)exceptionType
                                             signal:(NSNumber *)signal
                                  terminationReason:(NSString *)terminationReason
                                      exceptionCode:(NSNumber *)exceptionCode {
  return [exceptionType isEqualToNumber:@EXC_CRASH] && [signal isEqualToNumber:@SIGKILL]
      && ([[terminationReason lowercaseString] containsString:@"0x8badf00d"]
          || (terminationReason == nil && [exceptionCode isEqualToNumber:@0]));
}

- (NSDictionary<NSString *, NSString *> *)parseTerminationContext:(NSString *)terminationReason {
  if (terminationReason.length == 0) {
    return @{};
  }

  NSMutableDictionary<NSString *, NSString *> *fields = [NSMutableDictionary dictionary];
  NSError *error = nil;
  NSRegularExpression *headerPattern =
      [NSRegularExpression regularExpressionWithPattern:@"domain:(\\S+)\\s+code:(\\S+)\\s+explanation:(.*)$"
                                                 options:NSRegularExpressionAnchorsMatchLines
                                                   error:&error];
  NSTextCheckingResult *headerMatch = [headerPattern firstMatchInString:terminationReason
                                                                 options:0
                                                                   range:NSMakeRange(0, terminationReason.length)];
  if (headerMatch.numberOfRanges == 4) {
    fields[@"domain"] = [terminationReason substringWithRange:[headerMatch rangeAtIndex:1]];
    fields[@"code"] = [terminationReason substringWithRange:[headerMatch rangeAtIndex:2]];
    fields[@"explanation"] = [terminationReason substringWithRange:[headerMatch rangeAtIndex:3]];
  }

  for (NSString *line in [terminationReason componentsSeparatedByCharactersInSet:[NSCharacterSet newlineCharacterSet]]) {
    NSString *processVisibility = trimmed_value_after_prefix(line, @"ProcessVisibility:");
    if (processVisibility != nil) {
      fields[@"process_visibility"] = processVisibility;
      continue;
    }

    NSString *processState = trimmed_value_after_prefix(line, @"ProcessState:");
    if (processState != nil) {
      fields[@"process_state"] = processState;
      continue;
    }

    NSString *watchdogEvent = trimmed_value_after_prefix(line, @"WatchdogEvent:");
    if (watchdogEvent != nil) {
      fields[@"watchdog_event"] = watchdogEvent;
      continue;
    }

    NSString *watchdogVisibility = trimmed_value_after_prefix(line, @"WatchdogVisibility:");
    if (watchdogVisibility != nil) {
      fields[@"watchdog_visibility"] = watchdogVisibility;
    }
  }

  return fields;
}

- (void)timestampComponentsFor:(NSTimeInterval)timestamp
                        seconds:(uint64_t *)seconds
                          nanos:(uint32_t *)nanos {
  long double whole_seconds = truncl(timestamp);
  *seconds = (uint64_t)whole_seconds;
  *nanos = (uint32_t)((timestamp - whole_seconds) * NSEC_PER_SEC);
}

- (int8_t)architectureConstantFor:(NSString *)platformArchitecture {
  if (!platformArchitecture) {
    return /* Architecture.Unknown */ 0;
  }
  return [platformArchitecture containsString:@"arm64"] ? /* Architecture.arm64 */ 2 : /* Architecture.x86_64 */ 4;
}

@end
