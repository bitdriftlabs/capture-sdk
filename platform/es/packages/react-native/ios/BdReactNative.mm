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

RCT_EXPORT_METHOD(getDeviceID:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  [CAPRNLogger getDeviceID:^(NSString *deviceID) {
    if (deviceID == nil || [deviceID isEqual:[NSNull null]] || [deviceID isEqualToString:@""]) {
      NSError *error = [NSError errorWithDomain:@"CAPRNLogger"
                                           code:404
                                       userInfo:@{NSLocalizedDescriptionKey: @"Device ID is undefined"}];
      reject(@"device_id_undefined", @"Device ID is undefined", error);
    } else {
      resolve(deviceID);
    }
  } rejecter:^(NSString *code, NSString *message, NSError *error) {
    reject(code, message, error);
  }];
}


#else

RCT_EXPORT_METHOD(init:(NSString*)apiKey
  sessionStrategy:(NSString*)sessionStrategy
  options:(JS::NativeBdReactNative::InitOptions& )options)
{
  BOOL enableNetworkInstrumentation = options != nil && options.enableNetworkInstrumentation().has_value() ? options.enableNetworkInstrumentation().value() : false;

  [CAPRNLogger
    startWithKey:apiKey
    sessionStrategy:sessionStrategy
    url:options.url()
    enableNetworkInstrumentation:enableNetworkInstrumentation
  ];
}

- (void)getDeviceID:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
    [CAPRNLogger getDeviceID:^(NSString *deviceID) {
      if (deviceID == nil || [deviceID isEqual:[NSNull null]] || [deviceID isEqualToString:@""]) {
        NSError *error = [NSError errorWithDomain:@"CAPRNLogger"
                                             code:404
                                         userInfo:@{NSLocalizedDescriptionKey: @"Device ID is undefined"}];
        reject(@"device_id_undefined", @"Device ID is undefined", error);
      } else {
        resolve(deviceID);
      }
    } rejecter:^(NSString *code, NSString *message, NSError *error) {
      reject(code, message, error);
    }];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeBdReactNativeSpecJSI>(params);
}
#endif

@end
