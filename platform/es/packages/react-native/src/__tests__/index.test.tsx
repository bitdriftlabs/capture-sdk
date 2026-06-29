// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

type PlatformName = 'ios' | 'android';

type TestSetup = {
  sdk: typeof import('../index');
  nativeModule: {
    init: jest.Mock;
    getSdkStatus: jest.Mock;
  };
  nativeEventEmitterCtor: jest.Mock;
  nativeAddListener: jest.Mock;
  deviceAddListener: jest.Mock;
  removeListener: jest.Mock;
};

function loadSdk(platform: PlatformName): TestSetup {
  jest.resetModules();

  const removeListener = jest.fn();
  const nativeAddListener = jest.fn().mockReturnValue({ remove: removeListener });
  const deviceAddListener = jest.fn().mockReturnValue({ remove: removeListener });
  const nativeEventEmitterCtor = jest.fn().mockImplementation(() => ({
    addListener: nativeAddListener,
  }));

  const nativeModule = {
    init: jest.fn(),
    log: jest.fn(),
    addField: jest.fn(),
    removeField: jest.fn(),
    isTracingActive: jest.fn(),
    setEntityId: jest.fn(),
    clearEntityId: jest.fn(),
    getDeviceID: jest.fn(),
    getSessionID: jest.fn(),
    getSessionURL: jest.fn(),
    getSdkStatus: jest.fn(),
    logScreenView: jest.fn(),
    logAppLaunchTTI: jest.fn(),
    reportJsError: jest.fn(),
    setFeatureFlagExposureString: jest.fn(),
    setFeatureFlagExposureBool: jest.fn(),
    addListener: jest.fn(),
    removeListeners: jest.fn(),
  };

  jest.doMock('react-native', () => ({
    DeviceEventEmitter: {
      addListener: deviceAddListener,
    },
    NativeEventEmitter: nativeEventEmitterCtor,
    NativeModules: {
      BdReactNative: nativeModule,
    },
    Platform: {
      OS: platform,
      select: (config: Record<string, string>) => config[platform] ?? config.default,
    },
    TurboModuleRegistry: {
      getEnforcing: jest.fn(() => nativeModule),
    },
  }));

  jest.doMock('../globalErrorHandler', () => ({
    installGlobalErrorHandler: jest.fn(),
  }));

  const sdk = require('../index') as typeof import('../index');

  return {
    sdk,
    nativeModule,
    nativeEventEmitterCtor,
    nativeAddListener,
    deviceAddListener,
    removeListener,
  };
}

