// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createMessageCollector } from './mocks';

describe('page view tracking', () => {
    beforeEach(() => {
        vi.resetModules();
        // Mock window.location
        Object.defineProperty(window, 'location', {
            value: { href: 'https://example.com/page' },
            writable: true,
        });
    });

    describe('initPageViewTracking', () => {
        it('should start initial page view', async () => {
            const collector = createMessageCollector();
            const { initPageViewTracking } = await import('../page-view');

            initPageViewTracking();

            const messages = collector.getMessagesByType('pageView');
            expect(messages.length).toBeGreaterThanOrEqual(1);

            const startMessage = messages.find((m) => m.action === 'start');
            expect(startMessage).toBeDefined();
            expect(startMessage?.reason).toBe('initial');
            expect(startMessage?.url).toBe('https://example.com/page');
            expect(startMessage?.spanId).toBeDefined();
        });

        it('should log lifecycle events', async () => {
            const collector = createMessageCollector();
            const { initPageViewTracking } = await import('../page-view');

            initPageViewTracking();

            const messages = collector.getMessagesByType('lifecycle');
            // Should have at least DOMContentLoaded and load
            expect(messages.length).toBeGreaterThanOrEqual(1);
        });
    });

    describe('getCurrentPageSpanId', () => {
        it('should return current span ID after init', async () => {
            createMessageCollector();
            const { initPageViewTracking, getCurrentPageSpanId } = await import('../page-view');

            // Before init, should be null
            expect(getCurrentPageSpanId()).toBeNull();

            initPageViewTracking();

            // After init, should have a span ID
            const spanId = getCurrentPageSpanId();
            expect(spanId).toBeDefined();
            expect(spanId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
        });
    });

    describe('startPageView', () => {
        it('should end previous page view before starting new one', async () => {
            const collector = createMessageCollector();
            const { startPageView } = await import('../page-view');

            startPageView('https://example.com/page1', 'initial');
            startPageView('https://example.com/page2', 'navigation');

            const messages = collector.getMessagesByType('pageView');
            // Should have: start page1, end page1, start page2
            expect(messages.length).toBe(3);

            expect(messages[0].action).toBe('start');
            expect(messages[0].url).toBe('https://example.com/page1');

            expect(messages[1].action).toBe('end');
            expect(messages[1].reason).toBe('navigation');

            expect(messages[2].action).toBe('start');
            expect(messages[2].url).toBe('https://example.com/page2');
        });

        it('should use performance.timeOrigin for initial page view', async () => {
            const collector = createMessageCollector();
            const { startPageView } = await import('../page-view');

            const beforeStart = Date.now();
            startPageView('https://example.com', 'initial');

            const messages = collector.getMessagesByType('pageView');
            const startMessage = messages.find((m) => m.action === 'start');

            // For initial, timestamp should be close to performance.timeOrigin
            expect(startMessage?.timestamp).toBeLessThanOrEqual(beforeStart);
        });
    });

    describe('endPageView', () => {
        it('should calculate duration correctly', async () => {
            vi.useFakeTimers();
            const collector = createMessageCollector();
            const { startPageView, endPageView } = await import('../page-view');

            startPageView('https://example.com', 'navigation');
            vi.advanceTimersByTime(5000);
            endPageView('hidden');

            const messages = collector.getMessagesByType('pageView');
            const endMessage = messages.find((m) => m.action === 'end');

            expect(endMessage?.durationMs).toBeGreaterThanOrEqual(5000);
            expect(endMessage?.reason).toBe('hidden');

            vi.useRealTimers();
        });

        it('should do nothing if no active page view', async () => {
            const collector = createMessageCollector();
            const { endPageView } = await import('../page-view');

            // End without starting - should not throw or log
            endPageView('unload');

            const messages = collector.getMessagesByType('pageView');
            expect(messages.length).toBe(0);
        });
    });

    describe('visibility change handling', () => {
        it('should end page view on visibility hidden', async () => {
            const collector = createMessageCollector();
            const { initPageViewTracking } = await import('../page-view');

            initPageViewTracking();

            // Simulate visibility change to hidden
            Object.defineProperty(document, 'visibilityState', {
                value: 'hidden',
                writable: true,
            });
            document.dispatchEvent(new Event('visibilitychange'));

            const messages = collector.getMessagesByType('pageView');
            const endMessage = messages.find((m) => m.action === 'end' && m.reason === 'hidden');
            expect(endMessage).toBeDefined();
        });
    });
});
