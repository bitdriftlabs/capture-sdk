import {
  DeviceEventEmitter,
  NativeEventEmitter,
  NativeModules,
  Platform,
} from 'react-native';
import { installGlobalErrorHandler } from './globalErrorHandler';
import {
  logInternal,
  serialize,
  type SerializableLogFields,
  Serializable,
  LogLevel,
} from './log';
import {
  InitOptions as NativeInitOptions,
  CrashReportingOptions as NativeCrashReportingOptions,
  SdkStatus as NativeSdkStatus,
  PreviousRunInfo as PreviousRunInfoModel,
  SessionStrategy,
} from './NativeBdReactNative';
import NativeBdReactNative from './NativeBdReactNative';
export { SessionStrategy } from './NativeBdReactNative';

// Cross-platform native<->JS event contract for issue report callbacks.
// If this value changes, update the matching constants in:
// - android/src/main/java/com/bdreactnative/BdReactNativeModule.kt
// - ios/BdReactNative.mm
// - ios/Logger.swift
const ISSUE_REPORT_EVENT = 'BdReactNative.onBeforeReportSend';
const START_RESULT_EVENT = 'BdReactNative.onStartResult';
const DEFAULT_API_URL = 'https://api.bitdrift.io';

export type IssueReport = {
  reportType: string;
  reason: string;
  details: string;
  sessionId: string;
  fields: Record<string, string>;
};

export type CrashReportingOptions = Omit<
  NativeCrashReportingOptions,
  'enableIssueCallbackBridge'
> & {
  UNSTABLE_onBeforeReportSend?: (report: IssueReport) => void;
};

export type SdkStatus = NativeSdkStatus;

export type StartResult = {
  success: boolean;
  error?: string;
};

export type InitOptions = Omit<NativeInitOptions, 'crashReporting'> & {
  UNSTABLE_webView?: NativeInitOptions['webView'];
  startResult?: (result: StartResult) => void;
  crashReporting?: CrashReportingOptions;
};

type EventSubscription = { remove: () => void };

let api_url = DEFAULT_API_URL;
let api_key = '';
let issueReportSubscription: EventSubscription | undefined;
let startResultSubscription: EventSubscription | undefined;

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

function createIssueReportEmitter() {
  if (Platform.OS === 'ios') {
    return new NativeEventEmitter(BdReactNativeModule);
  }
  return DeviceEventEmitter;
}

function resetIssueReportSubscription() {
  issueReportSubscription?.remove();
  issueReportSubscription = undefined;
}

function resetStartResultSubscription() {
  startResultSubscription?.remove();
  startResultSubscription = undefined;
}

function maybeRegisterIssueReportCallback(options?: InitOptions) {
  resetIssueReportSubscription();

  const callback = options?.crashReporting?.UNSTABLE_onBeforeReportSend;
  if (!callback) {
    return;
  }

  issueReportSubscription = createIssueReportEmitter().addListener(
    ISSUE_REPORT_EVENT,
    (report: IssueReport) => {
      callback(report);
    },
  );
}

function maybeRegisterStartResultCallback(options?: InitOptions) {
  resetStartResultSubscription();

  const callback = options?.startResult;
  if (!callback) {
    return;
  }

  startResultSubscription = createIssueReportEmitter().addListener(
    START_RESULT_EVENT,
    (result: StartResult) => {
      callback(result);
      resetStartResultSubscription();
    },
  );
}

function toNativeInitOptions(apiUrl: string, options?: InitOptions): NativeInitOptions {
  const enableIssueCallbackBridge = Boolean(
    options?.crashReporting?.UNSTABLE_onBeforeReportSend,
  );

  return {
    ...options,
    url: apiUrl,
    enableStartResultBridge: Boolean(options?.startResult),
    webView: options?.UNSTABLE_webView,
    crashReporting: options?.crashReporting
      ? {
          enableNativeFatalIssues: options.crashReporting.enableNativeFatalIssues,
          UNSTABLE_enableJsErrors: options.crashReporting.UNSTABLE_enableJsErrors,
          enableIssueCallbackBridge: enableIssueCallbackBridge,
        }
      : undefined,
  };
}

function buildDeviceCodeUrl(apiUrl: string): string {
  const deviceCodeUrl = new URL('/v1/device/code', `${apiUrl}/`);
  const hostWithPort =
    deviceCodeUrl.port.length > 0
      ? deviceCodeUrl.host
      : `${deviceCodeUrl.hostname}:443`;

  return `https://${hostWithPort}${deviceCodeUrl.pathname}${deviceCodeUrl.search}${deviceCodeUrl.hash}`;
}

