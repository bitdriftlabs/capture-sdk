// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { log, createMessage, pristine, isAnyBridgeMessage } from './bridge';
import { safeCall, safeStringify, truncate, MAX_STRING_LENGTH } from './safe-call';

const LEVELS = ['log', 'warn', 'error', 'info', 'debug'] as const;

/** Maximum number of console args to capture beyond the first. */
const MAX_EXTRA_ARGS = 10;

/**
 * Initialize console log capture.
 * Intercepts console.log, warn, error, info, debug and sends to native.
 */
export const initConsoleCapture = (): void => {
    safeCall(() => {
        for (const level of LEVELS) {
            // Ensure pristine.console is initialized
            pristine.console[level] = pristine.console[level] ?? console[level];

            console[level] = (...args: unknown[]) => {
                // Call original console method first - this must succeed even if our logging fails
                safeCall(() => pristine.console[level]?.apply(console, args));

                // Wrap our telemetry in safeCall to never crash the client
                safeCall(() => {
                    // Avoid capturing our own bridge messages
                    if (isAnyBridgeMessage(args[0])) return;

                    // Convert args to strings
                    const messageStr = stringifyArg(args[0]);
                    const additionalArgs =
                        args.length > 1
                            ? (args
                                  .slice(1, 1 + MAX_EXTRA_ARGS)
                                  .map(stringifyArg)
                                  .filter(Boolean) as string[])
                            : undefined;

                    const message = createMessage({
                        type: 'console',
                        level,
                        message: messageStr,
                        args: additionalArgs,
                    });
                    log(message);
                });
            };
        }
    });
};

/**
 * Convert an argument to a string representation, bounded in size.
 */
const stringifyArg = (arg: unknown): string => {
    if (arg === null) return 'null';
    if (arg === undefined) return 'undefined';
    if (typeof arg === 'string') return truncate(arg, MAX_STRING_LENGTH);
    if (typeof arg === 'number' || typeof arg === 'boolean') return String(arg);
    if (arg instanceof Error) return truncate(`${arg.name}: ${arg.message}`, MAX_STRING_LENGTH);

    return safeStringify(arg);
};
