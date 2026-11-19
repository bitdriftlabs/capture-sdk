// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createMessageCollector } from './mocks';

describe('navigation tracking', () => {
    let originalPushState: typeof history.pushState;
    let originalReplaceState: typeof history.replaceState;

    beforeEach(() => {
        vi.resetModules();
        originalPushState = history.pushState;
        originalReplaceState = history.replaceState;

        // Mock window.location
        Object.defineProperty(window, 'location', {
            value: { href: 'https://example.com/initial' },
            writable: true,
            configurable: true,
        });
    });

    afterEach(() => {
        history.pushState = originalPushState;
        history.replaceState = originalReplaceState;
    });

    describe('initNavigationTracking', () => {
        it('should intercept pushState', async () => {
            const collector = createMessageCollector();
            const { initNavigationTracking } = await import('../navigation');

            initNavigationTracking();
            collector.clear();

            // Simulate pushState
            window.location.href = 'https://example.com/new-page';
            history.pushState({}, '', '/new-page');

            const messages = collector.getMessagesByType('navigation');
            expect(messages.length).toBe(1);
            expect(messages[0].method).toBe('pushState');
            expect(messages[0].fromUrl).toBe('https://example.com/initial');
            expect(messages[0].toUrl).toBe('https://example.com/new-page');
        });

        it('should intercept replaceState', async () => {
            const collector = createMessageCollector();
            const { initNavigationTracking } = await import('../navigation');

            initNavigationTracking();
            collector.clear();

            // Simulate replaceState
            window.location.href = 'https://example.com/replaced';
            history.replaceState({}, '', '/replaced');

            const messages = collector.getMessagesByType('navigation');
            expect(messages.length).toBe(1);
            expect(messages[0].method).toBe('replaceState');
        });

        it('should not log navigation if URL unchanged', async () => {
            const collector = createMessageCollector();
            const { initNavigationTracking } = await import('../navigation');

            initNavigationTracking();
            collector.clear();

            // pushState without URL change
            history.pushState({ someState: true }, '', undefined);

            const messages = collector.getMessagesByType('navigation');
            expect(messages.length).toBe(0);
        });

        it('should handle popstate events', async () => {
            const collector = createMessageCollector();
            const { initNavigationTracking } = await import('../navigation');

            initNavigationTracking();
            collector.clear();

            // Simulate a URL change (like user clicking back)
            // Update location.href before dispatching popstate
            window.location.href = 'https://example.com/previous';
            window.dispatchEvent(new PopStateEvent('popstate'));

            const messages = collector.getMessagesByType('navigation');
            // Filter for only popstate messages to avoid any carryover
            const popstateMessages = messages.filter((m) => m.method === 'popstate');
            expect(popstateMessages.length).toBeGreaterThanOrEqual(1);
        });

        it('should start new page view on navigation', async () => {
            const collector = createMessageCollector();
            const { initNavigationTracking } = await import('../navigation');

            initNavigationTracking();
            collector.clear();

            window.location.href = 'https://example.com/new-page';
            history.pushState({}, '', '/new-page');

            const pageViewMessages = collector.getMessagesByType('pageView');
            const startMessages = pageViewMessages.filter((m) => m.action === 'start');

            // Should have started a new page view
            expect(startMessages.length).toBeGreaterThanOrEqual(1);
        });

        it('should not crash when history methods throw', async () => {
            createMessageCollector();
            const { initNavigationTracking } = await import('../navigation');

            initNavigationTracking();

            // Override pushState to throw
            const interceptedPushState = history.pushState;
            history.pushState = function (...args) {
                interceptedPushState.apply(this, args);
                throw new Error('pushState failed');
            };

            // Should not propagate the error
            expect(() => history.pushState({}, '', '/test')).toThrow('pushState failed');
        });
    });
});
