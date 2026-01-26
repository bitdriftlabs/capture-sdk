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
