import { NativeModules, Platform } from 'react-native';
import type { LogFields } from './NativeBdReactNative';

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
  options?: { url?: string; enableNetworkInstrumentation?: boolean },
): void {
  return BdReactNative.init(key, options);
}

export function trace(message: string, fields?: LogFields): void {
  return BdReactNative.log(0, message, fields);
}

export function debug(message: string, fields?: LogFields): void {
  return BdReactNative.log(1, message, fields);
}

export function info(message: string, fields?: LogFields): void {
  return BdReactNative.log(2, message, fields);
}

export function warn(message: string, fields?: LogFields): void {
  return BdReactNative.log(3, message, fields);
}

export function error(message: string, fields?: LogFields): void {
  return BdReactNative.log(4, message, fields);
}
