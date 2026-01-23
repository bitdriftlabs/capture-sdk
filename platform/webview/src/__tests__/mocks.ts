// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { vi } from 'vitest';
import type { AnyBridgeMessage } from '../types';

/**
 * Mock for Android BitdriftLogger bridge
 */
export const createAndroidBridgeMock = () => {
    const mock = {
        log: vi.fn(),
    };
    (window as { BitdriftLogger?: typeof mock }).BitdriftLogger = mock;
    return mock;
};

/**
 * Mock for iOS webkit messageHandlers bridge
 */
export const createIOSBridgeMock = () => {
    const mock = {
        postMessage: vi.fn(),
    };
    (window as { webkit?: { messageHandlers?: { BitdriftLogger?: typeof mock } } }).webkit = {
        messageHandlers: {
            BitdriftLogger: mock,
        },
    };
    return mock;
};

/**
 * Collect all messages sent to the bridge
 */
export const createMessageCollector = () => {
    const messages: AnyBridgeMessage[] = [];
    const androidMock = createAndroidBridgeMock();
    androidMock.log.mockImplementation((serialized: string) => {
        messages.push(JSON.parse(serialized) as AnyBridgeMessage);
    });
    return {
        messages,
        mock: androidMock,
        getMessages: () => messages,
        getLastMessage: () => messages[messages.length - 1],
        getMessagesByType: <T extends AnyBridgeMessage['type']>(type: T) =>
            messages.filter((m) => m.type === type) as Extract<AnyBridgeMessage, { type: T }>[],
        clear: () => {
            messages.length = 0;
        },
    };
};

/**
 * Create a mock PerformanceObserver
 */
export const createPerformanceObserverMock = () => {
    const observers: Array<{
        callback: PerformanceObserverCallback;
        options: PerformanceObserverInit;
    }> = [];

    const MockPerformanceObserver = vi.fn((callback: PerformanceObserverCallback) => {
        const observer = {
            callback,
            options: {} as PerformanceObserverInit,
            observe: vi.fn((options: PerformanceObserverInit) => {
                observer.options = options;
                observers.push({ callback, options });
            }),
            disconnect: vi.fn(() => {
                const idx = observers.findIndex((o) => o.callback === callback);
                if (idx !== -1) observers.splice(idx, 1);
            }),
            takeRecords: vi.fn(() => []),
        };
        return observer;
    });

    // Replace global PerformanceObserver
    vi.stubGlobal('PerformanceObserver', MockPerformanceObserver);

    return {
        MockPerformanceObserver,
        /**
         * Simulate performance entries being observed
         */
        triggerEntries: (
            entries: Partial<
                PerformanceEntry & {
                    attribution?: {
                        name?: string;
                        containerType?: string;
                        containerSrc?: string;
                        containerId?: string;
                        containerName?: string;
                    }[];
                }
            >[],
            type?: string,
        ) => {
            const list: PerformanceObserverEntryList = {
                getEntries: () => entries as PerformanceEntry[],
                getEntriesByType: (t: string) => entries.filter((e) => e.entryType === t) as PerformanceEntry[],
                getEntriesByName: (name: string) => entries.filter((e) => e.name === name) as PerformanceEntry[],
            };

            for (const { callback, options } of observers) {
                if (!type || options.type === type || options.entryTypes?.includes(type)) {
                    callback(list, {} as PerformanceObserver);
                }
            }
        },
        getObservers: () => observers,
    };
};

/**
 * Create mock fetch function
 */
export const createFetchMock = () => {
    const mockFetch = vi.fn();
    vi.stubGlobal('fetch', mockFetch);
    return mockFetch;
};

/**
 * Mock XMLHttpRequest
 */
export const createXHRMock = () => {
    const instances: MockXHR[] = [];

    class MockXHR {
        public onreadystatechange: (() => void) | null = null;
        public onload: (() => void) | null = null;
        public onerror: (() => void) | null = null;
        public readyState = 0;
        public status = 0;
        public responseText = '';
        public responseURL = '';

        private _url = '';

        public open = vi.fn((_method: string, url: string) => {
            this._url = url;
            this.readyState = 1;
        });

        public send = vi.fn(() => {
            this.readyState = 2;
        });

        public setRequestHeader = vi.fn();
        public getAllResponseHeaders = vi.fn(() => '');
        public getResponseHeader = vi.fn(() => null);
        public abort = vi.fn();

        constructor() {
            instances.push(this);
        }

        // Test helpers
        simulateSuccess(status: number, responseText: string) {
            this.status = status;
            this.responseText = responseText;
            this.responseURL = this._url;
            this.readyState = 4;
            this.onreadystatechange?.();
            this.onload?.();
        }

        simulateError() {
            this.status = 0;
            this.readyState = 4;
            this.onreadystatechange?.();
            this.onerror?.();
        }
    }

    vi.stubGlobal('XMLHttpRequest', MockXHR);

    return {
        MockXHR,
        getInstances: () => instances,
        getLastInstance: () => instances[instances.length - 1],
        clear: () => {
            instances.length = 0;
        },
    };
};

/**
 * Helper to simulate DOM events
 */
export const simulateEvent = <T extends Event>(target: EventTarget, eventType: string, eventInit?: Partial<T>) => {
    const event = new Event(eventType, { bubbles: true, cancelable: true });
    Object.assign(event, eventInit);
    target.dispatchEvent(event);
    return event;
};

/**
 * Helper to simulate pointer events (for user interaction tests)
 */
export const simulatePointerEvent = (
    target: Element,
    type: 'pointerdown' | 'pointerup' | 'click',
    options?: Partial<PointerEvent>,
) => {
    const event = new PointerEvent(type, {
        bubbles: true,
        cancelable: true,
        clientX: 100,
        clientY: 100,
        ...options,
    });
    target.dispatchEvent(event);
    return event;
};

/**
 * Wait for next tick (useful for async operations)
 */
export const nextTick = () => new Promise((resolve) => setTimeout(resolve, 0));

/**
 * Wait for a specific number of milliseconds
 */
export const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Create a mock for console methods that can be restored
 */
export const createConsoleMock = () => {
    const originalConsole = { ...console };
    const mocks = {
        log: vi.spyOn(console, 'log').mockImplementation(() => {}),
        warn: vi.spyOn(console, 'warn').mockImplementation(() => {}),
        error: vi.spyOn(console, 'error').mockImplementation(() => {}),
        info: vi.spyOn(console, 'info').mockImplementation(() => {}),
        debug: vi.spyOn(console, 'debug').mockImplementation(() => {}),
    };

    return {
        mocks,
        restore: () => {
            Object.assign(console, originalConsole);
        },
    };
};
