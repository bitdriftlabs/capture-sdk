// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/**
 * Processes JavaScript errors by attempting symbolication in debug builds
 * and formatting stack traces for backend symbolication in release builds.
 */
export function processJsError(
  error: unknown,
  onProcessed: (name: string, message: string, stack: string) => void,
): void {
  if (!(error instanceof Error)) {
    onProcessed('Error', String(error), '');
    return;
  }

  const name = error.name || 'Error';
  const message = error.message || '';
  const rawStack = error.stack || '';

  if (!rawStack) {
    return;
  }

  if (__DEV__) {
    const symbolicationAttempted = attemptDebugSymbolication(rawStack, name, message, onProcessed);
    if (!symbolicationAttempted) {
      const formattedStack = formatRawStack(rawStack);
      onProcessed(name, message, formattedStack);
    }
  } else {
    const formattedStack = formatRawStack(rawStack);
    onProcessed(name, message, formattedStack);
  }
}

function attemptDebugSymbolication(
  rawStack: string,
  name: string,
  message: string,
  onProcessed: (name: string, message: string, stack: string) => void,
): boolean {
  try {
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore - React Native internal API (may not exist in all versions)
    const parseErrorStackModule = require('react-native/Libraries/Core/Devtools/parseErrorStack');
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore - React Native internal API (may not exist in all versions)
    const symbolicateStackTraceModule = require('react-native/Libraries/Core/Devtools/symbolicateStackTrace');

    // Handle React Native version differences: 0.79+ uses default export, 0.78- uses direct function
    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
    const parseErrorStack =
      parseErrorStackModule.default && typeof parseErrorStackModule.default === 'function'
        ? parseErrorStackModule.default
        : parseErrorStackModule;

    // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
    const symbolicateStackTrace =
      symbolicateStackTraceModule.default && typeof symbolicateStackTraceModule.default === 'function'
        ? symbolicateStackTraceModule.default
        : symbolicateStackTraceModule;

    if (typeof parseErrorStack === 'function' && typeof symbolicateStackTrace === 'function') {
      const parsedFrames = parseErrorStack(rawStack);

      if (parsedFrames && parsedFrames.length > 0) {
        symbolicateStackTrace(parsedFrames)
          .then((symbolicated: unknown) => {
            const symbolicatedFrames = Array.isArray(symbolicated)
              ? symbolicated
              : ((symbolicated as { stack?: unknown[] })?.stack || []);

            if (symbolicatedFrames && symbolicatedFrames.length > 0) {
              let symbolicatedStack = '';
              // eslint-disable @typescript-eslint/no-unsafe-member-access
              for (const frame of symbolicatedFrames) {
                const frameObj = frame as { file?: string; lineNumber?: number | null; column?: number | null; methodName?: string };
                const location = frameObj.file || 'unknown';
                const line = frameObj.lineNumber != null ? `:${frameObj.lineNumber}` : '';
                const col = frameObj.column != null ? `:${frameObj.column}` : '';
                const methodName = frameObj.methodName || 'unknown';
                symbolicatedStack += `${methodName} at ${location}${line}${col}\n`;
              }
              onProcessed(name, message, symbolicatedStack);
              } else {
                const formattedStack = formatRawStack(rawStack);
                onProcessed(name, message, formattedStack);
              }
            })
            .catch(() => {
              const formattedStack = formatRawStack(rawStack);
              onProcessed(name, message, formattedStack);
            });
        return true;
      }
    }
  } catch {
    // Fall through to format raw stack
  }

  return false;
}

/**
 * Formats raw JavaScript stack trace for backend symbolication in release builds.
 * Converts stack frames to format: "at <functionName> (address at <bundle>:<line>:<column>)"
 */
export function formatRawStack(rawStack: string): string {
  const lines = rawStack.split('\n');
  const formattedLines: string[] = [];

  for (const line of lines) {
    if (!line.trim() || !line.includes('at ')) {
      continue;
    }

    const match = line.match(/at\s+(.+?)\s+\((.+?):(\d+):(\d+)\)/);

    if (match) {
      const functionName = match[1].trim();
      let filePath = match[2].trim();
      const lineNum = match[3];
      const column = match[4];

      filePath = filePath.replace(/^(address\s+at\s+)+/gi, '').trim();

      let bundleName = filePath.includes('/') ? filePath.split('/').pop() || filePath : filePath;
      bundleName = bundleName.replace(/^(address\s+at\s+)+/gi, '').trim();

      const location = bundleName.startsWith('address at ')
        ? bundleName
        : `address at ${bundleName}`;

      formattedLines.push(`at ${functionName} (${location}:${lineNum}:${column})`);
    } else {
      const simpleMatch = line.match(/at\s+(.+)/);
      if (simpleMatch) {
        formattedLines.push(`at ${simpleMatch[1]}`);
      }
    }
  }

  return formattedLines.join('\n');
}
