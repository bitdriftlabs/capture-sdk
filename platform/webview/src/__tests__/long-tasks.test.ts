// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createMessageCollector, createPerformanceObserverMock } from './mocks';

describe('long task monitoring', () => {
    let perfObserverMock: ReturnType<typeof createPerformanceObserverMock>;

    beforeEach(() => {
        vi.resetModules();
        perfObserverMock = createPerformanceObserverMock();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    describe('initLongTaskMonitoring', () => {
        it('should register PerformanceObserver for longtask', async () => {
            createMessageCollector();
            const { initLongTaskMonitoring } = await import('../long-tasks');

            initLongTaskMonitoring();

            expect(perfObserverMock.MockPerformanceObserver).toHaveBeenCalled();
            const observers = perfObserverMock.getObservers();
            expect(observers.length).toBe(1);
            expect(observers[0].options.type).toBe('longtask');
        });

        it('should capture long task entries', async () => {
            const collector = createMessageCollector();
            const { initLongTaskMonitoring } = await import('../long-tasks');

            initLongTaskMonitoring();

            // Simulate a long task entry
            perfObserverMock.triggerEntries(
                [
                    {
                        entryType: 'longtask',
                        duration: 120,
                        startTime: 1000,
                        name: 'self',
                        attribution: [
                            {
                                name: 'script',
                                containerType: 'iframe',
                                containerSrc: 'https://example.com/frame.js',
                            },
                        ],
                    },
                ],
                'longtask',
            );

            const messages = collector.getMessagesByType('longTask');
            expect(messages.length).toBe(1);
            expect(messages[0].durationMs).toBe(120);
            expect(messages[0].startTime).toBe(1000);
            expect(messages[0].attribution).toBeDefined();
        });

        it('should handle missing attribution gracefully', async () => {
            const collector = createMessageCollector();
            const { initLongTaskMonitoring } = await import('../long-tasks');

            initLongTaskMonitoring();

            perfObserverMock.triggerEntries(
                [
                    {
                        entryType: 'longtask',
                        duration: 80,
                        startTime: 500,
                        name: 'self',
                        // No attribution
                    },
                ],
                'longtask',
            );

            const messages = collector.getMessagesByType('longTask');
            expect(messages.length).toBe(1);
            expect(messages[0].attribution).toBeUndefined();
        });

        it('should not crash when PerformanceObserver is unavailable', async () => {
            vi.stubGlobal('PerformanceObserver', undefined);
            createMessageCollector();

            const { initLongTaskMonitoring } = await import('../long-tasks');

            expect(() => initLongTaskMonitoring()).not.toThrow();
        });

        it('should not crash when observer callback throws', async () => {
            const collector = createMessageCollector();
            // Make the mock throw on getEntries
            collector.mock.log.mockImplementationOnce(() => {
                throw new Error('Logging error');
            });

            const { initLongTaskMonitoring } = await import('../long-tasks');
            initLongTaskMonitoring();

            // Should not throw even if internal processing fails
            expect(() =>
                perfObserverMock.triggerEntries(
                    [{ entryType: 'longtask', duration: 100, startTime: 0, name: 'self' }],
                    'longtask',
                ),
            ).not.toThrow();
        });
    });
});
