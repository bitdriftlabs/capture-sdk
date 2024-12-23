#import "BdReactNative.h"
#import "Capture/Capture.h"
#import "bd_react_native-Swift.h"

@implementation BdReactNative
RCT_EXPORT_MODULE()

RCT_EXPORT_METHOD(log:(double)level
      message:(NSString*)message
      fields:(NSDictionary*)fields)
{
  [CAPLogger logWithLevel:LogLevel(level) message:message fields:fields];
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
  options:(JS::NativeBdReactNative::SpecInitOptions &)options)
{
  BOOL enableNetworkInstrumentation = options.enableNetworkInstrumentation().has_value() ?
    options.enableNetworkInstrumentation().value() : false;

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
