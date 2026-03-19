import {
  init,
  addField,
  removeField,
  debug,
  info,
  SessionStrategy,
  getSessionID,
  getSessionURL,
  getDeviceID,
} from '@bitdrift/react-native';

const BITDRIFT_API_KEY = process.env.EXPO_PUBLIC_BITDRIFT_API_KEY;

if (BITDRIFT_API_KEY) {
  init(BITDRIFT_API_KEY, SessionStrategy.Fixed, {
    url: process.env.EXPO_PUBLIC_BITDRIFT_API_URL ?? 'https://api.bitdrift.io',
    enableNetworkInstrumentation: true,
    
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
  debug('expo example initialized');
}
