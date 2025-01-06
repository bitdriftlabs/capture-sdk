import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

type Level = 'info' | 'success' | 'warning' | 'error';

const styles = StyleSheet.create({
  container: {
    borderLeftWidth: 4,
    padding: 10,
    marginVertical: 10,
    borderRadius: 4,
  },
  title: {
    fontWeight: 'bold',
    fontSize: 16,
    marginBottom: 5,
    marginHorizontal: 5,
  },
  message: {
    fontSize: 14,
  },
});

const infoStyles = StyleSheet.create({
  container: {
    backgroundColor: '#CAFDF5',
    borderLeftColor: '#00B8D9',
  },
  title: {
    color: '#006C9C',
  },
  message: {
    color: '#006C9C',
  },
});

const successStyles = StyleSheet.create({
  container: {
    backgroundColor: '#D3FCD2',
    borderLeftColor: '#22C55E',
  },
  title: {
    color: '#118D57',
    textTransform: 'capitalize',
  },
  message: {
    color: '#118D57',
  },
});

const warningStyles = StyleSheet.create({
  container: {
    backgroundColor: '#FFF5CC',
    borderLeftColor: '#FFAB00',
  },
  title: {
    color: '#B76E00',
  },
  message: {
    color: '#B76E00',
  },
});

const errorStyles = StyleSheet.create({
  container: {
    backgroundColor: '#FFE9D5',
    borderLeftColor: '#FF5630',
  },
  title: {
    color: '#B71D18',
  },
  message: {
    color: '#B71D18',
  },
});

const levelStyles = new Map<Level, typeof errorStyles>([
  ['info', infoStyles],
  ['success', successStyles],
  ['warning', warningStyles],
  ['error', errorStyles],
]);

const levelIcons = new Map<Level, string>([
  ['info', 'ℹ️'],
  ['success', '✅'],
  ['warning', '⚠️'],
  ['error', '❌'],
]);

type InfoBlockProps = {
  message: string;
} & (
  | {
      level?: Level;
      warning?: never;
      error?: never;
      info?: never;
      success?: never;
    }
  | {
      warning: true;
      level?: never;
      error?: never;
      info?: never;
      success?: never;
    }
  | {
      error: true;
      level?: never;
      warning?: never;
      info?: never;
      success?: never;
    }
  | {
      info: true;
      level?: never;
      warning?: never;
      error?: never;
      success?: never;
    }
  | {
      success: true;
      level?: never;
      warning?: never;
      error?: never;
      info?: never;
    }
);

export const InfoBlock = ({
  message,
  level,
  error,
  warning,
  success,
  info,
}: InfoBlockProps) => {
  const innerLevel =
    level ??
    (error
      ? 'error'
      : warning
        ? 'warning'
        : info
          ? 'info'
          : success
            ? 'success'
            : 'info');
  return (
    <View
      style={StyleSheet.compose(
        styles.container,
        levelStyles.get(innerLevel)?.container,
      )}
    >
      <Text
        style={StyleSheet.compose(
          styles.title,
          levelStyles.get(innerLevel)?.title,
        )}
      >
        {levelIcons.get(innerLevel)}{' '}
        {`${innerLevel.charAt(0).toUpperCase()}${innerLevel.slice(1)}`}
      </Text>
      <Text
        style={StyleSheet.compose(
          styles.message,
          levelStyles.get(innerLevel)?.message,
        )}
      >
        {message}
      </Text>
    </View>
  );
};
