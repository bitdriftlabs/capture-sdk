
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNBdReactNativeSpec/RNBdReactNativeSpec.h"

@interface BdReactNative : NSObject <NativeBdReactNativeSpec>
#else
#import <React/RCTBridgeModule.h>

@interface BdReactNative : NSObject <RCTBridgeModule>

#endif

@end
