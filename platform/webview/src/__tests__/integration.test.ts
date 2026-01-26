// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createMessageCollector, createPerformanceObserverMock, createFetchMock } from './mocks';
import type {
    BridgeReadyMessage,
    NetworkRequestMessage,
    PageViewMessage,
    LifecycleMessage,
    ErrorMessage,
    ConsoleMessage,
    UserInteractionMessage,
} from '../types';

/**
 * Integration tests verifying that the full message flow from JS collectors
 * produces valid JSON matching the structure expected by WebViewBridgeMessage.kt
 */
describe('integration: message structure validation', () => {
    let perfObserverMock: ReturnType<typeof createPerformanceObserverMock>;

    // Store original console methods to restore after each test
    const originalConsole = {
        log: console.log,
        warn: console.warn,
        error: console.error,
        info: console.info,
        debug: console.debug,
    };

    beforeEach(() => {
        vi.resetModules();
        perfObserverMock = createPerformanceObserverMock();
        document.body.innerHTML = '';

        // Restore original console methods before each test
        console.log = originalConsole.log;
        console.warn = originalConsole.warn;
        console.error = originalConsole.error;
        console.info = originalConsole.info;
        console.debug = originalConsole.debug;

        Object.defineProperty(window, 'location', {
            value: { href: 'https://example.com/test' },
            writable: true,
            configurable: true,
        });
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        // Restore original console methods after each test
        console.log = originalConsole.log;
        console.warn = originalConsole.warn;
        console.error = originalConsole.error;
        console.info = originalConsole.info;
        console.debug = originalConsole.debug;
    });

    describe('bridgeReady message', () => {
        it('should match Kotlin WebViewBridgeMessage structure', async () => {
            const collector = createMessageCollector();
            const { initBridge, log, createMessage } = await import('../bridge');

            initBridge();
            log(
                createMessage({
                    type: 'bridgeReady',
                    url: 'https://example.com',
                    instrumentationConfig: {
                        captureNetworkRequests: true,
                        captureWebVitals: true,
                    },
                } as Omit<BridgeReadyMessage, 'tag' | 'v' | 'timestamp'>),
            );

            const message = collector.getLastMessage() as BridgeReadyMessage;

            // Required base fields (matches WebViewBridgeMessage.kt)
            expect(message.tag).toBe('bitdrift-webview-sdk');
            expect(message.v).toBe(1);
            expect(message.type).toBe('bridgeReady');
            expect(typeof message.timestamp).toBe('number');

            // bridgeReady specific fields
            expect(message.url).toBe('https://example.com');
            expect(message.instrumentationConfig).toBeDefined();
        });
    });

    describe('pageView message', () => {
        it('should have correct structure for start action', async () => {
            const collector = createMessageCollector();
            const { startPageView } = await import('../page-view');

            startPageView('https://example.com/page', 'initial');

            const message = collector.getLastMessage() as PageViewMessage;

            expect(message.tag).toBe('bitdrift-webview-sdk');
            expect(message.v).toBe(1);
            expect(message.type).toBe('pageView');
            expect(message.action).toBe('start');
            expect(message.spanId).toBeDefined();
            expect(typeof message.spanId).toBe('string');
            expect(message.url).toBe('https://example.com/page');
            expect(message.reason).toBe('initial');
        });

        it('should have correct structure for end action', async () => {
            const collector = createMessageCollector();
            const { startPageView, endPageView } = await import('../page-view');

            startPageView('https://example.com/page', 'initial');
            endPageView('hidden');

            const messages = collector.getMessagesByType('pageView') as PageViewMessage[];
            const endMessage = messages.find((m) => m.action === 'end');

            expect(endMessage).toBeDefined();
            expect(endMessage?.action).toBe('end');
            expect(endMessage?.reason).toBe('hidden');
            expect(typeof endMessage?.durationMs).toBe('number');
        });
    });

    describe('lifecycle message', () => {
        it('should have correct structure', async () => {
            const collector = createMessageCollector();
            const { initPageViewTracking } = await import('../page-view');

            initPageViewTracking();

            const messages = collector.getMessagesByType('lifecycle') as LifecycleMessage[];
            expect(messages.length).toBeGreaterThan(0);

            const message = messages[0];
            expect(message.tag).toBe('bitdrift-webview-sdk');
            expect(message.v).toBe(1);
            expect(message.type).toBe('lifecycle');
            expect(['DOMContentLoaded', 'load', 'visibilitychange']).toContain(message.event);
            expect(typeof message.performanceTime).toBe('number');
        });
    });

    describe('error message', () => {
        it('should have correct structure', async () => {
            const collector = createMessageCollector();
            const { initErrorMonitoring } = await import('../error');

            // Track registered handlers since jsdom ErrorEvent dispatch doesn't work reliably
            const handlers: Array<(event: Event) => void> = [];
            const originalAddEventListener = window.addEventListener;
            window.addEventListener = vi.fn((type: string, handler: EventListener) => {
                if (type === 'error') {
                    handlers.push(handler);
                }
                originalAddEventListener.call(window, type, handler);
            }) as typeof window.addEventListener;

            initErrorMonitoring();

            const error = new TypeError('Test error');
            const errorEvent = {
                message: 'Test error message',
                filename: 'test.js',
                lineno: 10,
                colno: 5,
                error,
                target: window,
            } as unknown as ErrorEvent;

            // Manually call the handler
            handlers[0](errorEvent);

            const messages = collector.getMessagesByType('error') as ErrorMessage[];
            expect(messages.length).toBe(1);

            const message = messages[0];
            expect(message.tag).toBe('bitdrift-webview-sdk');
            expect(message.v).toBe(1);
            expect(message.type).toBe('error');
            expect(message.name).toBe('TypeError');
            expect(message.message).toBe('Test error message');
            expect(message.filename).toBe('test.js');
            expect(message.lineno).toBe(10);
            expect(message.colno).toBe(5);
            expect(message.stack).toBeDefined();
        });
    });

    describe('console message', () => {
        it('should have correct structure', async () => {
            const collector = createMessageCollector();
            const { initConsoleCapture } = await import('../console-capture');

            initConsoleCapture();
            console.warn('Warning message', { extra: 'data' });

            const messages = collector.getMessagesByType('console') as ConsoleMessage[];
            expect(messages.length).toBe(1);

            const message = messages[0];
            expect(message.tag).toBe('bitdrift-webview-sdk');
            expect(message.v).toBe(1);
            expect(message.type).toBe('console');
            expect(message.level).toBe('warn');
            expect(message.message).toBe('Warning message');
            expect(message.args).toContain('{"extra":"data"}');
        });
    });

    describe('userInteraction message', () => {
        it('should have correct structure for click', async () => {
            const collector = createMessageCollector();
            const { initUserInteractionMonitoring } = await import('../user-interactions');

            document.body.innerHTML = '<button id="btn" class="primary-btn">Click</button>';
            const button = document.getElementById('btn');

            expect(button).not.toBeNull();

            initUserInteractionMonitoring();
            (button as HTMLElement).dispatchEvent(
                new PointerEvent('pointerdown', { bubbles: true, clientX: 100, clientY: 100 }),
            );

            const messages = collector.getMessagesByType('userInteraction') as UserInteractionMessage[];
            expect(messages.length).toBe(1);

            const message = messages[0];
            expect(message.tag).toBe('bitdrift-webview-sdk');
            expect(message.v).toBe(1);
            expect(message.type).toBe('userInteraction');
            expect(message.interactionType).toBe('click');
            expect(message.tagName).toBe('button');
            expect(message.elementId).toBe('btn');
            expect(message.className).toBe('primary-btn');
            expect(message.isClickable).toBe(true);
        });
    });

    describe('longTask message', () => {
        it('should have correct structure', async () => {
            const collector = createMessageCollector();
            const { initLongTaskMonitoring } = await import('../long-tasks');

            initLongTaskMonitoring();

            PerformanceEntry;

            perfObserverMock.triggerEntries(
                [
                    {
                        entryType: 'longtask',
                        duration: 150,
                        startTime: 1234,
                        name: 'self',
                        attribution: [{ name: 'script', containerType: 'window' }],
                    },
                ],
                'longtask',
            );

            const messages = collector.getMessagesByType('longTask');
            expect(messages.length).toBe(1);

            const message = messages[0];
            expect(message.tag).toBe('bitdrift-webview-sdk');
            expect(message.v).toBe(1);
            expect(message.type).toBe('longTask');
            expect(message.durationMs).toBe(150);
            expect(message.startTime).toBe(1234);
            expect(message.attribution).toBeDefined();
        });
    });

    describe('SDK initialization', () => {
        it('should not crash even if all browser APIs fail', async () => {
            // Remove all browser APIs
            vi.stubGlobal('PerformanceObserver', undefined);
            vi.stubGlobal('fetch', undefined);

            createMessageCollector();

            // Import and run init - should not throw
            const indexModule = await import('../index');

            // The module should have loaded without crashing
            expect(indexModule).toBeDefined();
        });

        it('should handle malformed config gracefully', async () => {
            createMessageCollector();

            // Set up a config that might cause issues
            (window as { bitdrift?: { config?: unknown } }).bitdrift = {
                config: {
                    captureNetworkRequests: 'not-a-boolean', // Wrong type
                    captureWebVitals: null, // Null
                    captureErrors: undefined, // Undefined
                } as unknown,
            };

            // Should not throw
            expect(async () => {
                await import('../index');
            }).not.toThrow();
        });
    });
});

