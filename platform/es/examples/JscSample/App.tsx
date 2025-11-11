/**
 * JSC Sample App
 * Simple app for testing with JavaScriptCore engine
 *
 * @format
 */

import React from 'react';
import {
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  Platform,
} from 'react-native';

function App(): JSX.Element {
  const throwError = () => {
    throw new Error('Test error from JSC Sample App');
  };

  // @ts-ignore - HermesInternal is not in types but exists at runtime
  const isHermes = typeof HermesInternal !== 'undefined';
  const jsEngine = isHermes ? 'Hermes' : 'JavaScriptCore (JSC)';

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>JSC Sample App</Text>
        
        <View style={styles.infoContainer}>
          <InfoRow label="JS Engine" value={jsEngine} />
          <InfoRow label="React Native" value="0.71.19" />
          <InfoRow label="Architecture" value="Old (Bridge)" />
          <InfoRow label="Platform" value={Platform.OS} />
          <InfoRow label="Release build" value={__DEV__ ? 'No' : 'Yes'} />
        </View>
        
        <TouchableOpacity
          style={styles.button}
          onPress={throwError}>
          <Text style={styles.buttonText}>Throw Error</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

function InfoRow({label, value}: {label: string; value: string}): JSX.Element {
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
  },
  buttonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
  },
});

export default App;
