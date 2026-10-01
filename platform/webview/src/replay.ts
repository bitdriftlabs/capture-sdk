// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { createMessage, logWithMaxLength, onNativeMessage } from './bridge';
import { makeSafe, safeCall } from './safe-call';

/** Mirrors the replay view types of the Electron SDK (`capture-es`) and native `ReplayType`. */
export const ReplayElementType = {
    Label: 0,
    Button: 1,
    TextInput: 2,
    Image: 3,
    View: 4,
    TransparentView: 10,
    WebView: 12,
} as const;

type ReplayElementTypeValue = (typeof ReplayElementType)[keyof typeof ReplayElementType];

const TIME_BUDGET_MS = 8;
const IDLE_TIMEOUT_MS = 500;
const MAX_VISITED_NODES = 5000;
const MAX_ELEMENTS = 1500;
const MAX_MESSAGE_LENGTH = 65_536;

interface Viewport {
    width: number;
    height: number;
}

const matchByRole = (element: Element, roles: string[]): boolean => {
    const role = element.getAttribute('role');
    return role !== null && roles.includes(role);
};

const matchByTagName = (element: Element, tagNames: string[]): boolean =>
    tagNames.includes(element.tagName.toLowerCase());

const backgroundAlpha = (color: string): number => {
    if (color === 'transparent') return 0;
    const match = color.match(/^rgba?\(([^)]+)\)$/);
    if (!match) return 1;
    const alpha = match[1].split(/[\s,/]+/).filter(Boolean)[3];
    return alpha === undefined ? 1 : Number.parseFloat(alpha);
};

/** Same classification rules as the Electron SDK's `captureScreen`. */
const getTypeFromElement = (element: Element, style: CSSStyleDeclaration): ReplayElementTypeValue | null => {
    if (
        style.display === 'none' ||
        style.visibility === 'hidden' ||
        style.opacity === '0' ||
        element.getAttribute('aria-hidden') === 'true'
    ) {
        return null;
    }

    if (
        matchByRole(element, ['heading', 'label', 'paragraph']) ||
        matchByTagName(element, ['span', 'p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6'])
    ) {
        return ReplayElementType.Label;
    }

    if (matchByRole(element, ['image']) || matchByTagName(element, ['img', 'svg', 'picture'])) {
        return ReplayElementType.Image;
    }

    if (matchByRole(element, ['button', 'link']) || matchByTagName(element, ['button', 'a'])) {
        return ReplayElementType.Button;
    }

    if (matchByTagName(element, ['textarea', 'input'])) {
        return ReplayElementType.TextInput;
    }

    if (matchByTagName(element, ['iframe'])) {
        return ReplayElementType.WebView;
    }

    const alpha = backgroundAlpha(style.backgroundColor);
    if (alpha > 0 && alpha < 1) {
        return ReplayElementType.TransparentView;
    }

    return ReplayElementType.View;
};

const zIndexFromElement = (element: Element): number => Number.parseInt(getComputedStyle(element).zIndex, 10) || 0;

const intersectsViewport = (rect: DOMRect, viewport: Viewport): boolean =>
    rect.width > 0 &&
    rect.height > 0 &&
    rect.right > 0 &&
    rect.bottom > 0 &&
    rect.left < viewport.width &&
    rect.top < viewport.height;

/**
 * Walks the DOM the same way the Electron SDK's `captureScreen` does and returns a flat
 * `[type, x, y, width, height, ...]` array in CSS pixels relative to the layout viewport, ordered
 * back to front. Unlike Electron it skips elements outside the viewport, renders `data-redacted`
 * elements as an opaque block, and stops at `deadline` (`performance.now()` time) or once the
 * node or element caps are reached, returning what it collected so far.
 */
