
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNBdReactNativeSpec/RNBdReactNativeSpec.h"
#import <React/RCTEventEmitter.h>

@interface BdReactNative : RCTEventEmitter <NativeBdReactNativeSpec>
#else
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface BdReactNative : RCTEventEmitter <RCTBridgeModule>

#endif

@end
