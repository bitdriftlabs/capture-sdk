// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { withAppBuildGradle } from '@expo/config-plugins';    
import { ExpoConfig } from '@expo/config-types';

module.exports = function withAndroidStrategiesPlugin(config: ExpoConfig) {
    return withAppBuildGradle(config, (config) => {
        config.modResults.contents += `
repositories {
  maven {
    url 'https://dl.bitdrift.io/sdk/android-maven'
    content {
      includeGroup 'io.bitdrift'
    }
  }
}
        `;
        return config;
      });
}
