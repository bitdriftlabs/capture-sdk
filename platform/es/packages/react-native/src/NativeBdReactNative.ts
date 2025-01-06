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
export type InitOptions = {
  url?: string;
  enableNetworkInstrumentation?: boolean;
};

export interface Spec extends TurboModule {
  init(
    key: string,
    sessionStrategy: SessionStrategy,
    options?: InitOptions,
  ): void;

  log(level: number, message: string, fields?: LogFields): void;

  addField(key: string, value: string): void;

  getDeviceID(): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('BdReactNative');
