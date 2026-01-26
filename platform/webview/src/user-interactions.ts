// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { log, createMessage } from './bridge';
import { safeCall, makeSafe } from './safe-call';

/** Clickable element tags */
const CLICKABLE_TAGS = new Set(['a', 'button', 'input', 'select', 'textarea', 'label', 'summary']);

/** Input types that are clickable */
const CLICKABLE_INPUT_TYPES = new Set(['button', 'submit', 'reset', 'checkbox', 'radio', 'file']);

/** Rage click detection settings */
const RAGE_CLICK_THRESHOLD = 3; // Number of clicks to trigger rage click
const RAGE_CLICK_TIME_WINDOW_MS = 1000; // Time window for rage clicks
const RAGE_CLICK_DISTANCE_PX = 100; // Max distance between clicks to count as rage
const RAGE_CLICK_DEBOUNCE_MS = 500; // Wait time after last click before logging

/** Track clicks for rage detection */
interface ClickRecord {
    x: number;
    y: number;
    timestamp: number;
    element: Element;
}

let recentClicks: ClickRecord[] = [];
let rageClickDebounceTimer: number | null = null;
let pendingRageClick: {
    element: Element;
    clicks: ClickRecord[];
} | null = null;

/**
 * Flush any pending rage click immediately, logging it and clearing state.
 */
const flushPendingRageClick = (): void => {
    if (rageClickDebounceTimer !== null) {
        clearTimeout(rageClickDebounceTimer);
        rageClickDebounceTimer = null;
    }

    if (pendingRageClick) {
        const timeSpan =
            pendingRageClick.clicks[pendingRageClick.clicks.length - 1].timestamp -
            pendingRageClick.clicks[0].timestamp;

        logUserInteraction(pendingRageClick.element, 'rageClick', false, pendingRageClick.clicks.length, timeSpan);

        pendingRageClick = null;
        recentClicks = [];
    }
};

/**
 * Initialize user interaction monitoring.
 * Tracks taps/clicks on clickable elements and rage clicks on non-clickable elements.
 * Uses pointerdown for reliable capture on both mobile and desktop.
 */
export const initUserInteractionMonitoring = (): void => {
    // Use pointerdown for reliable mobile/desktop support
    // pointerdown fires immediately on touch/click without delay
    safeCall(() => {
        document.addEventListener('pointerdown', makeSafe(handlePointerDown), true);
    });
};

/**
 * Check if an element or its ancestors are clickable
 */
const isClickable = (element: Element): boolean => {
    return (
        safeCall(() => {
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
        }) ?? false
    );
};

/**
 * Handle pointer down events (touch/mouse)
 */
const handlePointerDown = (event: PointerEvent): void => {
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
            const distance = Math.sqrt((click.x - event.clientX) ** 2 + (click.y - event.clientY) ** 2);
            return distance < RAGE_CLICK_DISTANCE_PX;
        });

        if (nearbyClicks.length >= RAGE_CLICK_THRESHOLD) {
            // We've detected a rage click - start/update the debounce timer
            // This allows us to capture the full rage sequence
            if (pendingRageClick && pendingRageClick.element === target) {
                // Update existing rage click with new clicks
                pendingRageClick.clicks = nearbyClicks;
            } else {
                // New rage click sequence on a different element
                // Flush the pending rage click first if one exists
                if (pendingRageClick) {
                    flushPendingRageClick();
                }

                pendingRageClick = {
                    element: target,
                    clicks: nearbyClicks,
                };
            }

            // Clear existing timer and set a new one
            if (rageClickDebounceTimer !== null) {
                clearTimeout(rageClickDebounceTimer);
            }

            // Log the rage click after the debounce period
            rageClickDebounceTimer = setTimeout(() => {
                flushPendingRageClick();
            }, RAGE_CLICK_DEBOUNCE_MS) as unknown as number;
        }
    }
};

/**
 * Log a user interaction event
 */
const logUserInteraction = (
    element: Element,
    interactionType: 'click' | 'rageClick',
    isClickable: boolean,
    clickCount?: number,
    actualTimeWindowMs?: number,
): void => {
    safeCall(() => {
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
            textContent = text.length > 50 ? `${text.slice(0, 50)}...` : text || undefined;
        }

        const message = createMessage({
            type: 'userInteraction',
            interactionType,
            tagName,
            elementId,
            className: className?.slice(0, 100),
            textContent,
            isClickable,
            clickCount: interactionType === 'rageClick' ? clickCount : undefined,
            timeWindowMs: interactionType === 'rageClick' ? RAGE_CLICK_TIME_WINDOW_MS : undefined,
            duration: interactionType === 'rageClick' ? actualTimeWindowMs : undefined,
        });
        log(message);
    });
};
