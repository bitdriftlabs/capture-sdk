// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { Logger, SessionStrategy as CoreSessionStrategy } from '@bitdrift/core';
import { app } from 'electron';
import { platform as osPlatform, release as osRelease } from 'os';
import * as https from 'https';

import { type AutoExposeOptions, autoAddListener } from './autoAddListener';
import { LogFields } from './types';

let logger: Logger | null = null;
let apiUrl: string;
let apiKey: string;

export enum SessionStrategy {
  Activity = 'activity',
  Fixed = 'fixed',
}

const sessionStrategyMap = {
  [SessionStrategy.Activity]: CoreSessionStrategy.ActivityBased,
  [SessionStrategy.Fixed]: CoreSessionStrategy.Fixed,
};

/**
 * Optional parameters to configure the logger.
 */
export type InitOptions = {
  /** The URL to use to authenticate with the bitdrift API. Defaults to 'api.bitdrift.io'. */
  url?: string;
  /** The version of the application. Defaults to the version of the application based on the package.json file. */
  appVersion?: string;
  /** Whether to automatically register main event listeners. */
  autoAddMainListener?: boolean | AutoExposeOptions;
};

/**
 * Initializes the logger with the given key and session strategy.
 * @param key - The API key to use to authenticate with the bitdrift API.
 * @param sessionStrategy - The session strategy to use for logging.
 * @param options - Optional parameters to configure the logger.
 */
export const init = (
  key: string,
  sessionStrategy: SessionStrategy,
  options?: InitOptions,
) => {
  const urlOrDefault = options?.url ?? 'api.bitdrift.io';

  apiUrl = urlOrDefault;
  apiKey = key;

  logger = new Logger(
    key,
    `https://${urlOrDefault}`,
    sessionStrategyMap[sessionStrategy],
    app.getPath('userData') + '/bitdrift/',
    app.getName(),
    options?.appVersion ?? app.getVersion(),
    osPlatform(),
    osRelease(),
    app.getLocaleCountryCode(),
  );

  if (options?.autoAddMainListener) {
    autoAddListener(
      logger,
      typeof options.autoAddMainListener === 'boolean'
        ? {}
        : options.autoAddMainListener,
    );
  }

  return logger;
};

const loggerOrError = () => {
  if (!logger) throw new Error('Logger not initialized');

  return logger;
};

/**
 * Logs a message with severity level 4 (error).
 */
export const error = (message: string, fields?: LogFields) => {
  loggerOrError().log(4, message, fields || {});
};

/**
 * Logs a message with severity level 3 (warning).
 */
export const warn = (message: string, fields?: LogFields) => {
  loggerOrError().log(3, message, fields || {});
};

/**
 * Logs a message with severity level 2 (info).
 */
export const info = (message: string, fields?: LogFields) => {
  loggerOrError().log(2, message, fields || {});
};

/**
 * Logs a message with severity level 1 (debug).
 */
export const debug = (message: string, fields?: LogFields) => {
  loggerOrError().log(1, message, fields ?? {});
};

/**
 * Logs a message with severity level 0 (trace).
 */
export const trace = (message: string, fields?: LogFields) => {
  loggerOrError().log(0, message, fields || {});
};

/**
 * Gets the ID of the current session.
 * @returns The ID of the current session.
 */
export const getSessionID = () => logger?.sessionId;

/**
 * Gets the ID of the current device.
 * @returns The ID of the current device.
 */
export const getDeviceID = () => logger?.deviceId;

/**
 * Adds a field to all logs emitted by the logger from this point forward.
 * If a field with a given key has already been registered with the logger, its value is
 * replaced with the new one.
 *
 * @param field - The name of the field to add.
 * @param value - The value of the field to add.
 */
export const addField = (field: string, value: LogFields[keyof LogFields]) => {
  loggerOrError().addField(field, value);
};

/**
 * Removes a field with a given key. This operation has no effect if a field with the given key
 * is not registered with the logger.
 *
 * @param field - The name of the field to remove.
 */
export const removeField = (field: string) => {
  loggerOrError().removeField(field);
};

/**
 * Generates a temporary device code for the current device.
 * @returns The generated device code.
 */
export const generateDeviceCode = async (): Promise<string> => {
  const deviceId = logger?.deviceId;
  const postData = JSON.stringify({ device_id: deviceId });

  const options = {
    hostname: apiUrl,
    port: 443,
    path: '/v1/device/code',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(postData),
      'x-bitdrift-api-key': apiKey,
    },
  };

  return new Promise((resolve, reject) => {
    const req = https.request(options, (res) => {
      let data = '';

      // Set the encoding for the response data
      res.setEncoding('utf8');

      res.on('data', (chunk) => {
        data += chunk;
      });

      res.on('end', () => {
        try {
          const parsedData: { code: string } = JSON.parse(data);
          resolve(parsedData.code);
        } catch (e) {
          reject(e);
        }
      });
    });

    // Handle request errors
    req.on('error', (e) => {
      reject(e);
    });

    req.write(postData);
    req.end();
  });
};
