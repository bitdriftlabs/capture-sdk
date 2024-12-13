#import "BdReactNative.h"
#import "Capture/Capture.h"

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
      options:(NSDictionary*)options)
{
  NSString *apiURL = options[@"url"];

  if (apiURL != NULL) {
    [CAPLogger
      startWithAPIKey:apiKey
      sessionStrategy:[CAPSessionStrategy activityBased]
      apiURL:[NSURL URLWithString:apiURL]
    ];
  } else {
    [CAPLogger
      startWithAPIKey:apiKey
      sessionStrategy:[CAPSessionStrategy activityBased]
      apiURL:[NSURL URLWithString:@"https://api.bitdrift.io/"]
    ];
  }
}

#else

RCT_EXPORT_METHOD(init:(NSString*)apiKey
                  options:(JS::NativeBdReactNative::SpecInitOptions &)options)
{
    if (options.url() != NULL) {
        [CAPLogger
         startWithAPIKey:apiKey
         sessionStrategy:[CAPSessionStrategy activityBased]
         apiURL:[NSURL URLWithString:options.url()]
        ];
    } else {
        [CAPLogger
         startWithAPIKey:apiKey
         sessionStrategy:[CAPSessionStrategy activityBased]
         apiURL:[NSURL URLWithString:@"https://api.bitdrift.io/"]
        ];
    }
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeBdReactNativeSpecJSI>(params);
}
#endif

@end
