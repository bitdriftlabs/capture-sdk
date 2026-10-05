import React from 'react';
import { requireNativeComponent } from 'react-native';
import NativeWebView, { type WebViewProps } from 'react-native-webview';

export type BitdriftWebViewProps = Omit<WebViewProps, 'nativeConfig'>;

let nativeComponent: NonNullable<WebViewProps['nativeConfig']>['component'];

function getNativeComponent(): NonNullable<WebViewProps['nativeConfig']>['component'] {
  if (!nativeComponent) {
    nativeComponent = requireNativeComponent('BdReactNativeWebView');
  }

  return nativeComponent;
}

/**
 * A react-native-webview instrumented by bitdrift for this instance only.
 *
 * The native component subclasses react-native-webview and instruments the
 * underlying native WebView before its source is loaded.
 */
export const WebView = React.forwardRef<
  React.ElementRef<typeof NativeWebView>,
  BitdriftWebViewProps
>(function BitdriftWebView(
  props,
  ref,
) {
  return (
    <NativeWebView
      {...props}
      ref={ref}
      nativeConfig={{ component: getNativeComponent() }}
    />
  );
});
