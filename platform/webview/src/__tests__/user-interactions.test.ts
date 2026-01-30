// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createMessageCollector, simulatePointerEvent } from './mocks';

describe('user interactions', () => {
    beforeEach(() => {
        vi.resetModules();
        document.body.innerHTML = '';
    });

    describe('initUserInteractionMonitoring', () => {
        it('should capture clicks on clickable elements', async () => {
            const collector = createMessageCollector();
            const { initUserInteractionMonitoring } = await import('../user-interactions');

            document.body.innerHTML = '<button id="test-btn">Click me</button>';
            const button = document.getElementById('test-btn');

            expect(button).not.toBeNull();

            initUserInteractionMonitoring();

            // Clear any messages that might have been captured during init
            collector.clear();

            simulatePointerEvent(button as HTMLButtonElement, 'pointerdown');

            const messages = collector.getMessagesByType('userInteraction');
            expect(messages.length).toBeGreaterThanOrEqual(1);

            // Check the last message (to avoid issues with lingering listeners from previous tests)
            const lastMsg = messages[messages.length - 1];
            expect(lastMsg.interactionType).toBe('click');
            expect(lastMsg.tagName).toBe('button');
            expect(lastMsg.elementId).toBe('test-btn');
            expect(lastMsg.isClickable).toBe(true);
        });

        it('should detect clickable elements by role', async () => {
            const collector = createMessageCollector();
            const { initUserInteractionMonitoring } = await import('../user-interactions');

            document.body.innerHTML = '<div role="button" id="role-btn">Click me</div>';
            const div = document.getElementById('role-btn');

            expect(div).not.toBeNull();

            initUserInteractionMonitoring();
            collector.clear();

            simulatePointerEvent(div as HTMLDivElement, 'pointerdown');

            const messages = collector.getMessagesByType('userInteraction');
            expect(messages.length).toBeGreaterThanOrEqual(1);
            expect(messages[messages.length - 1].isClickable).toBe(true);
        });

        it('should capture text content truncated to 50 chars', async () => {
            const collector = createMessageCollector();
            const { initUserInteractionMonitoring } = await import('../user-interactions');

            const longText =
                'This is a very long button text that should be truncated because it exceeds fifty characters';
            document.body.innerHTML = `<button id="long-btn">${longText}</button>`;
            const button = document.getElementById('long-btn');

            expect(button).not.toBeNull();

            initUserInteractionMonitoring();
            collector.clear();

            simulatePointerEvent(button as HTMLButtonElement, 'pointerdown');

            const messages = collector.getMessagesByType('userInteraction');
            expect(messages.length).toBeGreaterThanOrEqual(1);
            const lastMsg = messages[messages.length - 1];
            expect(lastMsg.textContent?.length).toBeLessThanOrEqual(53); // 50 + '...'
            expect(lastMsg.textContent).toContain('...');
        });

        it('should handle clicks on elements without id or class', async () => {
            const collector = createMessageCollector();
            const { initUserInteractionMonitoring } = await import('../user-interactions');

            document.body.innerHTML = '<button>Plain button</button>';
            const button = document.querySelector('button');

            expect(button).not.toBeNull();

            initUserInteractionMonitoring();
            collector.clear();

            simulatePointerEvent(button as HTMLButtonElement, 'pointerdown');

            const messages = collector.getMessagesByType('userInteraction');
            expect(messages.length).toBeGreaterThanOrEqual(1);
            const lastMsg = messages[messages.length - 1];
            expect(lastMsg.elementId).toBeUndefined();
            expect(lastMsg.className).toBeUndefined();
        });

        it('should not crash on null event target', async () => {
            createMessageCollector();
            const { initUserInteractionMonitoring } = await import('../user-interactions');

            initUserInteractionMonitoring();

            // Create event with null target - should not crash
            const event = new PointerEvent('pointerdown', { bubbles: true });
            expect(() => document.dispatchEvent(event)).not.toThrow();
        });

        it('should detect anchor elements as clickable', async () => {
            const collector = createMessageCollector();
            const { initUserInteractionMonitoring } = await import('../user-interactions');

            document.body.innerHTML = '<a href="/test" id="test-link">Link</a>';
            const link = document.getElementById('test-link');

            initUserInteractionMonitoring();
            collector.clear();

            expect(link).not.toBeNull();

            simulatePointerEvent(link as HTMLAnchorElement, 'pointerdown');

            const messages = collector.getMessagesByType('userInteraction');
            expect(messages.length).toBeGreaterThanOrEqual(1);
            const lastMsg = messages[messages.length - 1];
            expect(lastMsg.tagName).toBe('a');
            expect(lastMsg.isClickable).toBe(true);
        });

        it('should redact text content when data-redacted attribute is present', async () => {
            const collector = createMessageCollector();
            const { initUserInteractionMonitoring } = await import('../user-interactions');

            document.body.innerHTML = '<button id="redacted-btn" data-redacted>Sensitive Info</button>';
            const button = document.getElementById('redacted-btn');

            expect(button).not.toBeNull();

            initUserInteractionMonitoring();
            collector.clear();

            simulatePointerEvent(button as HTMLButtonElement, 'pointerdown');

            const messages = collector.getMessagesByType('userInteraction');
            expect(messages.length).toBeGreaterThanOrEqual(1);
            const lastMsg = messages[messages.length - 1];
            expect(lastMsg.textContent).toBe('<redacted>');
            expect(lastMsg.tagName).toBe('button');
            expect(lastMsg.elementId).toBe('redacted-btn');
        });

        it('should not redact text content when data-redacted attribute is not present', async () => {
            const collector = createMessageCollector();
            const { initUserInteractionMonitoring } = await import('../user-interactions');

            document.body.innerHTML = '<button id="normal-btn">Public Info</button>';
            const button = document.getElementById('normal-btn');

            expect(button).not.toBeNull();

            initUserInteractionMonitoring();
            collector.clear();

            simulatePointerEvent(button as HTMLButtonElement, 'pointerdown');

            const messages = collector.getMessagesByType('userInteraction');
            expect(messages.length).toBeGreaterThanOrEqual(1);
            const lastMsg = messages[messages.length - 1];
            expect(lastMsg.textContent).toBe('Public Info');
            expect(lastMsg.tagName).toBe('button');
        });

        it('should redact text content on non-clickable elements with data-redacted', async () => {
            vi.useFakeTimers();
            const collector = createMessageCollector();
            const { initUserInteractionMonitoring } = await import('../user-interactions');

            document.body.innerHTML = '<div id="redacted-div" data-redacted>Secret Content</div>';
            const div = document.getElementById('redacted-div');

            initUserInteractionMonitoring();
            collector.clear();

            expect(div).not.toBeNull();

            // Simulate rage clicks to trigger capture on non-clickable element
            for (let i = 0; i < 4; i++) {
                simulatePointerEvent(div as HTMLDivElement, 'pointerdown', { clientX: 100, clientY: 100 });
            }

            // Wait for debounce
            vi.advanceTimersByTime(600);

            const messages = collector.getMessagesByType('userInteraction');
            const rageClick = messages.find((m) => m.interactionType === 'rageClick');
            expect(rageClick).toBeDefined();
            expect(rageClick?.textContent).toBe('<redacted>');

            vi.useRealTimers();
        });
    });

    describe('rage click detection', () => {
        it('should detect rage clicks on non-clickable elements', async () => {
            vi.useFakeTimers();
            const collector = createMessageCollector();
            const { initUserInteractionMonitoring } = await import('../user-interactions');

            document.body.innerHTML = '<div id="frustrating">Not clickable</div>';
            const div = document.getElementById('frustrating');

            initUserInteractionMonitoring();
            collector.clear();

            expect(div).not.toBeNull();

            // Simulate 3+ rapid clicks in close proximity
            for (let i = 0; i < 4; i++) {
                simulatePointerEvent(div as HTMLDivElement, 'pointerdown', { clientX: 100, clientY: 100 });
            }

            // Wait for debounce
            vi.advanceTimersByTime(600);

            const messages = collector.getMessagesByType('userInteraction');
            const rageClick = messages.find((m) => m.interactionType === 'rageClick');
            expect(rageClick).toBeDefined();
            expect(rageClick?.clickCount).toBeGreaterThanOrEqual(3);

            vi.useRealTimers();
        });
    });
});
