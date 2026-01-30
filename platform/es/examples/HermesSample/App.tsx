/**
 * Hermes Sample App
 * Simple app for testing with Hermes engine
 *
 * @format
 */

import './lib/bitdrift';

import React, { useState, useEffect } from 'react';
import {
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  Platform,
  Clipboard,
  Alert,
} from 'react-native';
import { getSessionURL } from '@bitdrift/react-native';

function App(): JSX.Element {
  const [sessionURL, setSessionURL] = useState<string | null>(null);

  useEffect(() => {
    getSessionURL()
      .then(setSessionURL)
      .catch(() => undefined);
  }, []);

  const copySessionURL = () => {
    if (sessionURL) {
      Clipboard.setString(sessionURL);
      Alert.alert('Copied', 'Session URL copied to clipboard');
    } else {
      Alert.alert('Error', 'Session URL not available');
    }
  };

  const triggerNonFatalError = () => {
    const err = new Error('Non-fatal error via ErrorUtils');
    const g = globalThis as unknown as {
      ErrorUtils?: { reportError?: (e: Error) => void };
    };
    g.ErrorUtils?.reportError?.(err);
  };

  const hermesInternal = (globalThis as { HermesInternal?: unknown })
    .HermesInternal;
  const isHermes = typeof hermesInternal !== 'undefined';
  const jsEngine = isHermes ? 'Hermes' : 'JavaScriptCore (JSC)';

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>Hermes Sample App</Text>

        <View style={styles.infoContainer}>
          <InfoRow label="JS Engine" value={jsEngine} />
          <InfoRow label="React Native" value="0.81.0" />
          <InfoRow label="Architecture" value="New" />
          <InfoRow label="Platform" value={Platform.OS} />
          <InfoRow label="Release build" value={__DEV__ ? 'No' : 'Yes'} />
        </View>

        <TouchableOpacity style={styles.copyButton} onPress={copySessionURL}>
          <Text style={styles.buttonText}>Copy Session URL</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.nonFatalButton}
          onPress={triggerNonFatalError}
        >
          <Text style={styles.buttonText}>Non-Fatal Error</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

function InfoRow({
  label,
  value,
}: {
  label: string;
  value: string;
}): JSX.Element {
  return (
    <View style={styles.infoRow}>
      <Text style={styles.infoLabel}>{label}:</Text>
      <Text style={styles.infoValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  title: {
    fontSize: 32,
    fontWeight: '700',
    marginBottom: 30,
    color: '#000',
  },
  infoContainer: {
    backgroundColor: '#f5f5f5',
    padding: 20,
    borderRadius: 8,
    marginBottom: 40,
    width: '100%',
    maxWidth: 400,
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  infoLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
  },
  infoValue: {
    fontSize: 16,
    color: '#666',
  },
  button: {
    backgroundColor: '#e74c3c',
    paddingHorizontal: 32,
    paddingVertical: 16,
    borderRadius: 8,
    marginTop: 16,
  },
  nonFatalButton: {
    backgroundColor: '#f39c12',
    paddingHorizontal: 32,
    paddingVertical: 16,
    borderRadius: 8,
    marginTop: 16,
  },
  copyButton: {
    backgroundColor: '#3498db',
    paddingHorizontal: 32,
    paddingVertical: 16,
    borderRadius: 8,
    marginTop: 16,
  },
  buttonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
  },
});

export default App;
