import {
  init,
  addField,
  removeField,
  debug,
  info,
  isTracingActive,
  setEntityId,
  SessionStrategy,
  getSessionID,
  getSessionURL,
  getDeviceID,
  getSdkStatus,
} from '@bitdrift/react-native';

const BITDRIFT_API_KEY = process.env.EXPO_PUBLIC_BITDRIFT_API_KEY;

if (BITDRIFT_API_KEY) {
  init(BITDRIFT_API_KEY, SessionStrategy.Fixed, {
    url: process.env.EXPO_PUBLIC_BITDRIFT_API_URL ?? 'https://api.bitdrift.io',
    enableNetworkInstrumentation: true,
    startResult: (result) => {
      console.log('Start result:', result);
      console.log('SDK status after start result:', getSdkStatus());
    },
    UNSTABLE_webView: {
      capturePageViews: true,
      captureNetworkRequests: true,
      captureNavigationEvents: true,
      captureWebVitals: true,
      captureLongTasks: true,
      captureConsoleLogs: true,
      captureUserInteractions: true,
      captureErrors: true,
    },
    
    // enableNativeFatalIssues Should be enabled by default
    crashReporting: {
      UNSTABLE_enableJsErrors: true,
      UNSTABLE_onBeforeReportSend: (report) => {
        console.log('Issue callback triggered:', report);
        info('Issue callback triggered', {
          reportType: report.reportType,
          session: report.sessionId,
          details: report.details,
          reason: report.reason,
          fields: JSON.stringify(report.fields),
        });
      },
    },
  });

  getSessionID().then((sessionID) => {
    console.log('Session ID:', sessionID);
  });

  getSessionURL().then((sessionURL) => {
    console.log('Session URL:', sessionURL);
  });

  getDeviceID().then((deviceID) => {
    console.log('Device ID:', deviceID);
  });

  addField('environment', 'expo');
  removeField('environment');
  addField('environment', 'expo');
  setEntityId('react-native-entity-id');
  console.log('Tracing active after init:', isTracingActive());
  debug('expo example initialized');
}
