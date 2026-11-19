// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { describe, it, expect } from 'vitest';
import { safeCall, safeCallAsync, makeSafe, truncate, safeStringify, MAX_STRING_LENGTH } from '../safe-call';

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

describe('truncate', () => {
    it('should return short strings unchanged', () => {
        expect(truncate('hello')).toBe('hello');
    });

    it('should truncate strings exceeding maxLength', () => {
        const result = truncate('abcdef', 3);
        expect(result).toBe('abc...<truncated>');
    });

    it('should truncate at MAX_STRING_LENGTH by default', () => {
        const longStr = 'x'.repeat(MAX_STRING_LENGTH + 100);
        const result = truncate(longStr);
        expect(result.length).toBe(MAX_STRING_LENGTH + '...<truncated>'.length);
        expect(result).toContain('...<truncated>');
    });

    it('should return exact-length strings unchanged', () => {
        const str = 'x'.repeat(MAX_STRING_LENGTH);
        expect(truncate(str)).toBe(str);
    });

    it('should handle empty strings', () => {
        expect(truncate('')).toBe('');
    });
});

describe('safeStringify', () => {
    it('should stringify simple objects', () => {
        expect(safeStringify({ a: 1 })).toBe('{"a":1}');
    });

    it('should handle circular references without throwing', () => {
        const obj: Record<string, unknown> = { a: 1 };
        obj.self = obj;
        const result = safeStringify(obj);
        expect(result).toContain('[Circular]');
        expect(result).not.toThrow;
    });

    it('should cap deeply nested objects', () => {
        // Build an object nested deeper than maxDepth
        let obj: Record<string, unknown> = { value: 'leaf' };
        for (let i = 0; i < 20; i++) {
            obj = { child: obj };
        }
        const result = safeStringify(obj, 4096, 5);
        expect(result).toContain('[MaxDepth]');
    });

    it('should truncate excessively large output', () => {
        const bigArray = Array.from({ length: 10000 }, (_, i) => `item-${i}`);
        const result = safeStringify(bigArray, 500);
        expect(result.length).toBeLessThanOrEqual(500 + '...<truncated>'.length);
        expect(result).toContain('...<truncated>');
    });

    it('should handle null and undefined', () => {
        expect(safeStringify(null)).toBe('null');
        expect(safeStringify(undefined)).toBe('undefined');
    });

    it('should handle primitives', () => {
        expect(safeStringify(42)).toBe('42');
        expect(safeStringify('hello')).toBe('"hello"');
        expect(safeStringify(true)).toBe('true');
    });

    it('should fall back to String() when JSON.stringify throws', () => {
        const evil = {
            toJSON() {
                throw new Error('no');
            },
        };
        const result = safeStringify(evil);
        expect(result).toBe('[object Object]');
    });
});
