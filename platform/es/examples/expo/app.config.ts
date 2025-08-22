const newArchEnabled = process.env.NEW_ARCH !== 'false';

export default {
  expo: {
    name: 'ExpoExample',
    newArchEnabled,
    slug: 'expo-example',
    entryPoint: 'expo-router/entry',
    version: '1.0.0',
    orientation: 'portrait',
    icon: './assets/icon.png',
    splash: {
      image: './assets/splash.png',
      resizeMode: 'contain',
      backgroundColor: '#ffffff',
    },
    updates: {
      fallbackToCacheTimeout: 0,
    },
    assetBundlePatterns: ['**/*'],
    ios: {
      supportsTablet: true,
      bundleIdentifier: 'com.bitdrift.io.expoExample',
    },
    android: {
      adaptiveIcon: {
        foregroundImage: './assets/adaptive-icon.png',
        backgroundColor: '#FFFFFF',
      },
      package: 'com.bitdrift.io.expoExample',
    },
    web: {
      favicon: './assets/favicon.png',
      bundler: 'metro',
    },
    plugins: [
      [
        '../../dist/react-native/app.plugin.js', // In a real project, this would be '@bitdrift/react-native'
        {
          networkInstrumentation: true,
        },
      ],
      [
        'expo-build-properties',
        {
          android: {
            minSdkVersion: 24,
            kotlinVersion: '2.1.21'
          },
        },
      ],
    ],
  },
};
