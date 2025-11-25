// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { logInternal } from './log';
import NativeBdReactNative from './NativeBdReactNative';
import { processJsError } from './jsErrorProcessor';

type ErrorUtilsType = {
  setGlobalHandler?: (handler: (error: unknown, isFatal?: boolean) => void) => void;
  getGlobalHandler?: () => ((error: unknown, isFatal?: boolean) => void) | undefined;
};


export function installGlobalErrorHandler(): void {
  const ErrorUtilsGlobal = getErrorUtils();
  
  if (
    !ErrorUtilsGlobal ||
    typeof ErrorUtilsGlobal.setGlobalHandler !== 'function' ||
    typeof ErrorUtilsGlobal.getGlobalHandler !== 'function'
  ) {
    return;
  }

  const previousHandler = ErrorUtilsGlobal.getGlobalHandler?.();

  ErrorUtilsGlobal.setGlobalHandler((error: unknown, isFatal?: boolean) => {
    // New Architecture handles fatal errors gracefully, so mark as non-fatal to avoid false positives
    const reactNativeIsFatal = Boolean(isFatal ?? false);
    const actualIsFatal = reactNativeIsFatal && !detectNewArchitecture();
    
    try {
      processJsError(error, (name, message, stack) => {
        NativeBdReactNative.reportJsError(
          name,
          message,
          stack,
          actualIsFatal,
          detectEngine(),
          getLibraryVersion()
        );
      });
    } catch (errorAtProcessing) {
      console.error('Error processing JS error:', errorAtProcessing);
    } finally {
      try {
        previousHandler?.(error, isFatal);
      } catch (previousHandlerError) {
        console.error('Previous error handler failed:', previousHandlerError);
      }
    }
  });
}

function getErrorUtils(): ErrorUtilsType | undefined {
  const g = global as unknown as { ErrorUtils?: ErrorUtilsType };
  return g.ErrorUtils;
}

function detectEngine(): string {
  const g = global as unknown as { HermesInternal?: unknown };
  return g.HermesInternal != null ? 'hermes' : 'jsc';
}

function getLibraryVersion(): string {
  try {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const packageJson = require('../package.json');
    return packageJson?.version ?? 'unknown';
  } catch {
    return 'unknown';
  }
}

/**
 * Detects if React Native is using the New Architecture (Fabric/TurboModules).
 * Defaults to false (old architecture) if detection fails (safer - assumes crashes).
 */
function detectNewArchitecture(): boolean {
  const g = global as unknown as {
    RN$Bridgeless?: boolean;
    __turboModuleProxy?: unknown;
    nativeFabricUIManager?: unknown;
  };

  return (
    g.RN$Bridgeless === true ||
    g.__turboModuleProxy != null ||
    g.nativeFabricUIManager != null
  );
}
