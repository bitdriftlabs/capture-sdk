// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import type { AnyBridgeMessage, BridgeMessage, CustomLogMessage } from './types';

export const pristine = {
    console: {
        log: console.log,
        warn: console.warn,
        error: console.error,
        info: console.info,
        debug: console.debug,
    },
};

type Platform = 'ios' | 'android' | 'unknown';

const detectPlatform = (): Platform => {
    if (window.webkit?.messageHandlers?.BitdriftLogger) {
        return 'ios';
    }
    if (window.BitdriftLogger) {
        return 'android';
    }
    return 'unknown';
};

const sendToNative = (message: AnyBridgeMessage): void => {
    const platform = detectPlatform();
    const serialized = JSON.stringify(message);

    switch (platform) {
        case 'ios':
            window.webkit?.messageHandlers?.BitdriftLogger?.postMessage(message);
            break;
        case 'android':
            window.BitdriftLogger?.log(serialized);
            break;
        case 'unknown':
            // In development/testing, log to console
            if (typeof console !== 'undefined') {
                console.debug('[Bitdrift WebView]', message);
            }
            break;
    }
};

/**
 * Initialize the global bitdrift object
 */
export const initBridge = (): void => {
    // Avoid re-initialization
    if (window.bitdrift?.log) {
        return;
    }

    window.bitdrift = {
        config: window.bitdrift?.config,
        log: (
            ...args:
                | [AnyBridgeMessage]
                | [CustomLogMessage['level'], CustomLogMessage['message'], CustomLogMessage['fields']]
        ): void => {
            if (args.length !== 1 && args.length !== 3) {
                throw new Error('Invalid arguments to bitdrift.log. Expected 1 or 3 arguments.');
            }

            let message: AnyBridgeMessage;
            if (args.length === 1) {
                message = args[0];
            } else {
                const [level, msg, fields] = args;
                message = createMessage<CustomLogMessage>({
                    type: 'customLog',
                    level,
                    message: msg,
                    fields,
                });
            }
            sendToNative(message);
        },
    };
};

/**
 * Send a message through the bridge
 */
export const log = (message: AnyBridgeMessage): void => {
    if (window.bitdrift) {
        window.bitdrift.log?.(message);
    } else {
        sendToNative(message);
    }
};

/**
 * Helper to create a timestamped message
 */
export const createMessage = <T extends AnyBridgeMessage>(partial: Omit<T, 'v' | 'timestamp' | 'tag'>): T => {
    return {
        tag: 'bitdrift-webview-sdk',
        v: 1,
        timestamp: Date.now(),
        ...partial,
    } as T;
};

export const isAnyBridgeMessage = (obj: unknown): obj is AnyBridgeMessage => {
    return (
        typeof obj === 'object' &&
        obj !== null &&
        'type' in obj &&
        typeof (obj as BridgeMessage).type === 'string' &&
        'v' in obj &&
        typeof (obj as BridgeMessage).v === 'number' &&
        'tag' in obj &&
        (obj as BridgeMessage).tag === 'bitdrift-webview-sdk'
    );
};
