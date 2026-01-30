import {
  init,
  debug,
  SessionStrategy,
  getSessionID,
  getSessionURL,
  getDeviceID,
} from '@bitdrift/react-native';

const BITDRIFT_API_KEY = 'ADD_YOUR_API_KEY_HERE';
const BITDRIFT_API_URL = 'https://api.bitdrift.io';

if (BITDRIFT_API_KEY) {
  init(BITDRIFT_API_KEY, SessionStrategy.Fixed, {
    url: BITDRIFT_API_URL,
    crashReporting: {
      enableNativeFatalIssues: true,
      UNSTABLE_enableJsErrors: true,
    },
  });

  getSessionID().then((id) => console.log('Session ID:', id));
  getSessionURL().then((url) => console.log('Session URL:', url));
  getDeviceID().then((id) => console.log('Device ID:', id));

  debug('HermesSample initialized');
}