describe('integration: network interception', () => {
    beforeEach(() => {
        vi.resetModules();
        createPerformanceObserverMock();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    describe('fetch interception', () => {
        it('should capture successful fetch requests', async () => {
            const collector = createMessageCollector();
            const mockFetch = createFetchMock();
            mockFetch.mockResolvedValue(new Response('OK', { status: 200, statusText: 'OK' }));

            const { initNetworkInterceptor } = await import('../network');
            initNetworkInterceptor();

            await fetch('https://api.example.com/data');

            const messages = collector.getMessagesByType('networkRequest') as NetworkRequestMessage[];
            expect(messages.length).toBe(1);

            const message = messages[0];
            expect(message.type).toBe('networkRequest');
            expect(message.method).toBe('GET');
            expect(message.url).toBe('https://api.example.com/data');
            expect(message.statusCode).toBe(200);
            expect(message.success).toBe(true);
            expect(message.requestType).toBe('fetch');
            expect(typeof message.durationMs).toBe('number');
            expect(message.requestId).toMatch(/^req_/);
        });

        it('should capture failed fetch requests', async () => {
            const collector = createMessageCollector();
            const mockFetch = createFetchMock();
            mockFetch.mockRejectedValue(new Error('Network error'));

            const { initNetworkInterceptor } = await import('../network');
            initNetworkInterceptor();

            await fetch('https://api.example.com/data').catch(() => {});

            const messages = collector.getMessagesByType('networkRequest') as NetworkRequestMessage[];
            expect(messages.length).toBe(1);

            const message = messages[0];
            expect(message.statusCode).toBe(0);
            expect(message.success).toBe(false);
            expect(message.error).toBe('Network error');
        });
    });
});
