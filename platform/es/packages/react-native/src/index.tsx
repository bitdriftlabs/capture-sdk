import { NativeModules, Platform } from 'react-native';
import { log, serialize, type SerializableLogFields, Serializable } from './log';
import { InitOptions, SessionStrategy } from './NativeBdReactNative';
import NativeBdReactNative from './NativeBdReactNative';
export { SessionStrategy } from './NativeBdReactNative';

let api_url: string;
let api_key: string;

const LINKING_ERROR =
  `The package '@bitdrift/react-native' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

// @ts-expect-error __turboModuleProxy is not defined
const isTurboModuleEnabled = global.__turboModuleProxy != null;

/* eslint-disable @typescript-eslint/no-var-requires */
const BdReactNativeModule = isTurboModuleEnabled
  ? require('./NativeBdReactNative').default
  : NativeModules.BdReactNative;
/* eslint-enable @typescript-eslint/no-var-requires */

const BdReactNative = BdReactNativeModule
  ? BdReactNativeModule
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      },
    );

export function init(
  key: string,
  sessionStrategy: SessionStrategy,
  options?: InitOptions,
): void {
  api_url = options?.url ?? 'api.bitdrift.io';
  api_key = key;

  return BdReactNative.init(key, sessionStrategy, options);
}

export function trace(message: string, fields?: SerializableLogFields): void {
  return log(0, message, fields);
}

export function debug(message: string, fields?: SerializableLogFields): void {
  return log(1, message, fields);
}

export function info(message: string, fields?: SerializableLogFields): void {
  return log(2, message, fields);
}

export function warn(message: string, fields?: SerializableLogFields): void {
  return log(3, message, fields);
}

export function error(message: string, fields?: SerializableLogFields): void {
  return log(4, message, fields);
}

/** Adds a field to be included in all future log messages. Calling this multiple times for the same key will overwrite the previous value. */
export function addField(key: string, value: Serializable): void {
  return NativeBdReactNative.addField(key, serialize(value));
}

export async function getDeviceID(): Promise<string> {
  return NativeBdReactNative.getDeviceID();
}

/**
 * Generate a device code for the current device. Useful for streaming logs from a specific device using {@link https://docs.bitdrift.dev/cli/quickstart.html#log-tailing|bd tail}.
 * @returns The device code for the current device.
 */
export async function generateDeviceCode(): Promise<string> {
  try {
    const deviceId = await getDeviceID();
    const body = JSON.stringify({ device_id: deviceId });

    return fetch(`${api_url}:443/v1/device/code`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-bitdrift-api-key': api_key,
      },
      body,
    })
      .then((res) => res.json())
      .then((data) => data.code)
      .catch((err) => err.message ?? 'Error generating device code');
  } catch {
    return 'Error generating device code';
  }
}