describe('init crash reporting callback wiring', () => {
  beforeEach(() => {
    (global as any).__turboModuleProxy = null;
  });

  test('enables native issue callback bridge for UNSTABLE_onBeforeReportSend', () => {
    const { sdk, nativeModule, nativeEventEmitterCtor, nativeAddListener } = loadSdk('ios');

    const callback = jest.fn();
    sdk.init('test-key', sdk.SessionStrategy.Fixed, {
      crashReporting: {
        UNSTABLE_onBeforeReportSend: callback,
      },
    });

    expect(nativeEventEmitterCtor).toHaveBeenCalledWith(nativeModule);
    expect(nativeAddListener).toHaveBeenCalledWith(
      'BdReactNative.onBeforeReportSend',
      expect.any(Function),
    );
    expect(nativeModule.init).toHaveBeenCalledWith(
      'test-key',
      sdk.SessionStrategy.Fixed,
      expect.objectContaining({
        crashReporting: expect.objectContaining({
          enableIssueCallbackBridge: true,
        }),
      }),
    );
  });

  test('disables native issue callback bridge when no callback is provided', () => {
    const { sdk, nativeModule, nativeAddListener, deviceAddListener } = loadSdk('ios');

    sdk.init('test-key', sdk.SessionStrategy.Fixed, {
      crashReporting: {
        UNSTABLE_enableJsErrors: true,
      },
    });

    expect(nativeAddListener).not.toHaveBeenCalled();
    expect(deviceAddListener).not.toHaveBeenCalled();
    expect(nativeModule.init).toHaveBeenCalledWith(
      'test-key',
      sdk.SessionStrategy.Fixed,
      expect.objectContaining({
        crashReporting: expect.objectContaining({
          enableIssueCallbackBridge: false,
          UNSTABLE_enableJsErrors: true,
        }),
      }),
    );
  });

  test('removes existing listener before re-registering a callback listener', () => {
    const { sdk, nativeAddListener } = loadSdk('ios');
    const firstRemove = jest.fn();
    const secondRemove = jest.fn();

    nativeAddListener
      .mockReturnValueOnce({ remove: firstRemove })
      .mockReturnValueOnce({ remove: secondRemove });

    sdk.init('test-key', sdk.SessionStrategy.Fixed, {
      crashReporting: {
        UNSTABLE_onBeforeReportSend: jest.fn(),
      },
    });

    sdk.init('test-key', sdk.SessionStrategy.Fixed, {
      crashReporting: {
        UNSTABLE_onBeforeReportSend: jest.fn(),
      },
    });

    expect(firstRemove).toHaveBeenCalledTimes(1);
    expect(nativeAddListener).toHaveBeenCalledTimes(2);
  });

  test('removes existing listener and does not re-register when callback is removed', () => {
    const { sdk, nativeAddListener } = loadSdk('ios');
    const firstRemove = jest.fn();

    nativeAddListener.mockReturnValueOnce({ remove: firstRemove });

    sdk.init('test-key', sdk.SessionStrategy.Fixed, {
      crashReporting: {
        UNSTABLE_onBeforeReportSend: jest.fn(),
      },
    });

    sdk.init('test-key', sdk.SessionStrategy.Fixed, {
      crashReporting: {
        UNSTABLE_enableJsErrors: true,
      },
    });

    expect(firstRemove).toHaveBeenCalledTimes(1);
    expect(nativeAddListener).toHaveBeenCalledTimes(1);
  });

  test('routes emitted payload to the registered callback', () => {
    const { sdk, nativeAddListener } = loadSdk('ios');
    const callback = jest.fn();

    sdk.init('test-key', sdk.SessionStrategy.Fixed, {
      crashReporting: {
        UNSTABLE_onBeforeReportSend: callback,
      },
    });

    const listener = nativeAddListener.mock.calls[0][1] as (report: unknown) => void;
    const report = {
      reportType: 'Crash',
      reason: 'Error',
      details: 'Something failed',
      sessionId: 'session-id',
      fields: { env: 'test' },
    };

    listener(report);

    expect(callback).toHaveBeenCalledWith(report);
  });

  test('uses DeviceEventEmitter on Android when callback is provided', () => {
    const { sdk, nativeEventEmitterCtor, nativeAddListener, deviceAddListener } = loadSdk('android');

    sdk.init('test-key', sdk.SessionStrategy.Fixed, {
      crashReporting: {
        UNSTABLE_onBeforeReportSend: jest.fn(),
      },
    });

    expect(nativeEventEmitterCtor).not.toHaveBeenCalled();
    expect(nativeAddListener).not.toHaveBeenCalled();
    expect(deviceAddListener).toHaveBeenCalledWith(
      'BdReactNative.onBeforeReportSend',
      expect.any(Function),
    );
  });

  test('enables native start result bridge when callback is provided', () => {
    const { sdk, nativeModule } = loadSdk('ios');

    sdk.init('test-key', sdk.SessionStrategy.Fixed, {
      startResult: jest.fn(),
    });

    expect(nativeModule.init).toHaveBeenCalledWith(
      'test-key',
      sdk.SessionStrategy.Fixed,
      expect.objectContaining({
        enableStartResultBridge: true,
      }),
    );
  });

  test('routes emitted start result payload to the registered callback once', () => {
    const { sdk, nativeAddListener, removeListener } = loadSdk('ios');
    const callback = jest.fn();

    sdk.init('test-key', sdk.SessionStrategy.Fixed, {
      startResult: callback,
    });

    const listener = nativeAddListener.mock.calls[0][1] as (result: unknown) => void;
    const result = { success: false, error: 'failed' };

    listener(result);

    expect(callback).toHaveBeenCalledWith(result);
    expect(removeListener).toHaveBeenCalled();
  });
});

