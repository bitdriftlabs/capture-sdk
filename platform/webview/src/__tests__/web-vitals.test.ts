// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { MetricType } from 'web-vitals';

describe('web-vitals', () => {
    beforeEach(() => {
        vi.resetModules();
    });

    describe('makeCloneableMetric', () => {
        it('should call toJSON on each performance entry', async () => {
            const { makeCloneableMetric } = await import('../web-vitals');

            const toJSON1 = vi.fn(() => ({ name: 'entry1', entryType: 'paint', startTime: 100, duration: 0 }));
            const toJSON2 = vi.fn(() => ({ name: 'entry2', entryType: 'paint', startTime: 200, duration: 0 }));

            const metric = {
                name: 'FCP',
                value: 100,
                rating: 'good',
                delta: 100,
                id: 'v4-123',
                navigationType: 'navigate',
                entries: [
                    { name: 'entry1', entryType: 'paint', startTime: 100, duration: 0, toJSON: toJSON1 },
                    { name: 'entry2', entryType: 'paint', startTime: 200, duration: 0, toJSON: toJSON2 },
                ],
            } as unknown as MetricType;

            const result = makeCloneableMetric(metric);

            expect(toJSON1).toHaveBeenCalledOnce();
            expect(toJSON2).toHaveBeenCalledOnce();
            expect(result.entries).toEqual([
                { name: 'entry1', entryType: 'paint', startTime: 100, duration: 0 },
                { name: 'entry2', entryType: 'paint', startTime: 200, duration: 0 },
            ]);
        });

        it('should preserve all non-entries metric fields', async () => {
            const { makeCloneableMetric } = await import('../web-vitals');

            const metric = {
                name: 'LCP',
                value: 2500,
                rating: 'needs-improvement',
                delta: 2500,
                id: 'v4-456',
                navigationType: 'reload',
                entries: [],
            } as unknown as MetricType;

            const result = makeCloneableMetric(metric);

            expect(result.name).toBe('LCP');
            expect(result.value).toBe(2500);
            expect(result.rating).toBe('needs-improvement');
            expect(result.delta).toBe(2500);
            expect(result.id).toBe('v4-456');
            expect(result.navigationType).toBe('reload');
            expect(result.entries).toEqual([]);
        });

        it('should strip non-enumerable properties from entries', async () => {
            const { makeCloneableMetric } = await import('../web-vitals');

            // Simulate a PerformanceEntry with non-enumerable properties (like real browser entries)
            const entry = Object.create(null);
            Object.defineProperty(entry, 'name', { value: 'test', enumerable: true });
            Object.defineProperty(entry, 'startTime', { value: 50, enumerable: true });
            Object.defineProperty(entry, '_internal', { value: 'hidden', enumerable: false });
            entry.toJSON = () => ({ name: 'test', startTime: 50 });

            const metric = {
                name: 'CLS',
                value: 0.1,
                rating: 'good',
                delta: 0.1,
                id: 'v4-789',
                navigationType: 'navigate',
                entries: [entry],
            } as unknown as MetricType;

            const result = makeCloneableMetric(metric);

            expect(result.entries[0]).toEqual({ name: 'test', startTime: 50 });
            expect((result.entries[0] as unknown as Record<string, unknown>)._internal).toBeUndefined();
        });

        it('should produce a result that is safely JSON-serializable', async () => {
            const { makeCloneableMetric } = await import('../web-vitals');

            // Create an entry with a circular reference that toJSON resolves
            const entry = { name: 'resource', startTime: 10, duration: 5 } as Record<string, unknown>;
            entry.self = entry; // circular

            const metric = {
                name: 'TTFB',
                value: 300,
                rating: 'good',
                delta: 300,
                id: 'v4-abc',
                navigationType: 'navigate',
                entries: [entry],
            } as unknown as MetricType;

            // Without toJSON, the circular entry makes the whole metric non-serializable
            expect(() => JSON.stringify(metric)).toThrow();

            // Now add toJSON to resolve the circular reference (as real PerformanceEntry does)
            (entry as { toJSON: () => Record<string, unknown> }).toJSON = () => ({
                name: 'resource',
                startTime: 10,
                duration: 5,
            });

            const result = makeCloneableMetric(metric);

            // Should serialize without throwing (circular ref was resolved by toJSON)
            expect(() => JSON.stringify(result)).not.toThrow();
            const parsed = JSON.parse(JSON.stringify(result));
            expect(parsed.entries[0].name).toBe('resource');
        });
    });
});
