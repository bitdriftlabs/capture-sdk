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

type LogParams = Parameters<typeof Logger.prototype.log>;
type LogFields = LogParams[2];

let logger: Logger | null = null;
let api_url: string;
let api_key: string;

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
 * @param url - The URL to use to authenticate with the bitdrift API. Defaults to 'api.bitdrift.io'.
 * @param appVersion - The version of the application. Defaults to the version of the application based on the package.json file.
 */
export type InitOptions = {
  url?: string;
  appVersion?: string;
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
  const url_or_default = options?.url ?? 'api.bitdrift.io';

  api_url = url_or_default;
  api_key = key;

  logger = new Logger(
    key,
    `https://${url_or_default}`,
    sessionStrategyMap[sessionStrategy],
    app.getPath('userData') + '/bitdrift/',
    app.getName(),
    options?.appVersion ?? app.getVersion(),
    osPlatform(),
    osRelease(),
    app.getLocaleCountryCode(),
  );
  return logger;
};

/**
 * Logs a message with severity level 4 (error).
 */
export const error = (message: string, fields?: LogFields) => {
  if (!logger) throw new Error('Logger not initialized');

  logger.log(4, message, fields || {});
};

/**
 * Logs a message with severity level 3 (warning).
 */
export const warn = (message: string, fields?: LogFields) => {
  if (!logger) throw new Error('Logger not initialized');

  logger.log(3, message, fields || {});
};

/**
 * Logs a message with severity level 2 (info).
 */
export const info = (message: string, fields?: LogFields) => {
  if (!logger) throw new Error('Logger not initialized');

  logger.log(2, message, fields || {});
};

/**
 * Logs a message with severity level 1 (debug).
 */
export const debug = (message: string, fields?: LogFields) => {
  if (!logger) throw new Error('Logger not initialized');

  logger.log(1, message, fields ?? {});
};

/**
 * Logs a message with severity level 0 (trace).
 */
export const trace = (message: string, fields?: LogFields) => {
  if (!logger) throw new Error('Logger not initialized');

  logger.log(0, message, fields || {});
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
 * Generates a temporary device code for the current device.
 * @returns The generated device code.
 */
export const generateDeviceCode = async (): Promise<string> => {
  const deviceId = logger?.deviceId;
  const postData = JSON.stringify({ device_id: deviceId });

  const options = {
    hostname: api_url,
    port: 443,
    path: '/v1/device/code',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(postData),
      'x-bitdrift-api-key': api_key,
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
