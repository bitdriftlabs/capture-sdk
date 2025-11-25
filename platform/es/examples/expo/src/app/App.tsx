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
  Clipboard,
  Alert,
  Modal,
  Platform,
} from 'react-native';
import {
  generateDeviceCode,
  getSessionID,
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

const ERROR_TYPES = ['Error', 'TypeError', 'ReferenceError', 'SyntaxError', 'RangeError', 'PromiseRejection', 'AsyncError'] as const;

type ErrorType = typeof ERROR_TYPES[number];

const triggerGlobalJsError = (errorType: ErrorType = 'Error') => {
  const buildType = __DEV__ ? 'Debug' : 'Release';
  const message = `${buildType} build - ${errorType} error triggered for testing`;
  
  switch (errorType) {
    case 'TypeError':
      throw new TypeError(message);
    case 'ReferenceError':
      throw new ReferenceError(message);
    case 'SyntaxError':
      throw new SyntaxError(message);
    case 'RangeError':
      throw new RangeError(message);
    case 'PromiseRejection':
      Promise.reject(new Error(message));
      return;
    case 'AsyncError':
      setTimeout(() => {
        throw new Error(message);
      }, 0);
      return;
    default:
      throw new Error(message);
  }
};


const LOG_LEVEL_ARRAY = Array.from(LOG_LEVELS.keys());

const HomeScreen = () => {
  const [selectedLogLevel, setSelectedLogLevel] = useState(
    LOG_LEVEL_ARRAY[0] || 'info',
  );
  const [selectedErrorType, setSelectedErrorType] = useState<ErrorType>(
    ERROR_TYPES[0],
  );
  const [temporaryDeviceCode, setTemporaryDeviceCode] = useState<string | null>(
    null,
  );
  const [sessionID, setSessionID] = useState<string | null>(null);
  const [showPickerModal, setShowPickerModal] = useState(false);
  const [showErrorPickerModal, setShowErrorPickerModal] = useState(false);

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

  const handleGetSessionID = async () => {
    try {
      const id = await getSessionID();
      setSessionID(id);
      Clipboard.setString(id);
      Alert.alert('Copied!', 'Session ID copied to clipboard');
    } catch (error) {
      console.error('Failed to get session ID:', error);
      Alert.alert('Error', 'Failed to get session ID');
    }
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

      {hasAPIKeyConfigured && (
        <View style={styles.inlineContainer}>
          <Pressable
            style={({ pressed }) => [
              styles.button,
              pressed && styles.buttonActive,
            ]}
            onPress={handleGetSessionID}
          >
            <Text style={styles.buttonText}>Get Session ID</Text>
          </Pressable>
          {sessionID && (
            <Text selectable style={{ margin: 10 }}>
              {sessionID}
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

      <View style={styles.pickerContainer}>
        <Text style={styles.pickerLabel}>Log Level:</Text>
        {Platform.OS === 'ios' ? (
          <>
            <Pressable
              style={styles.pickerButton}
              onPress={() => setShowPickerModal(true)}
            >
              <Text style={styles.pickerButtonText}>
                {selectedLogLevel || 'Select level'}
              </Text>
            </Pressable>
            <Modal
              visible={showPickerModal}
              transparent
              animationType="slide"
              onRequestClose={() => setShowPickerModal(false)}
            >
              <View style={styles.modalContainer}>
                <View style={styles.modalContent}>
                  <View style={styles.modalHeader}>
                    <Pressable
                      onPress={() => setShowPickerModal(false)}
                      style={styles.modalButton}
                    >
                      <Text style={styles.modalButtonText}>Done</Text>
                    </Pressable>
                  </View>
                  <Picker
                    selectedValue={selectedLogLevel || LOG_LEVEL_ARRAY[0]}
                    onValueChange={(value: string) => {
                      setSelectedLogLevel(value);
                      setShowPickerModal(false);
                    }}
                    style={styles.picker}
                  >
                    {LOG_LEVEL_ARRAY.map((level) => (
                      <Picker.Item 
                        key={level} 
                        label={level} 
                        value={String(level)} 
                        style={{ textTransform: 'capitalize' }}
                      />
                    ))}
                  </Picker>
                </View>
              </View>
            </Modal>
          </>
        ) : (
          <View style={styles.pickerWrapper}>
            <Picker
              selectedValue={selectedLogLevel || LOG_LEVEL_ARRAY[0]}
              onValueChange={(value: string) => setSelectedLogLevel(value)}
              style={styles.pickerAndroid}
            >
              {LOG_LEVEL_ARRAY.map((level) => (
                <Picker.Item key={level} label={level} value={level} style={{ textTransform: 'capitalize' }} />
              ))}
            </Picker>
          </View>
        )}
      </View>
      <View style={styles.inlineContainer}>
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

      <View style={styles.pickerContainer}>
        <Text style={styles.pickerLabel}>Error Type:</Text>
        {Platform.OS === 'ios' ? (
          <>
            <Pressable
              style={styles.pickerButton}
              onPress={() => setShowErrorPickerModal(true)}
            >
              <Text style={styles.pickerButtonText}>
                {selectedErrorType || 'Select type'}
              </Text>
            </Pressable>
            <Modal
              visible={showErrorPickerModal}
              transparent
              animationType="slide"
              onRequestClose={() => setShowErrorPickerModal(false)}
            >
              <View style={styles.modalContainer}>
                <View style={styles.modalContent}>
                  <View style={styles.modalHeader}>
                    <Pressable
                      onPress={() => setShowErrorPickerModal(false)}
                      style={styles.modalButton}
                    >
                      <Text style={styles.modalButtonText}>Done</Text>
                    </Pressable>
                  </View>
                  <Picker
                    selectedValue={selectedErrorType || ERROR_TYPES[0]}
                    onValueChange={(value: string) => {
                      if (ERROR_TYPES.includes(value as ErrorType)) {
                        setSelectedErrorType(value as ErrorType);
                        setShowErrorPickerModal(false);
                      }
                    }}
                    style={styles.picker}
                  >
                    {ERROR_TYPES.map((type) => (
                      <Picker.Item 
                        key={type} 
                        label={type} 
                        value={type} 
                      />
                    ))}
                  </Picker>
                </View>
              </View>
            </Modal>
          </>
        ) : (
          <View style={styles.pickerWrapper}>
            <Picker
              selectedValue={selectedErrorType || ERROR_TYPES[0]}
              onValueChange={(value: string) => {
                if (ERROR_TYPES.includes(value as ErrorType)) {
                  setSelectedErrorType(value as ErrorType);
                }
              }}
              style={styles.pickerAndroid}
            >
              {ERROR_TYPES.map((type) => (
                <Picker.Item key={type} label={type} value={type} />
              ))}
            </Picker>
          </View>
        )}
      </View>
      <View style={styles.buttonRow}>
        <Pressable
          style={({ pressed }) => [styles.button, pressed && styles.buttonActive]}
          onPress={() => triggerGlobalJsError(selectedErrorType)}
        >
          <Text style={styles.buttonText}>Trigger {selectedErrorType} Error</Text>
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
  buttonRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    columnGap: 10,
    rowGap: 10,
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
  pickerContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 10,
    width: '100%',
  },
  pickerLabel: {
    fontSize: 16,
    marginRight: 10,
    minWidth: 80,
  },
  pickerWrapper: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 5,
    backgroundColor: '#fff',
    overflow: 'hidden',
    maxHeight: 50,
  },
  pickerButton: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 5,
    backgroundColor: '#fff',
    padding: 12,
    justifyContent: 'center',
  },
  pickerButtonText: {
    fontSize: 16,
    color: '#000',
  },
  picker: {
    height: 200,
    width: '100%',
    backgroundColor: '#fff',
  },
  pickerAndroid: {
    height: 50,
    width: '100%',
  },
  modalContainer: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
  },
  modalContent: {
    backgroundColor: '#fff',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingBottom: 20,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    padding: 15,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  modalButton: {
    paddingHorizontal: 15,
    paddingVertical: 5,
  },
  modalButtonText: {
    fontSize: 16,
    color: '#00A76F',
    fontWeight: '600',
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
