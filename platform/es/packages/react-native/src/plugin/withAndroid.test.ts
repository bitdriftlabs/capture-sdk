// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import fs from 'node:fs';
import path from 'node:path';
import withAndroid from './withAndroid';

const { captureSdkVersion } = require('../../package.json') as {
  captureSdkVersion: string;
};

jest.mock('@expo/config-plugins', () => ({
  withPlugins: jest.fn((config, plugins) =>
    plugins.reduce((nextConfig: unknown, [plugin, props]: [(config: unknown, props?: unknown) => unknown, unknown]) => {
      return plugin(nextConfig, props);
    }, config),
  ),
  withAppBuildGradle: jest.fn((config, action) => action(config)),
  withSettingsGradle: jest.fn((config, action) => action(config)),
}));

type ConfigWithContents = {
  modResults: {
    contents: string;
  };
};

function applyAppBuildGradlePlugin(
  contents: string,
  props?: {
    networkInstrumentation?: boolean;
    okHttpInstrumentationType?: 'PROXY' | 'OVERWRITE';
    UNSTABLE_webViewInstrumentation?: boolean;
  },
): string {
  const config = {
    modResults: {
      contents,
    },
  } as ConfigWithContents;

  return (withAndroid(config, props) as ConfigWithContents).modResults.contents;
}

function readFixture(name: string): string {
  return fs
    .readFileSync(path.join(__dirname, '__fixtures__', 'withAndroid', name), 'utf8')
    .replaceAll('__CAPTURE_PLUGIN_VERSION__', captureSdkVersion);
}

describe('withAndroid', () => {
  test('appends a new bitdrift block for OkHttp instrumentation', () => {
    const contents = `plugins {
    id 'com.android.application'
}
`;

    expect(
      applyAppBuildGradlePlugin(contents, {
        networkInstrumentation: true,
      }),
    ).toBe(readFixture('appends-okhttp-block.gradle'));
  });

  test('injects OkHttp instrumentation into an existing bitdrift block', () => {
    const contents = `plugins {
    id 'com.android.application'
}

bitdrift {
    instrumentation {
        automaticWebViewInstrumentation = true
    }
}
`;

    expect(
      applyAppBuildGradlePlugin(contents, {
        networkInstrumentation: true,
        okHttpInstrumentationType: 'OVERWRITE',
      }),
    ).toBe(readFixture('injects-okhttp-into-existing-block.gradle'));
  });

  test('adds only missing okHttpInstrumentationType when automatic flag already exists', () => {
    const contents = `plugins {
    id 'com.android.application'
}

bitdrift {
    instrumentation {
        automaticOkHttpInstrumentation = true
    }
}
`;

    expect(
      applyAppBuildGradlePlugin(contents, {
        networkInstrumentation: true,
        okHttpInstrumentationType: 'OVERWRITE',
      }),
    ).toBe(readFixture('adds-missing-okhttp-type.gradle'));
  });

  test('injects WebView instrumentation into an existing bitdrift block', () => {
    const contents = `plugins {
    id 'com.android.application'
}

bitdrift {
    instrumentation {
        automaticOkHttpInstrumentation = true
        okHttpInstrumentationType = PROXY
    }
}
`;

    expect(
      applyAppBuildGradlePlugin(contents, {
        UNSTABLE_webViewInstrumentation: true,
      }),
    ).toBe(readFixture('injects-webview-into-existing-block.gradle'));
  });

  test('appends a new bitdrift block for WebView instrumentation', () => {
    const contents = `plugins {
    id 'com.android.application'
}
`;

    expect(
      applyAppBuildGradlePlugin(contents, {
        UNSTABLE_webViewInstrumentation: true,
      }),
    ).toBe(readFixture('appends-webview-block.gradle'));
  });
});
