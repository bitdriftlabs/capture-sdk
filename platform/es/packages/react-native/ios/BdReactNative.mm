#import "BdReactNative.h"
#import "Capture/Capture.h"
#import "BdReactNative-Swift.h"

@implementation BdReactNative
RCT_EXPORT_MODULE()

// Must match src/index.tsx ISSUE_REPORT_EVENT and Android equivalent.
static NSString *const kIssueReportEventName = @"BdReactNative.onBeforeReportSend";
static NSNotificationName const kIssueReportNotificationName = @"BdReactNative.onBeforeReportSend";

- (NSArray<NSString *> *)supportedEvents
{
  return @[kIssueReportEventName];
}

- (void)startObserving
{
  [[NSNotificationCenter defaultCenter] addObserver:self
                                           selector:@selector(handleIssueReportNotification:)
                                               name:kIssueReportNotificationName
                                             object:nil];
}

- (void)stopObserving
{
  [[NSNotificationCenter defaultCenter] removeObserver:self
                                                  name:kIssueReportNotificationName
                                                object:nil];
}

- (void)handleIssueReportNotification:(NSNotification *)notification
{
  NSDictionary *payload = notification.userInfo ?: @{};
  dispatch_async(dispatch_get_main_queue(), ^{
    [self sendEventWithName:kIssueReportEventName body:payload];
  });
}

RCT_EXPORT_METHOD(log:(double)level
      message:(NSString*)message
      fields:(NSDictionary*)fields)
{
  [CAPLogger logWithLevel:LogLevel(level) message:message fields:fields];
}

RCT_EXPORT_METHOD(addField:(NSString*)key
      value:(NSString*)value)
{
    [CAPRNLogger addField:key value:value];
}

RCT_EXPORT_METHOD(removeField:(NSString*)key)
{
    [CAPRNLogger removeField:key];
}

RCT_EXPORT_METHOD(getDeviceID:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
    [CAPRNLogger getDeviceID:resolve rejecter:reject];
}

RCT_EXPORT_METHOD(getSessionID:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
    [CAPRNLogger getSessionID:resolve rejecter:reject];
}

RCT_EXPORT_METHOD(getSessionURL:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject) {
    [CAPRNLogger getSessionURL:resolve rejecter:reject];
}

RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getPreviousRunInfo) {
    return [CAPRNLogger getPreviousRunInfo];
}

RCT_EXPORT_METHOD(logScreenView:(NSString *)screenName)
{
    [CAPRNLogger logScreenViewWithScreenName:screenName];
}

RCT_EXPORT_METHOD(logAppLaunchTTI:(double)ttiMs)
{
    [CAPRNLogger logAppLaunchTTIWithTTI:ttiMs / 1000];
}

RCT_EXPORT_METHOD(reportJsError:(NSString*)errorName
      message:(NSString*)message
      stack:(NSString*)stack
      isFatal:(BOOL)isFatal
      engine:(NSString*)engine
      libraryVersion:(NSString*)libraryVersion)
{
    [CAPRNLogger reportJsErrorWithErrorName:errorName message:message stack:stack isFatal:isFatal engine:engine reactNativeVersion:libraryVersion];
}

RCT_EXPORT_METHOD(setFeatureFlagExposureString:(NSString*)name
      variant:(NSString*)variant)
{
    [CAPRNLogger setFeatureFlagExposureStringWithName:name variant:variant];
}

RCT_EXPORT_METHOD(setFeatureFlagExposureBool:(NSString*)name
      variant:(BOOL)variant)
{
    [CAPRNLogger setFeatureFlagExposureBoolWithName:name variant:variant];
}

#ifndef RCT_NEW_ARCH_ENABLED

RCT_EXPORT_METHOD(init:(NSString*)apiKey
  sessionStrategy:(NSString*)sessionStrategy
  options:(NSDictionary*)options)
{
  NSString* apiURL = options[@"url"];
  BOOL enableNetworkInstrumentation = [options[@"enableNetworkInstrumentation"] boolValue];
  
  NSDictionary* crashReportingOptions = options[@"crashReporting"];
  NSNumber* enableNativeFatalIssuesValue = crashReportingOptions[@"enableNativeFatalIssues"];
  BOOL enableNativeFatalIssues = enableNativeFatalIssuesValue != nil ? [enableNativeFatalIssuesValue boolValue] : YES;
  BOOL enableJsErrors = [crashReportingOptions[@"UNSTABLE_enableJsErrors"] boolValue];
  NSNumber* enableIssueCallbackBridgeValue = crashReportingOptions[@"enableIssueCallbackBridge"];
  BOOL enableIssueCallbackBridge = enableIssueCallbackBridgeValue != nil ? [enableIssueCallbackBridgeValue boolValue] : NO;

  [CAPRNLogger
    startWithKey:apiKey
    sessionStrategy:sessionStrategy
    url:apiURL
    enableNetworkInstrumentation:enableNetworkInstrumentation
    enableNativeFatalIssues:enableNativeFatalIssues
    enableJsErrors:enableJsErrors
    enableIssueCallbackBridge:enableIssueCallbackBridge
  ];
}

#else

RCT_EXPORT_METHOD(init:(NSString*)apiKey
  sessionStrategy:(NSString*)sessionStrategy
  options:(JS::NativeBdReactNative::InitOptions& )options)
{
  BOOL enableNetworkInstrumentation = options.enableNetworkInstrumentation().has_value() ? options.enableNetworkInstrumentation().value() : false;
  
  BOOL enableNativeFatalIssues = true;
  BOOL enableJsErrors = false;
  BOOL enableIssueCallbackBridge = false;
  if (options.crashReporting().has_value()) {
    auto crashReporting = options.crashReporting().value();
    enableNativeFatalIssues = crashReporting.enableNativeFatalIssues().has_value() ? crashReporting.enableNativeFatalIssues().value() : true;
    enableJsErrors = crashReporting.UNSTABLE_enableJsErrors().has_value() ? crashReporting.UNSTABLE_enableJsErrors().value() : false;
    enableIssueCallbackBridge = crashReporting.enableIssueCallbackBridge().has_value() ? crashReporting.enableIssueCallbackBridge().value() : false;
  }

  [CAPRNLogger
    startWithKey:apiKey
    sessionStrategy:sessionStrategy
    url:options.url()
    enableNetworkInstrumentation:enableNetworkInstrumentation
    enableNativeFatalIssues:enableNativeFatalIssues
    enableJsErrors:enableJsErrors
    enableIssueCallbackBridge:enableIssueCallbackBridge
  ];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeBdReactNativeSpecJSI>(params);
}
#endif

@end
