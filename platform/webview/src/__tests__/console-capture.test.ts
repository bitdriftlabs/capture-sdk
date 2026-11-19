// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createMessageCollector } from './mocks';

describe('console capture', () => {
    // Store original console methods to restore after each test
    const originalConsole = {
        log: console.log,
        warn: console.warn,
        error: console.error,
        info: console.info,
        debug: console.debug,
        trace: console.trace,
    };

    beforeEach(() => {
        vi.resetModules();
        // Restore original console methods before each test
        console.log = originalConsole.log;
        console.warn = originalConsole.warn;
        console.error = originalConsole.error;
        console.info = originalConsole.info;
        console.debug = originalConsole.debug;
        console.trace = originalConsole.trace;
    });

    afterEach(() => {
        // Restore original console methods after each test
        console.log = originalConsole.log;
        console.warn = originalConsole.warn;
        console.error = originalConsole.error;
        console.info = originalConsole.info;
        console.debug = originalConsole.debug;
        console.trace = originalConsole.trace;
    });

    describe('initConsoleCapture', () => {
        it('should capture console.log messages', async () => {
            const collector = createMessageCollector();
            const { initConsoleCapture } = await import('../console-capture');

            initConsoleCapture();
            console.log('test message');

            const messages = collector.getMessagesByType('console');
            expect(messages.length).toBe(1);
            expect(messages[0].level).toBe('log');
            expect(messages[0].message).toBe('test message');
        });

        it('should capture all console levels', async () => {
            const collector = createMessageCollector();
            const { initConsoleCapture } = await import('../console-capture');

            initConsoleCapture();

            console.log('log message');
            console.warn('warn message');
            console.error('error message');
            console.info('info message');
            console.debug('debug message');
            console.trace('trace message');

            // Note: In Node.js, console.trace internally delegates to console.error,
            // which is also intercepted, so we may see an extra captured message.
            const messages = collector.getMessagesByType('console');
            const levels = new Set(messages.map((m) => m.level));
            expect(levels).toContain('log');
            expect(levels).toContain('warn');
            expect(levels).toContain('error');
            expect(levels).toContain('info');
            expect(levels).toContain('debug');
            expect(levels).toContain('trace');
        });

        it('should capture additional arguments', async () => {
            const collector = createMessageCollector();
            const { initConsoleCapture } = await import('../console-capture');

            initConsoleCapture();
            console.log('message', 'arg1', 42, { key: 'value' });

            const messages = collector.getMessagesByType('console');
            expect(messages.length).toBe(1);
            expect(messages[0].message).toBe('message');
            expect(messages[0].args).toEqual(['arg1', '42', '{"key":"value"}']);
        });

        it('should stringify various argument types', async () => {
            const collector = createMessageCollector();
            const { initConsoleCapture } = await import('../console-capture');

            initConsoleCapture();

            console.log(null);
            console.log(undefined);
            console.log(true);
            console.log(123);
            console.log(new Error('test error'));

            const messages = collector.getMessagesByType('console');
            expect(messages.map((m) => m.message)).toEqual(['null', 'undefined', 'true', '123', 'Error: test error']);
        });

        it('should not capture bridge messages', async () => {
            const collector = createMessageCollector();
            const { initConsoleCapture } = await import('../console-capture');
            const { createMessage } = await import('../bridge');

            initConsoleCapture();

            // Log a bridge message directly - should be filtered
            const bridgeMessage = createMessage({
                type: 'customLog',
                level: 'info',
                message: 'bridge message',
            });
            console.log(bridgeMessage);

            // Log a regular message
            console.log('regular message');

            const messages = collector.getMessagesByType('console');
            // Should only have the regular message
            expect(messages.length).toBe(1);
            expect(messages[0].message).toBe('regular message');
        });

        it('should still call original console method', async () => {
            const spy = vi.fn();
            console.log = spy;

            createMessageCollector();
            const { initConsoleCapture } = await import('../console-capture');

            initConsoleCapture();
            console.log('test');

            // The spy should have been called with the message
            expect(spy).toHaveBeenCalledWith('test');
        });

        it('should handle objects that throw on stringify', async () => {
            const collector = createMessageCollector();
            const { initConsoleCapture } = await import('../console-capture');

            initConsoleCapture();

            // Create an object that throws when stringified
            const throwingObj = {
                toJSON() {
                    throw new Error('Cannot stringify');
                },
            };

            // Should not throw
            expect(() => console.log(throwingObj)).not.toThrow();

            const messages = collector.getMessagesByType('console');
            expect(messages.length).toBe(1);
            // Falls back to String(obj)
            expect(messages[0].message).toBe('[object Object]');
        });

        it('should handle circular references in console args', async () => {
            const collector = createMessageCollector();
            const { initConsoleCapture } = await import('../console-capture');

            initConsoleCapture();

            const circular: Record<string, unknown> = { a: 1 };
            circular.self = circular;

            expect(() => console.log(circular)).not.toThrow();

            const messages = collector.getMessagesByType('console');
            expect(messages.length).toBe(1);
            expect(messages[0].message).toContain('[Circular]');
        });

        it('should truncate very long string arguments', async () => {
            const collector = createMessageCollector();
            const { initConsoleCapture } = await import('../console-capture');

            initConsoleCapture();

            const hugeString = 'x'.repeat(100_000);
            console.log(hugeString);

            const messages = collector.getMessagesByType('console');
            expect(messages.length).toBe(1);
            expect(messages[0].message.length).toBeLessThan(100_000);
            expect(messages[0].message).toContain('...<truncated>');
        });

        it('should cap extra arguments to 10', async () => {
            const collector = createMessageCollector();
            const { initConsoleCapture } = await import('../console-capture');

            initConsoleCapture();

            // Log 15 extra args (1 message + 15 extras = 16 total)
            console.log('msg', 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);

            const messages = collector.getMessagesByType('console');
            expect(messages.length).toBe(1);
            expect(messages[0].message).toBe('msg');
            // Should cap at 10 extra args
            expect(messages[0].args?.length).toBeLessThanOrEqual(10);
        });

        it('should handle deeply nested objects without crashing', async () => {
            const collector = createMessageCollector();
            const { initConsoleCapture } = await import('../console-capture');

            initConsoleCapture();

            // Build deeply nested object
            let obj: Record<string, unknown> = { value: 'leaf' };
            for (let i = 0; i < 50; i++) {
                obj = { child: obj };
            }

            expect(() => console.log(obj)).not.toThrow();

            const messages = collector.getMessagesByType('console');
            expect(messages.length).toBe(1);
            expect(messages[0].message).toContain('[MaxDepth]');
        });
    });
});
