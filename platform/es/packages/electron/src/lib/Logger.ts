// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { Logger, SessionStrategy as CoreSessionStrategy } from '@bitdrift/core';
import { app } from 'electron';
import { platform as osPlatform, release as osRelease } from 'os';

import https = require('https');

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

export const init = (
  key: string,
  sessionStrategy: SessionStrategy,
  options?: { url?: string; appVersion?: string },
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

export const error = (message: string, fields?: LogFields) => {
  if (!logger) throw new Error('Logger not initialized');

  logger.log(4, message, fields || {});
};

export const warn = (message: string, fields?: LogFields) => {
  if (!logger) throw new Error('Logger not initialized');

  logger.log(3, message, fields || {});
};

export const info = (message: string, fields?: LogFields) => {
  if (!logger) throw new Error('Logger not initialized');

  logger.log(2, message, fields || {});
};

export const debug = (message: string, fields?: LogFields) => {
  if (!logger) throw new Error('Logger not initialized');

  logger.log(1, message, fields ?? {});
};

export const trace = (message: string, fields?: LogFields) => {
  if (!logger) throw new Error('Logger not initialized');

  logger.log(0, message, fields || {});
};

export const getSessionID = () => logger?.sessionId;

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
