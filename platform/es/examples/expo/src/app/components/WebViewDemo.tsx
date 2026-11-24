import React, { useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { WebView, type WebViewMessageEvent } from 'react-native-webview';

// Reports whether the Capture bridge was injected into the page. The bridge registers
// `window.webkit.messageHandlers.BitdriftLogger` on iOS and `window.BitdriftLogger` on Android.
const BITDRIFT_PROBE = `
  (function () {
    var iosBridge = window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.BitdriftLogger;
    window.ReactNativeWebView.postMessage(JSON.stringify({
      probe: 'bitdrift',
      url: String(window.location.href).slice(0, 40),
      bridge: Boolean(iosBridge || window.BitdriftLogger),
      config: Boolean(window.bitdrift && window.bitdrift.config),
    }));
  })();
  true;
`;

const SOURCE = { uri: 'https://bitdrift.io/' };

export function WebViewDemo({ onClose }: { onClose: () => void }): JSX.Element {
  const [status, setStatus] = useState<string[]>([]);

  const onMessage = (event: WebViewMessageEvent) => {
    try {
      const { probe, ...result } = JSON.parse(event.nativeEvent.data);
      if (probe === 'bitdrift') {
        setStatus((previous) => [...previous, JSON.stringify(result)]);
      }
    } catch {
      // Ignore messages that don't come from the probe.
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity style={styles.closeButton} onPress={onClose}>
          <Text style={styles.closeButtonText}>Close</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Automatic instrumentation</Text>
      </View>
      <Text testID="webview-bitdrift-status" style={styles.status}>
        {status.length === 0 ? 'pending' : status.join('\n')}
      </Text>
      <WebView
        source={SOURCE}
        injectedJavaScript={BITDRIFT_PROBE}
        onMessage={onMessage}
        style={styles.webView}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
  },
  closeButton: {
    backgroundColor: '#3498db',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 6,
    marginRight: 12,
  },
  closeButtonText: {
    color: '#fff',
    fontWeight: '600',
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
  },
  status: {
    fontFamily: 'Menlo',
    fontSize: 11,
    paddingHorizontal: 12,
    paddingBottom: 8,
  },
  webView: {
    flex: 1,
  },
});
