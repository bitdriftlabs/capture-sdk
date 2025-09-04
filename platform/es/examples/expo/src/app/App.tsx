/* eslint-disable jsx-a11y/accessible-emoji */
import '../lib/bitdrift'; // Must be near the top

import React, { useState } from 'react';
import {
  SafeAreaView,
  StyleSheet,
  View,
  Text,
  StatusBar,
  Pressable,
} from 'react-native';
import {
  generateDeviceCode,
  info,
  debug,
  trace,
  error,
  warn,
  logScreenView,
  logAppLaunchTTI,
} from '@bitdrift/react-native';
import axios from 'axios';
import { Picker } from '@react-native-picker/picker';
import { InfoBlock } from './components/InfoBlock';
import { showToast } from './utils/showToast';

const LOG_LEVELS = new Map([
  ['info', info],
  ['debug', debug],
  ['trace', trace],
  ['error', error],
  ['warn', warn],
]);

const hasAPIKeyConfigured = Boolean(
  process.env.EXPO_PUBLIC_BITDRIFT_API_KEY &&
    process.env.EXPO_PUBLIC_BITDRIFT_API_URL,
);

const sendRandomRequest = async () => {
  const endpoints = [
    'https://jsonplaceholder.typicode.com/posts',
    'https://jsonplaceholder.typicode.com/users',
  ];
  const randomEndpoint =
    endpoints[Math.floor(Math.random() * endpoints.length)];
  try {
    const response = await axios.get(randomEndpoint);
    console.log('Random Request Response:', response.data);
  } catch (err: any) {
    console.log('Error making random request:', err);
  }
};

const HomeScreen = () => {
  const [selectedLogLevel, setSelectedLogLevel] = useState(
    LOG_LEVELS.keys().next().value,
  );
  const [temporaryDeviceCode, setTemporaryDeviceCode] = useState<string | null>(
    null,
  );

  const logMessageHandler = () => {
    if (selectedLogLevel) {
      const log = LOG_LEVELS.get(selectedLogLevel);
      log?.(`[${selectedLogLevel.toUpperCase()}]: Log emitted`, {
        randomNumber: Math.random(),
      });

      logScreenView('HomeScreen');
      logAppLaunchTTI(1.0);
      showToast(`Logged: [${selectedLogLevel.toUpperCase()}]: Log emitted`);
    }
  };

  const handleGenerateTemporaryDeviceCode = async () => {
    const deviceCode = await generateDeviceCode();
    setTemporaryDeviceCode(deviceCode);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>
        @bitdrift/react-native Expo Integration Example
      </Text>

      {!hasAPIKeyConfigured && (
        <InfoBlock
          warning
          message="Please configure your Bitdrift API Key and URL in your .env file to see logs in the Bitdrift dashboard."
        />
      )}

      {hasAPIKeyConfigured && (
        <View style={styles.inlineContainer}>
          <Pressable
            style={({ pressed }) => [
              styles.button,
              pressed && styles.buttonActive,
            ]}
            onPress={handleGenerateTemporaryDeviceCode}
          >
            <Text style={styles.buttonText}>Generate Temporary Device Code</Text>
          </Pressable>
          {temporaryDeviceCode && (
            <Text selectable style={{ margin: 10 }}>
              {temporaryDeviceCode}
            </Text>
          )}
        </View>
      )}

      <Pressable
        style={({ pressed }) => [styles.button, pressed && styles.buttonActive]}
        onPress={sendRandomRequest}
      >
        <Text style={styles.buttonText}>Send Random REST Request</Text>
      </Pressable>

      <View style={styles.inlineContainer}>
        <Picker
          selectedValue={selectedLogLevel}
          onValueChange={(value) => setSelectedLogLevel(value)}
          style={styles.picker}
        >
          {Array.from(LOG_LEVELS.keys()).map((level) => (
            <Picker.Item key={level} label={level} value={level} />
          ))}
        </Picker>
        <Pressable
          style={({ pressed }) => [
            styles.button,
            pressed && styles.buttonActive,
          ]}
          onPress={logMessageHandler}
        >
          <Text style={styles.buttonText}>Log</Text>
        </Pressable>
      </View>
    </View>
  );
};

export const App = () => {
  return (
    <>
      <StatusBar barStyle="dark-content" />
      <SafeAreaView
        style={{
          flex: 1,
        }}
      >
        <HomeScreen />
      </SafeAreaView>
    </>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    justifyContent: 'center',
    alignItems: 'flex-start',
  },
  inlineContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 20,
  },
  button: {
    backgroundColor: '#00A76F',
    padding: 10,
    borderRadius: 5,
    marginVertical: 10,
  },
  buttonActive: {
    backgroundColor: '#007867',
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
  },
  picker: {
    height: 50,
    width: 200,
    marginVertical: 10,
    marginRight: 10,
    backgroundColor: '#ccc',
  },
  input: {
    borderWidth: 1,
    borderColor: '#ccc',
    padding: 10,
    borderRadius: 5,
    width: '80%',
    marginVertical: 10,
  },
});

export default App;
