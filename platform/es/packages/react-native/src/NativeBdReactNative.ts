// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export type LogFields = { [key: string]: string };
export enum SessionStrategy {
  Activity = 'activity',
  Fixed = 'fixed',
}
export type CrashReportingOptions = {
  /** Enable native fatal issues reporting (e.g. JVM crashes, JNI crashes, etc). Defaults to true. */
  enableNativeFatalIssues?: boolean;
  /** Enable JS global error handler via ErrorUtils.setGlobalHandler. This is an experimental feature at the moment and may change in future releases. */
  UNSTABLE_enableJsErrors?: boolean;
};

export type InitOptions = {
  url?: string;
  enableNetworkInstrumentation?: boolean;
  crashReporting?: CrashReportingOptions;
};

export interface Spec extends TurboModule {
  init(
    key: string,
    sessionStrategy: SessionStrategy,
    options?: InitOptions,
  ): void;

  log(level: number, message: string, fields?: LogFields): void;

  addField(key: string, value: string): void;

  removeField(key: string): void;

  getDeviceID(): Promise<string>;

  getSessionID(): Promise<string>;

  getSessionURL(): Promise<string>;

  logScreenView(screenName: string): void;

  logAppLaunchTTI(tti_ms: number): void;

  reportJsError(
    errorName: string,
    message: string,
    stack: string,
    isFatal: boolean,
    engine: string,
    libraryVersion: string,
  ): void;

  setFeatureFlagExposureString(name: string, variant: string): void;

  setFeatureFlagExposureBool(name: string, variant: boolean): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('BdReactNative');
