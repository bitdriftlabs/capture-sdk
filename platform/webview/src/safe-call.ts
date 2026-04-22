// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/**
 * Safely execute a function, catching any exceptions.
 * Returns undefined if an exception is thrown.
 * This is used to harden the SDK against crashing client webpages.
 */
export const safeCall = <T>(fn: () => T): T | undefined => {
    try {
        return fn();
    } catch {
        return undefined;
    }
};

/**
 * Safely execute an async function, catching any exceptions.
 * Returns undefined if an exception is thrown.
 */
export const safeCallAsync = async <T>(fn: () => Promise<T>): Promise<T | undefined> => {
    try {
        return await fn();
    } catch {
        return undefined;
    }
};

/**
 * Wrap a function to make it safe - any exceptions will be silently caught.
 * Useful for wrapping callbacks passed to browser APIs.
 */
export const makeSafe = <TArgs extends unknown[], TReturn>(
    fn: (...args: TArgs) => TReturn,
): ((...args: TArgs) => TReturn | undefined) => {
    return (...args: TArgs): TReturn | undefined => {
        try {
            return fn(...args);
        } catch {
            return undefined;
        }
    };
};

/** Maximum string length for any single field sent to the native bridge. */
export const MAX_STRING_LENGTH = 4096;

/**
 * Truncate a string to a safe length to prevent excessive memory allocation
 * when passing data across the native bridge.
 */
export const truncate = (str: string, maxLength: number = MAX_STRING_LENGTH): string => {
    if (str.length <= maxLength) return str;
    return str.slice(0, maxLength) + '...<truncated>';
};

/**
 * Safely JSON.stringify an object with protection against:
 * - Circular references (returns fallback instead of throwing)
 * - Deeply nested objects (limited by maxDepth)
 * - Excessively large output (truncated to maxLength)
 */
export const safeStringify = (value: unknown, maxLength: number = MAX_STRING_LENGTH, maxDepth: number = 10): string => {
    try {
        let depth = 0;
        const seen = new WeakSet();
        const result = JSON.stringify(value, (_key, val: unknown) => {
            if (typeof val === 'object' && val !== null) {
                if (seen.has(val)) return '[Circular]';
                seen.add(val);
                depth++;
                if (depth > maxDepth) {
                    depth--;
                    return '[MaxDepth]';
                }
            }
            return val;
        });
        if (result && result.length > maxLength) {
            return result.slice(0, maxLength) + '...<truncated>';
        }
        return result ?? 'undefined';
    } catch {
        return String(value);
    }
};