export const collectReplayElements = (
    root: Element,
    viewport: Viewport,
    deadline: number = Number.POSITIVE_INFINITY,
): number[] => {
    const elementsByKey = new Map<string, number[]>();
    let visited = 0;

    const upsertElement = (type: ReplayElementTypeValue, rect: DOMRect): void => {
        const element = [type, Math.round(rect.x), Math.round(rect.y), Math.round(rect.width), Math.round(rect.height)];
        const coordinates = element.slice(1).join('~');
        const viewKey = `${ReplayElementType.View}~${coordinates}`;

        // A non-view element replaces a container view with the same bounds to reduce clutter.
        if (type !== ReplayElementType.View && elementsByKey.has(viewKey)) {
            elementsByKey.delete(viewKey);
        }
        elementsByKey.set(`${type}~${coordinates}`, element);
    };

    const traverse = (element: Element): void => {
        if (visited++ >= MAX_VISITED_NODES || elementsByKey.size >= MAX_ELEMENTS) return;
        if (performance.now() > deadline) return;

        const style = window.getComputedStyle(element);
        if (style.display === 'none') return;

        const rect = element.getBoundingClientRect();
        const isOnScreen = intersectsViewport(rect, viewport);

        if (element.hasAttribute('data-redacted')) {
            if (isOnScreen && style.visibility !== 'hidden') upsertElement(ReplayElementType.View, rect);
            return;
        }

        const type = getTypeFromElement(element, style);
        if (type !== null && isOnScreen) upsertElement(type, rect);

        if (matchByTagName(element, ['svg'])) return;

        // Sorting per parent keeps DOM position as a stacking factor among siblings.
        const children = Array.from(element.children).sort((a, b) => zIndexFromElement(a) - zIndexFromElement(b));
        for (const child of children) {
            traverse(child);
        }
    };

    traverse(root);
    return Array.from(elementsByKey.values()).flat();
};

const runWhenIdle = (callback: () => void): void => {
    if (typeof window.requestIdleCallback === 'function') {
        window.requestIdleCallback(callback, { timeout: IDLE_TIMEOUT_MS });
    } else {
        setTimeout(callback, 0);
    }
};

const captureSnapshot = (): void => {
    safeCall(() => {
        const viewport = { width: window.innerWidth, height: window.innerHeight };
        if (viewport.width <= 0 || viewport.height <= 0 || !document.body) return;
        const elements = collectReplayElements(document.body, viewport, performance.now() + TIME_BUDGET_MS);
        logWithMaxLength(
            createMessage({
                type: 'replaySnapshot',
                viewportWidth: viewport.width,
                viewportHeight: viewport.height,
                elements,
            }),
            MAX_MESSAGE_LENGTH,
        );
    });
};

/**
 * Answers native snapshot requests, which native sends only while it is capturing a replay
 * frame. The page is walked only when it may have changed since the last answered request.
 */
export const initReplayCapture = (): void => {
    if (window.top !== window) return;

    let isDirty = true;
    const markDirty = (): void => {
        isDirty = true;
    };

    const isListening = onNativeMessage(
        makeSafe((data: string) => {
            const request = JSON.parse(data) as { type?: string; force?: boolean };
            if (request.type !== 'replaySnapshotRequest' || (!isDirty && !request.force)) return;
            isDirty = false;
            runWhenIdle(captureSnapshot);
        }),
    );
    if (!isListening) return;

    const observe = (): void => {
        new MutationObserver(markDirty).observe(document.documentElement, {
            subtree: true,
            childList: true,
            attributes: true,
            characterData: true,
        });
    };

    if (document.documentElement) {
        observe();
    } else {
        document.addEventListener('DOMContentLoaded', makeSafe(observe), { once: true });
    }

    window.addEventListener('scroll', markDirty, { capture: true, passive: true });
    window.addEventListener('resize', markDirty, { passive: true });
    window.addEventListener('pageshow', markDirty);
    document.addEventListener('input', markDirty, { capture: true, passive: true });
    document.addEventListener('change', markDirty, { capture: true, passive: true });
};
