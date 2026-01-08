import {
  init,
  addField,
  removeField,
  debug,
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
    crashReporting: {
      enableNativeFatalIssues: true,
      UNSTABLE_enableJsErrors: true,
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
