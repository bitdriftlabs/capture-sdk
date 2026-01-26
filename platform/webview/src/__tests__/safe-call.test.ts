// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { describe, it, expect } from 'vitest';
import { safeCall, safeCallAsync, makeSafe } from '../safe-call';

describe('safeCall', () => {
    it('should return value when function succeeds', () => {
        const result = safeCall(() => 42);
        expect(result).toBe(42);
    });

    it('should return undefined when function throws', () => {
        const result = safeCall(() => {
            throw new Error('test error');
        });
        expect(result).toBeUndefined();
    });

    it('should handle null return values', () => {
        const result = safeCall(() => null);
        expect(result).toBeNull();
    });

    it('should handle complex objects', () => {
        const obj = { foo: 'bar', nested: { value: 123 } };
        const result = safeCall(() => obj);
        expect(result).toEqual(obj);
    });
});

describe('safeCallAsync', () => {
    it('should return value when async function succeeds', async () => {
        const result = await safeCallAsync(async () => 42);
        expect(result).toBe(42);
    });

    it('should return undefined when async function throws', async () => {
        const result = await safeCallAsync(async () => {
            throw new Error('test error');
        });
        expect(result).toBeUndefined();
    });

    it('should return undefined when async function rejects', async () => {
        const result = await safeCallAsync(() => Promise.reject(new Error('rejected')));
        expect(result).toBeUndefined();
    });
});

describe('makeSafe', () => {
    it('should return wrapped function that catches errors', () => {
        const unsafeFn = (shouldThrow: boolean) => {
            if (shouldThrow) throw new Error('boom');
            return 'success';
        };

        const safeFn = makeSafe(unsafeFn);

        expect(safeFn(false)).toBe('success');
        expect(safeFn(true)).toBeUndefined();
    });

    it('should preserve function arguments', () => {
        const add = (a: number, b: number) => a + b;
        const safeAdd = makeSafe(add);

        expect(safeAdd(2, 3)).toBe(5);
    });

    it('should work as event handler', () => {
        let called = false;
        const handler = makeSafe(() => {
            called = true;
            throw new Error('should not propagate');
        });

        // Should not throw
        expect(() => handler()).not.toThrow();
        expect(called).toBe(true);
    });
});
