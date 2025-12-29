// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { log, createMessage } from './bridge';
import { getCurrentPageSpanId } from './page-view';
import type { UserInteractionMessage } from './types';

/** Clickable element tags */
const CLICKABLE_TAGS = new Set(['a', 'button', 'input', 'select', 'textarea', 'label', 'summary']);

/** Input types that are clickable */
const CLICKABLE_INPUT_TYPES = new Set(['button', 'submit', 'reset', 'checkbox', 'radio', 'file']);

/** Rage click detection settings */
const RAGE_CLICK_THRESHOLD = 3; // Number of clicks to trigger rage click
const RAGE_CLICK_TIME_WINDOW_MS = 1000; // Time window for rage clicks
const RAGE_CLICK_DISTANCE_PX = 100; // Max distance between clicks to count as rage

/** Track clicks for rage detection */
interface ClickRecord {
    x: number;
    y: number;
    timestamp: number;
    element: Element;
}

let recentClicks: ClickRecord[] = [];

/**
 * Initialize user interaction monitoring.
 * Tracks taps/clicks on clickable elements and rage clicks on non-clickable elements.
 * Uses pointerdown for reliable capture on both mobile and desktop.
 */
export function initUserInteractionMonitoring(): void {
    // Use pointerdown for reliable mobile/desktop support
    // pointerdown fires immediately on touch/click without delay
    document.addEventListener('pointerdown', handlePointerDown, true);
}

/**
 * Check if an element or its ancestors are clickable
 */
function isClickable(element: Element): boolean {
    let current: Element | null = element;

    while (current) {
        const tagName = current.tagName.toLowerCase();

        // Check tag name
        if (CLICKABLE_TAGS.has(tagName)) {
            // For inputs, check the type
            if (tagName === 'input') {
                const type = (current as HTMLInputElement).type.toLowerCase();
                return CLICKABLE_INPUT_TYPES.has(type);
            }
            return true;
        }

        // Check for role attribute
        const role = current.getAttribute('role');
        if (role === 'button' || role === 'link' || role === 'menuitem') {
            return true;
        }

        // Check for onclick handler or tabindex
        if (current.hasAttribute('onclick') || current.hasAttribute('tabindex')) {
            return true;
        }

        // Check for cursor pointer style
        const style = window.getComputedStyle(current);
        if (style.cursor === 'pointer') {
            return true;
        }

        current = current.parentElement;
    }

    return false;
}

/**
 * Handle pointer down events (touch/mouse)
 */
function handlePointerDown(event: PointerEvent): void {
    const target = event.target as Element | null;
    if (!target) {
        return;
    }

    const clickable = isClickable(target);
    const now = Date.now();

    if (clickable) {
        // Track taps/clicks on clickable elements
        logUserInteraction(target, 'click', true);
    } else {
        // For non-clickable elements, check for rage clicks
        const clickRecord: ClickRecord = {
            x: event.clientX,
            y: event.clientY,
            timestamp: now,
            element: target,
        };

        // Add to recent clicks and clean up old ones
        recentClicks.push(clickRecord);
        recentClicks = recentClicks.filter((click) => now - click.timestamp < RAGE_CLICK_TIME_WINDOW_MS);

        // Check for rage click pattern
        const nearbyClicks = recentClicks.filter((click) => {
            const distance = Math.sqrt(Math.pow(click.x - event.clientX, 2) + Math.pow(click.y - event.clientY, 2));
            return distance < RAGE_CLICK_DISTANCE_PX;
        });

        if (nearbyClicks.length >= RAGE_CLICK_THRESHOLD) {
            // Log rage click
            logUserInteraction(target, 'rageClick', false, nearbyClicks.length);
            // Clear recent clicks to avoid duplicate rage click events
            recentClicks = [];
        }
    }
}

/**
 * Log a user interaction event
 */
function logUserInteraction(
    element: Element,
    interactionType: 'click' | 'rageClick',
    isClickable: boolean,
    clickCount?: number,
): void {
    const tagName = element.tagName.toLowerCase();
    const elementId = element.id || undefined;
    const className = element.className
        ? typeof element.className === 'string'
            ? element.className
            : (element.className as DOMTokenList).toString()
        : undefined;

    // Get text content, truncated
    let textContent: string | undefined;
    if (element.textContent) {
        const text = element.textContent.trim().replace(/\s+/g, ' ');
        textContent = text.length > 50 ? text.slice(0, 50) + '...' : text || undefined;
    }

    const message = createMessage<UserInteractionMessage>({
        type: 'userInteraction',
        interactionType,
        tagName,
        elementId,
        className: className?.slice(0, 100),
        textContent,
        isClickable,
        clickCount: interactionType === 'rageClick' ? clickCount : undefined,
        timeWindowMs: interactionType === 'rageClick' ? RAGE_CLICK_TIME_WINDOW_MS : undefined,
        parentSpanId: getCurrentPageSpanId() ?? undefined,
    });
    log(message);
}
