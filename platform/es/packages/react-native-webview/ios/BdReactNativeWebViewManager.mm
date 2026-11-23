#import "BdReactNativeWebViewManager.h"
#import "BdReactNativeWebView.h"

@implementation BdReactNativeWebViewManager

RCT_EXPORT_MODULE(BdReactNativeWebView)

- (UIView *)view
{
  return [[BdReactNativeWebView alloc] init];
}

@end