describe('generateDeviceCode URL handling', () => {
  const fetchMock = jest.fn();

  beforeEach(() => {
    (global as unknown as { fetch: typeof fetchMock }).fetch = fetchMock;
    fetchMock.mockResolvedValue({
      json: async () => ({ code: 'abc123' }),
    });
  });

  afterEach(() => {
    fetchMock.mockReset();
  });

  test('uses https default API URL when url is not provided', async () => {
    const { sdk, nativeModule } = loadSdk('ios');
    nativeModule.getDeviceID.mockResolvedValue('device-id');

    sdk.init('test-key', sdk.SessionStrategy.Fixed);
    await sdk.generateDeviceCode();

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.bitdrift.io:443/v1/device/code',
      expect.objectContaining({
        method: 'POST',
      }),
    );
  });

  test('uses explicit https API URL override', async () => {
    const { sdk, nativeModule } = loadSdk('ios');
    nativeModule.getDeviceID.mockResolvedValue('device-id');

    sdk.init('test-key', sdk.SessionStrategy.Fixed, {
      url: 'https://api.bitdrift.dev',
    });
    await sdk.generateDeviceCode();

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.bitdrift.dev:443/v1/device/code',
      expect.objectContaining({
        method: 'POST',
      }),
    );
  });

  test('trims trailing slash from API URL', async () => {
    const { sdk, nativeModule } = loadSdk('ios');
    nativeModule.getDeviceID.mockResolvedValue('device-id');

    sdk.init('test-key', sdk.SessionStrategy.Fixed, {
      url: 'https://api.bitdrift.dev/',
    });
    await sdk.generateDeviceCode();

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.bitdrift.dev:443/v1/device/code',
      expect.objectContaining({
        method: 'POST',
      }),
    );
  });

  test('preserves explicit https port', async () => {
    const { sdk, nativeModule } = loadSdk('ios');
    nativeModule.getDeviceID.mockResolvedValue('device-id');

    sdk.init('test-key', sdk.SessionStrategy.Fixed, {
      url: 'https://localhost:8443',
    });
    await sdk.generateDeviceCode();

    expect(fetchMock).toHaveBeenCalledWith(
      'https://localhost:8443/v1/device/code',
      expect.objectContaining({
        method: 'POST',
      }),
    );
  });
});

describe('getSdkStatus', () => {
  test('returns native sdk status synchronously', () => {
    const { sdk, nativeModule } = loadSdk('ios');
    nativeModule.getSdkStatus.mockReturnValue({
      initializationState: 'running',
      lastHandshakeTimeMs: 123,
      lastConfigDeliveryTimeMs: 456,
    });

    expect(sdk.getSdkStatus()).toEqual({
      initializationState: 'running',
      lastHandshakeTimeMs: 123,
      lastConfigDeliveryTimeMs: 456,
    });
  });
});

describe('setEntityId', () => {
  test('forwards the entity id to the native module', () => {
    const { sdk, nativeModule } = loadSdk('ios');

    sdk.setEntityId('expo-sample-entity');

    expect(nativeModule.setEntityId).toHaveBeenCalledWith('expo-sample-entity');
  });
});

describe('isTracingActive', () => {
  test('returns the native tracing state synchronously', () => {
    const { sdk, nativeModule } = loadSdk('ios');
    nativeModule.isTracingActive.mockReturnValue(true);

    expect(sdk.isTracingActive()).toBe(true);
  });
});

describe('clearEntityId', () => {
  test('forwards to the native module', () => {
    const { sdk, nativeModule } = loadSdk('ios');

    sdk.clearEntityId();

    expect(nativeModule.clearEntityId).toHaveBeenCalled();
  });
});
