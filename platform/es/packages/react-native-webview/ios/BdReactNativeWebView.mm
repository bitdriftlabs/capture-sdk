#import "BdReactNativeWebView.h"
#import <WebKit/WebKit.h>
#import <BdReactNative/BdReactNative.h>

@interface RNCWebViewImpl (BdReactNativeWebViewInternals)
- (void)visitSource;
@end

@implementation BdReactNativeWebView {
  BOOL _bitdriftInstrumented;
}

- (void)visitSource
{
  [self instrumentWebViewIfNeeded];
  [super visitSource];
}

- (void)instrumentWebViewIfNeeded
{
  if (_bitdriftInstrumented) {
    return;
  }

  WKWebView *webView = [self findWebViewInView:self];
  if (webView != nil) {
    BdReactNativeInstrumentWebView(webView);
    _bitdriftInstrumented = YES;
  }
}

- (WKWebView *)findWebViewInView:(UIView *)view
{
  if ([view isKindOfClass:[WKWebView class]]) {
    return (WKWebView *)view;
  }

  for (UIView *subview in view.subviews) {
    WKWebView *webView = [self findWebViewInView:subview];
    if (webView != nil) {
      return webView;
    }
  }

  return nil;
}

@end