export function init(
  key: string,
  sessionStrategy: SessionStrategy,
  options?: InitOptions,
): void {
  api_url = options?.url ?? DEFAULT_API_URL;
  api_key = key;

  // Install JS global error handler if enabled via config
  if (options?.crashReporting?.UNSTABLE_enableJsErrors === true) {
    installGlobalErrorHandler();
  }

  maybeRegisterIssueReportCallback(options);
  maybeRegisterStartResultCallback(options);

  const nativeOptions = toNativeInitOptions(api_url, options);
  return BdReactNative.init(key, sessionStrategy, nativeOptions);
}

export function trace(message: string, fields?: SerializableLogFields): void {
  return logInternal('trace', message, fields);
}

export function debug(message: string, fields?: SerializableLogFields): void {
  return logInternal('debug', message, fields);
}

export function info(message: string, fields?: SerializableLogFields): void {
  return logInternal('info', message, fields);
}

export function warn(message: string, fields?: SerializableLogFields): void {
  return logInternal('warn', message, fields);
}

export function error(
  message: string,
  error?: Error | null,
  fields?: SerializableLogFields,
): void {
  const combinedFields = error ? {
    ...fields,
    _error: error.name || 'Error',
    ...(error.message && { _error_details: error.message }),
  } : fields;
  
  return logInternal('error', message, combinedFields);
}

export function log(
  log_level: LogLevel,
  message: string,
  fields?: SerializableLogFields,
): void {
  return logInternal(log_level, message, fields);
}

/**
 * Adds a field to be included in all future log messages.
 *
 * Calling this multiple times for the same key will overwrite the previous value.
 *
 * @param key The key of the field to add.
 * @param value The value of the field to add.
 */
export function addField(key: string, value: Serializable): void {
  return NativeBdReactNative.addField(key, serialize(value));
}

/**
 * Removes a field that was previously added with {@link addField}.
 *
 * Calling this if the field has not been added will have no effect.
 *
 * @param key The key of the field to remove.
 */
export function removeField(key: string): void {
  return NativeBdReactNative.removeField(key);
}

export function isTracingActive(): boolean {
  return NativeBdReactNative.isTracingActive();
}

/**
 * Sets an entity identifier for backend correlation with device identifier.
 * The value is hashed for storage and the exact value is never persisted.
 *
 * @param entityId entity identifier
 */
export function setEntityId(entityId: string): void {
  return NativeBdReactNative.setEntityId(entityId);
}

export function clearEntityId(): void {
  return NativeBdReactNative.clearEntityId();
}

export async function getDeviceID(): Promise<string> {
  return NativeBdReactNative.getDeviceID();
}

export async function getSessionID(): Promise<string> {
  return NativeBdReactNative.getSessionID();
}

export async function getSessionURL(): Promise<string> {
  return NativeBdReactNative.getSessionURL();
}

export function getSdkStatus(): NativeSdkStatus {
  return NativeBdReactNative.getSdkStatus();
}

export type { PreviousRunInfo } from './NativeBdReactNative';

/**
 * Returns information about the previous app run, or `null` when not available.
 * Must be called after {@link init}.
 */
export function getPreviousRunInfo(): PreviousRunInfoModel {
  return NativeBdReactNative.getPreviousRunInfo();
}

/**
 * Logs a screen view event. This can be called to record that a screen was viewed.
 * @param screenName The name of the screen that was viewed.
 */
export function logScreenView(screenName: string): void {
  return NativeBdReactNative.logScreenView(screenName);
}

/**
 * Writes an app launch TTI log event. This event should be logged only once per Logger configuration.
 * Consecutive calls have no effect.
 *
 * @param {number} tti_ms - The time between a user's intent to launch the app and when the app becomes
 *                       interactive. Calls with a negative duration are ignored.
 */
export function logAppLaunchTTI(tti_ms: number): void {
  return NativeBdReactNative.logAppLaunchTTI(tti_ms);
}

/**
 * Records a feature flag exposure with a variant. Use this method to track when
 * a user is exposed to a specific feature flag variant in your application. The exposure
 * is recorded with a timestamp and tracked for the duration of the process.
 *
 * @param name the name of the flag being exposed
 * @param variant the variant of the flag being exposed
 */
export function setFeatureFlagExposure(name: string, variant: string | boolean): void {
  if (typeof variant === 'boolean') {
    return NativeBdReactNative.setFeatureFlagExposureBool(name, variant);
  } else {
    return NativeBdReactNative.setFeatureFlagExposureString(name, variant);
  }
}

/**
 * Generate a device code for the current device. Useful for streaming logs from a specific device using {@link https://docs.bitdrift.dev/cli/quickstart.html#log-tailing|bd tail}.
 * @returns The device code for the current device.
 */
export async function generateDeviceCode(): Promise<string> {
  try {
    const deviceId = await getDeviceID();
    const body = JSON.stringify({ device_id: deviceId });

    return fetch(buildDeviceCodeUrl(api_url), {
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
