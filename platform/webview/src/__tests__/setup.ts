// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { vi, beforeEach, afterEach } from 'vitest';

// Polyfill PointerEvent for jsdom (not available in jsdom)
class PointerEventPolyfill extends MouseEvent {
    public readonly pointerId: number;
    public readonly width: number;
    public readonly height: number;
    public readonly pressure: number;
    public readonly tangentialPressure: number;
    public readonly tiltX: number;
    public readonly tiltY: number;
    public readonly twist: number;
    public readonly pointerType: string;
    public readonly isPrimary: boolean;

    constructor(type: string, params: PointerEventInit = {}) {
        super(type, params);
        this.pointerId = params.pointerId ?? 0;
        this.width = params.width ?? 1;
        this.height = params.height ?? 1;
        this.pressure = params.pressure ?? 0;
        this.tangentialPressure = params.tangentialPressure ?? 0;
        this.tiltX = params.tiltX ?? 0;
        this.tiltY = params.tiltY ?? 0;
        this.twist = params.twist ?? 0;
        this.pointerType = params.pointerType ?? '';
        this.isPrimary = params.isPrimary ?? false;
    }
}

// Polyfill PromiseRejectionEvent for jsdom
class PromiseRejectionEventPolyfill extends Event {
    public readonly promise: Promise<unknown>;
    public readonly reason: unknown;

    constructor(type: string, eventInitDict: PromiseRejectionEventInit) {
        super(type, eventInitDict);
        this.promise = eventInitDict.promise;
        this.reason = eventInitDict.reason;
    }
}

// Install polyfills
if (typeof globalThis.PointerEvent === 'undefined') {
    (globalThis as unknown as { PointerEvent: typeof PointerEventPolyfill }).PointerEvent = PointerEventPolyfill;
}

if (typeof globalThis.PromiseRejectionEvent === 'undefined') {
    (globalThis as unknown as { PromiseRejectionEvent: typeof PromiseRejectionEventPolyfill }).PromiseRejectionEvent =
        PromiseRejectionEventPolyfill;
}

// Reset window state before each test
beforeEach(() => {
    // Reset bitdrift global
    delete (window as { __bitdriftBridgeInitialized?: boolean }).__bitdriftBridgeInitialized;
    delete (window as { bitdrift?: unknown }).bitdrift;

    // Reset BitdriftLogger mocks
    delete (window as { BitdriftLogger?: unknown }).BitdriftLogger;
    delete (window as { webkit?: unknown }).webkit;

    // Clear all mocks
    vi.clearAllMocks();
});

afterEach(() => {
    vi.restoreAllMocks();
});
