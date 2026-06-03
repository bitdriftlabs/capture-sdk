// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { withPlugins } from '@expo/config-plugins';
import { withAppBuildGradle, withSettingsGradle } from '@expo/config-plugins';
import type { ConfigPlugin } from '@expo/config-plugins';
import PluginProps from './config';

const CAPTURE_PLUGIN_ID = "id 'io.bitdrift.capture-plugin'";
const BITDRIFT_MAVEN_HOST = 'dl.bitdrift.io';

const withBitdriftAppBuildGradle: ConfigPlugin<PluginProps | void> = (
  config,
  props,
) => {
  return withAppBuildGradle(config, (config) => {
    const shouldEnableNetworkInstrumentation = props?.networkInstrumentation === true;
    const okHttpInstrumentationType =
      props?.okHttpInstrumentationType === 'OVERWRITE' ? 'OVERWRITE' : 'PROXY';

    if (
      shouldEnableNetworkInstrumentation &&
      !config.modResults.contents.includes(CAPTURE_PLUGIN_ID)
    ) {
      config.modResults.contents =
        `plugins {
    id 'io.bitdrift.capture-plugin' version '0.23.3'
}

` + config.modResults.contents;
    }

    if (
      shouldEnableNetworkInstrumentation &&
      !config.modResults.contents.includes('automaticOkHttpInstrumentation')
    ) {
      config.modResults.contents += `

bitdrift {
    instrumentation {
        automaticOkHttpInstrumentation = true
        okHttpInstrumentationType = ${okHttpInstrumentationType}
    }
}
`;
    }

    if (!config.modResults.contents.includes(BITDRIFT_MAVEN_HOST)) {
      // Define the bitdrift maven repository at the end. This is necessary to resolve the SDK dependency specified by the plugin.
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
    }

    return config;
  });
};

const withBitdriftSettingsGradle: ConfigPlugin<PluginProps | void> = (
  config,
  _props,
) => {
  // Add the bitdrift maven repository to the pluginManagement block to allow the plugin to resolve the SDK dependency. This is safe to do regardless of whether the network instrumentation is enabled or not.
  return withSettingsGradle(config, (config) => {
    if (!config.modResults.contents.includes(BITDRIFT_MAVEN_HOST)) {
      // There will be a pluginManagement block in the settings.gradle file already, so we need to insert ourselves
      // into the existing block.
      //
      // Note that we use a regex here because we need to resolve both the `io.bitdrift` group as well as the
      // `io.bitdrift.capture-plugin` group.
      config.modResults.contents = config.modResults.contents.replace(
        'pluginManagement {',
        `pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven {
            url 'https://dl.bitdrift.io/sdk/android-maven'
            content {
                includeGroupByRegex "io\\\\.bitdrift.*"
            }
        }
    } 
`,
      );
    }

    return config;
  });
};

const withAndroid: ConfigPlugin<PluginProps | void> = (config, props) => {
  return withPlugins(config, [
    [withBitdriftAppBuildGradle, props],
    [withBitdriftSettingsGradle, props],
  ]);
};

export default withAndroid;
