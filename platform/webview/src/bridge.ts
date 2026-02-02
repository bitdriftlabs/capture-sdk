// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { safeCall } from './safe-call';
import type { AnyBridgeMessage, AnyBridgeMessageMap, BridgeMessage, CustomLogMessage, SerializableLogFields } from './types';

export const pristine = {
    console: {
        log: console.log,
        warn: console.warn,
        error: console.error,
        info: console.info,
        debug: console.debug,
    },
};

/**
 * Create a fields object with standard fields that are always included:
 * - _source: "webview"
 * - _timestamp: current timestamp in ms
 */
export const createStandardFields = (timestamp?: number): { _source: string; _timestamp: string } => {
    return {
        _source: 'webview',
        _timestamp: (timestamp ?? Date.now()).toString(),
    };
};

/**
 * Flatten nested objects with underscore-prefixed keys.
 * E.g., { attribution: { name: 'foo', containerType: 'bar' } }
 * becomes { _attribution_name: 'foo', _container_type: 'bar' }
 */
export const flattenObject = (
    obj: Record<string, unknown>,
    prefix = '',
): Record<string, string> => {
    const result: Record<string, string> = {};
    
    for (const [key, value] of Object.entries(obj)) {
        if (value === null || value === undefined) {
            continue;
        }
        
        // Convert camelCase to snake_case
        const snakeKey = key.replace(/[A-Z]/g, (letter) => `_${letter.toLowerCase()}`);
        // Build the full field key
        const fieldKey = prefix ? `${prefix}${snakeKey}` : `_${snakeKey}`;
        
        if (typeof value === 'object' && !Array.isArray(value)) {
            // Recursively flatten nested objects
            // For nested objects, pass the current fieldKey + underscore as prefix
            Object.assign(result, flattenObject(value as Record<string, unknown>, fieldKey + '_'));
        } else {
            // Convert value to string for logging
            result[fieldKey] = value.toString();
        }
    }
    
    return result;
};

type Platform = 'ios' | 'android' | 'unknown';

const detectPlatform = (): Platform => {
    return (
        safeCall(() => {
            if (window.webkit?.messageHandlers?.BitdriftLogger) {
                return 'ios';
            }
            if (window.BitdriftLogger) {
                return 'android';
            }
            return 'unknown';
        }) ?? 'unknown'
    );
};

const sendToNative = (message: AnyBridgeMessage): void => {
    safeCall(() => {
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
    });
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
                message = createMessage({
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
 * Helper to create a timestamped message with standard fields automatically added
 */
export const createMessage = <T extends keyof AnyBridgeMessageMap>({
    type,
    timestamp,
    fields,
    ...partial
}: { type: T; timestamp?: number; fields?: SerializableLogFields } & Omit<
    AnyBridgeMessageMap[T],
    'v' | 'timestamp' | 'tag' | 'type' | 'fields'
>): AnyBridgeMessageMap[T] => {
    const ts = timestamp ?? Date.now();
    const standardFields = createStandardFields(ts);
    
    return {
        tag: 'bitdrift-webview-sdk',
        v: 1,
        timestamp: ts,
        type,
        ...partial,
        // Merge standard fields with provided fields
        fields: fields ? { ...standardFields, ...fields } : standardFields,
    } as unknown as AnyBridgeMessageMap[T];
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
