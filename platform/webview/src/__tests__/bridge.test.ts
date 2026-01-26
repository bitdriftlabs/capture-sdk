// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createAndroidBridgeMock, createIOSBridgeMock } from './mocks';
import type { SerializableLogFields } from '../types';

describe('bridge', () => {
    beforeEach(() => {
        // Reset modules to get fresh state
        vi.resetModules();
    });

    describe('platform detection', () => {
        it('should detect Android platform', async () => {
            const androidMock = createAndroidBridgeMock();
            const { initBridge, log, createMessage } = await import('../bridge');

            initBridge();
            log(
                createMessage({
                    type: 'bridgeReady',
                    url: 'https://example.com',
                    instrumentationConfig: undefined,
                }),
            );

            expect(androidMock.log).toHaveBeenCalled();
            const call = androidMock.log.mock.calls[0][0];
            const parsed = JSON.parse(call);
            expect(parsed.type).toBe('bridgeReady');
        });

        it('should detect iOS platform', async () => {
            const iosMock = createIOSBridgeMock();
            const { initBridge, log, createMessage } = await import('../bridge');

            initBridge();
            log(
                createMessage({
                    type: 'bridgeReady',
                    url: 'https://example.com',
                    instrumentationConfig: undefined,
                }),
            );

            expect(iosMock.postMessage).toHaveBeenCalled();
            const message = iosMock.postMessage.mock.calls[0][0];
            expect(message.type).toBe('bridgeReady');
        });

        it('should handle unknown platform gracefully', async () => {
            // No bridge mock set up - should not crash
            const consoleSpy = vi.spyOn(console, 'debug').mockImplementation(() => {});

            const { initBridge, log, createMessage } = await import('../bridge');

            initBridge();
            expect(() =>
                log(
                    createMessage({
                        type: 'bridgeReady',
                        url: 'https://example.com',
                        instrumentationConfig: undefined,
                    }),
                ),
            ).not.toThrow();

            consoleSpy.mockRestore();
        });
    });

    describe('createMessage', () => {
        it('should add required fields to message', async () => {
            const { createMessage } = await import('../bridge');

            const message = createMessage({
                type: 'customLog',
                level: 'info',
                message: 'test',
            });

            expect(message.tag).toBe('bitdrift-webview-sdk');
            expect(message.v).toBe(1);
            expect(message.timestamp).toBeTypeOf('number');
            expect(message.type).toBe('customLog');
        });
    });

    describe('error handling', () => {
        it('should not crash when bridge throws', async () => {
            const androidMock = createAndroidBridgeMock();
            androidMock.log.mockImplementation(() => {
                throw new Error('Bridge error');
            });

            const { initBridge, log, createMessage } = await import('../bridge');
            initBridge();

            // Should not throw despite bridge error
            expect(() =>
                log(
                    createMessage({
                        type: 'bridgeReady',
                        url: 'https://example.com',
                        instrumentationConfig: undefined,
                    }),
                ),
            ).not.toThrow();
        });

        it('should handle circular reference in message gracefully', async () => {
            createAndroidBridgeMock();
            const { initBridge, log, createMessage } = await import('../bridge');
            initBridge();

            // Create a circular reference - JSON.stringify will throw
            const circular: Record<string, unknown> = { a: 1 };
            circular.self = circular;

            // This would normally throw due to circular reference
            // but safeCall should catch it
            expect(() =>
                log(
                    createMessage({
                        type: 'customLog',
                        level: 'info',
                        message: 'test',
                        fields: circular as SerializableLogFields,
                    }),
                ),
            ).not.toThrow();
        });
    });

    describe('isAnyBridgeMessage', () => {
        it('should validate correct message structure', async () => {
            const { isAnyBridgeMessage, createMessage } = await import('../bridge');

            const validMessage = createMessage({
                type: 'customLog',
                level: 'info',
                message: 'test',
            });

            expect(isAnyBridgeMessage(validMessage)).toBe(true);
        });

        it('should reject invalid messages', async () => {
            const { isAnyBridgeMessage } = await import('../bridge');

            expect(isAnyBridgeMessage(null)).toBe(false);
            expect(isAnyBridgeMessage(undefined)).toBe(false);
            expect(isAnyBridgeMessage({})).toBe(false);
            expect(isAnyBridgeMessage({ type: 'test' })).toBe(false);
            expect(isAnyBridgeMessage({ type: 'test', v: 1 })).toBe(false);
            expect(isAnyBridgeMessage({ type: 'test', v: 1, tag: 'wrong' })).toBe(false);
        });
    });
});
