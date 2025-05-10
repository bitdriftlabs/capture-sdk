// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import type { LogFields } from './NativeBdReactNative';
import stringify from 'fast-json-stringify';
import NativeBdReactNative from './NativeBdReactNative';

export type Serializable =
  | string
  | number
  | boolean
  | null
  | undefined
  | Serializable[]
  | { [key: string]: Serializable };

export type SerializableLogFields = { [key: string]: Serializable };

const LogLevels = Object.freeze({
  trace: 0,
  debug: 1,
  info: 2,
  warn: 3,
  error: 4,
});

export type LogLevel = keyof typeof LogLevels;

export const logInternal = (
  level: LogLevel,
  message: string,
  fields?: SerializableLogFields,
): void =>
  NativeBdReactNative.log(
    LogLevels[level],
    message,
    fields
      ? Object.entries(fields).reduce<LogFields>(
          (acc, [key, value]) => ({
            ...acc,
            [key]: serialize(value),
          }),
          {},
        )
      : undefined,
  );

export const serialize = (value: Serializable): string =>
  typeof value === 'string' ? value : stringify({})(value);
