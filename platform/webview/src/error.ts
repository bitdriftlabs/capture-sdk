// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { log, createMessage } from './bridge';
import type { ErrorMessage, PromiseRejectionMessage } from './types';

/**
 * Initialize unhandled error monitoring.
 * Captures uncaught JavaScript errors that bubble to the window.
 */
export function initErrorMonitoring(): void {
    window.addEventListener('error', (event: ErrorEvent) => {
        // Skip if this is a resource error (handled by resource-errors.ts)
        // Resource errors have event.target as an Element
        if (event.target && event.target !== window) {
            return;
        }

        const error = event.error;
        let stack: string | undefined;

        if (error instanceof Error) {
            stack = error.stack;
        }

        const message = createMessage<ErrorMessage>({
            type: 'error',
            name: error?.name ?? 'Error',
            message: event.message || 'Unknown error',
            stack,
            filename: event.filename || undefined,
            lineno: event.lineno || undefined,
            colno: event.colno || undefined,
        });
        log(message);
    });
}

/**
 * Initialize unhandled promise rejection monitoring.
 */
export function initPromiseRejectionMonitoring(): void {
    window.addEventListener('unhandledrejection', (event: PromiseRejectionEvent) => {
        let reason = 'Unknown rejection reason';
        let stack: string | undefined;

        if (event.reason instanceof Error) {
            reason = event.reason.message;
            stack = event.reason.stack;
        } else if (typeof event.reason === 'string') {
            reason = event.reason;
        } else if (event.reason !== null && event.reason !== undefined) {
            try {
                reason = JSON.stringify(event.reason);
            } catch {
                reason = String(event.reason);
            }
        }

        const message = createMessage<PromiseRejectionMessage>({
            type: 'promiseRejection',
            reason,
            stack: stack,
        });
        log(message);
    });
}
