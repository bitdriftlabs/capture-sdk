// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createMessageCollector } from './mocks';

describe('error monitoring', () => {
    beforeEach(() => {
        vi.resetModules();
    });

    describe('initErrorMonitoring', () => {
        it('should register error handler and capture errors', async () => {
            const collector = createMessageCollector();
            const { initErrorMonitoring } = await import('../error');

            // Track registered handlers
            const handlers: Array<(event: Event) => void> = [];
            const originalAddEventListener = window.addEventListener;
            window.addEventListener = vi.fn((type: string, handler: EventListener) => {
                if (type === 'error') {
                    handlers.push(handler);
                }
                originalAddEventListener.call(window, type, handler);
            }) as typeof window.addEventListener;

            initErrorMonitoring();

            // Verify handler was registered
            expect(handlers.length).toBe(1);

            // Create and manually call the handler with a proper error event
            const errorEvent = {
                message: 'Test error message',
                filename: 'test.js',
                lineno: 42,
                colno: 10,
                error: new TypeError('Something went wrong'),
                target: window,
            } as unknown as ErrorEvent;

            handlers[0](errorEvent);

            const messages = collector.getMessagesByType('error');
            expect(messages.length).toBe(1);
            expect(messages[0].message).toBe('Test error message');
            expect(messages[0].name).toBe('TypeError');
            expect(messages[0].filename).toBe('test.js');
            expect(messages[0].lineno).toBe(42);
            expect(messages[0].colno).toBe(10);

            window.addEventListener = originalAddEventListener;
        });

        it('should handle errors without Error instance', async () => {
            const collector = createMessageCollector();
            const { initErrorMonitoring } = await import('../error');

            const handlers: Array<(event: Event) => void> = [];
            const originalAddEventListener = window.addEventListener;
            window.addEventListener = vi.fn((type: string, handler: EventListener) => {
                if (type === 'error') {
                    handlers.push(handler);
                }
                originalAddEventListener.call(window, type, handler);
            }) as typeof window.addEventListener;

            initErrorMonitoring();

            const errorEvent = {
                message: 'String error',
                error: null,
                target: window,
            } as unknown as ErrorEvent;

            handlers[0](errorEvent);

            const messages = collector.getMessagesByType('error');
            expect(messages.length).toBe(1);
            expect(messages[0].name).toBe('Error');
            expect(messages[0].message).toBe('String error');

            window.addEventListener = originalAddEventListener;
        });

        it('should not crash when processing error throws', async () => {
            createMessageCollector();
            const { initErrorMonitoring } = await import('../error');

            initErrorMonitoring();

            // Even if internal processing throws, it shouldn't propagate
            const errorEvent = new ErrorEvent('error', {
                message: 'Test error',
                error: {
                    get name() {
                        throw new Error('Getter throws');
                    },
                },
            });

            expect(() => window.dispatchEvent(errorEvent)).not.toThrow();
        });

        it('should skip resource errors (those with target as Element)', async () => {
            const collector = createMessageCollector();
            const { initErrorMonitoring } = await import('../error');

            const handlers: Array<(event: Event) => void> = [];
            const originalAddEventListener = window.addEventListener;
            window.addEventListener = vi.fn((type: string, handler: EventListener) => {
                if (type === 'error') {
                    handlers.push(handler);
                }
                originalAddEventListener.call(window, type, handler);
            }) as typeof window.addEventListener;

            initErrorMonitoring();

            // Resource errors have target as an Element, not window
            const img = document.createElement('img');
            const errorEvent = {
                message: 'Image failed to load',
                error: null,
                target: img, // Element target = resource error
            } as unknown as ErrorEvent;

            handlers[0](errorEvent);

            // Should not be captured - resource errors are handled separately
            const messages = collector.getMessagesByType('error');
            expect(messages.length).toBe(0);

            window.addEventListener = originalAddEventListener;
        });
    });

    describe('initPromiseRejectionMonitoring', () => {
        it('should capture unhandled promise rejections with Error', async () => {
            const collector = createMessageCollector();
            const { initPromiseRejectionMonitoring } = await import('../error');

            const handlers: Array<(event: Event) => void> = [];
            const originalAddEventListener = window.addEventListener;
            window.addEventListener = vi.fn((type: string, handler: EventListener) => {
                if (type === 'unhandledrejection') {
                    handlers.push(handler);
                }
                originalAddEventListener.call(window, type, handler);
            }) as typeof window.addEventListener;

            initPromiseRejectionMonitoring();

            const error = new Error('Promise failed');
            const event = {
                reason: error,
                promise: Promise.resolve(),
            } as PromiseRejectionEvent;

            handlers[0](event);

            const messages = collector.getMessagesByType('promiseRejection');
            expect(messages.length).toBe(1);
            expect(messages[0].reason).toBe('Promise failed');
            expect(messages[0].stack).toBeDefined();

            window.addEventListener = originalAddEventListener;
        });

        it('should capture unhandled promise rejections with string', async () => {
            const collector = createMessageCollector();
            const { initPromiseRejectionMonitoring } = await import('../error');

            const handlers: Array<(event: Event) => void> = [];
            const originalAddEventListener = window.addEventListener;
            window.addEventListener = vi.fn((type: string, handler: EventListener) => {
                if (type === 'unhandledrejection') {
                    handlers.push(handler);
                }
                originalAddEventListener.call(window, type, handler);
            }) as typeof window.addEventListener;

            initPromiseRejectionMonitoring();

            const event = {
                reason: 'string reason',
                promise: Promise.resolve(),
            } as PromiseRejectionEvent;

            handlers[0](event);

            const messages = collector.getMessagesByType('promiseRejection');
            expect(messages.length).toBe(1);
            expect(messages[0].reason).toBe('string reason');

            window.addEventListener = originalAddEventListener;
        });

        it('should capture unhandled promise rejections with object', async () => {
            const collector = createMessageCollector();
            const { initPromiseRejectionMonitoring } = await import('../error');

            const handlers: Array<(event: Event) => void> = [];
            const originalAddEventListener = window.addEventListener;
            window.addEventListener = vi.fn((type: string, handler: EventListener) => {
                if (type === 'unhandledrejection') {
                    handlers.push(handler);
                }
                originalAddEventListener.call(window, type, handler);
            }) as typeof window.addEventListener;

            initPromiseRejectionMonitoring();

            const event = {
                reason: { code: 500, message: 'Server error' },
                promise: Promise.resolve(),
            } as PromiseRejectionEvent;

            handlers[0](event);

            const messages = collector.getMessagesByType('promiseRejection');
            expect(messages.length).toBe(1);
            expect(messages[0].reason).toContain('500');
            expect(messages[0].reason).toContain('Server error');

            window.addEventListener = originalAddEventListener;
        });

        it('should handle null/undefined rejection reason', async () => {
            const collector = createMessageCollector();
            const { initPromiseRejectionMonitoring } = await import('../error');

            const handlers: Array<(event: Event) => void> = [];
            const originalAddEventListener = window.addEventListener;
            window.addEventListener = vi.fn((type: string, handler: EventListener) => {
                if (type === 'unhandledrejection') {
                    handlers.push(handler);
                }
                originalAddEventListener.call(window, type, handler);
            }) as typeof window.addEventListener;

            initPromiseRejectionMonitoring();

            const event = {
                reason: null,
                promise: Promise.resolve(),
            } as PromiseRejectionEvent;

            handlers[0](event);

            const messages = collector.getMessagesByType('promiseRejection');
            expect(messages.length).toBe(1);
            expect(messages[0].reason).toBe('Unknown rejection reason');

            window.addEventListener = originalAddEventListener;
        });
    });
});
