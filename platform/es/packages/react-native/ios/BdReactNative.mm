#import "BdReactNative.h"
#import "Capture/Capture.h"
#import "BdReactNative-Swift.h"

@implementation BdReactNative
RCT_EXPORT_MODULE()

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

#ifndef RCT_NEW_ARCH_ENABLED

RCT_EXPORT_METHOD(init:(NSString*)apiKey
  sessionStrategy:(NSString*)sessionStrategy
  options:(NSDictionary*)options)
{
  NSString* apiURL = options[@"url"];
  BOOL enableNetworkInstrumentation = [options[@"enableNetworkInstrumentation"] boolValue];

  [CAPRNLogger
    startWithKey:apiKey
    sessionStrategy:sessionStrategy
    url:apiURL
    enableNetworkInstrumentation:enableNetworkInstrumentation
  ];
}

#else

RCT_EXPORT_METHOD(init:(NSString*)apiKey
  sessionStrategy:(NSString*)sessionStrategy
  options:(JS::NativeBdReactNative::InitOptions& )options)
{
  BOOL enableNetworkInstrumentation = options.enableNetworkInstrumentation().has_value() ? options.enableNetworkInstrumentation().value() : false;

  [CAPRNLogger
    startWithKey:apiKey
    sessionStrategy:sessionStrategy
    url:options.url()
    enableNetworkInstrumentation:enableNetworkInstrumentation
  ];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeBdReactNativeSpecJSI>(params);
}
#endif

@end
