// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { createMessage, logWithMaxLength, onNativeMessage } from './bridge';
import { makeSafe, safeCall } from './safe-call';

export const ReplayElementType = {
    Label: 0,
    Button: 1,
    TextInput: 2,
    Image: 3,
    View: 4,
    BackgroundImage: 5,
    SwitchOn: 6,
    SwitchOff: 7,
} as const;

type ReplayElementTypeValue = (typeof ReplayElementType)[keyof typeof ReplayElementType];

const TIME_BUDGET_MS = 8;
const MAX_VISITED_NODES = 5000;
const MAX_ELEMENTS = 1500;
const MAX_MESSAGE_LENGTH = 65_536;

const IMAGE_TAGS = new Set(['img', 'svg', 'picture', 'video', 'canvas', 'iframe', 'object', 'embed']);
const BUTTON_TAGS = new Set(['button', 'a', 'summary']);
const TEXT_INPUT_TAGS = new Set(['textarea', 'select']);
const BUTTON_INPUT_TYPES = new Set(['button', 'submit', 'reset', 'image', 'file', 'color']);
const SKIPPED_TAGS = new Set(['script', 'style', 'noscript', 'template', 'head', 'meta', 'link', 'title', 'br']);
const BUTTON_ROLES = new Set(['button', 'link', 'menuitem', 'tab']);

interface Viewport {
    width: number;
    height: number;
}

const isTransparent = (color: string): boolean =>
    color === '' || color === 'transparent' || color === 'rgba(0, 0, 0, 0)' || /rgba\(.*,\s*0\)$/.test(color);

const hasVisibleBorder = (style: CSSStyleDeclaration): boolean =>
    (parseFloat(style.borderTopWidth) > 0 && !isTransparent(style.borderTopColor)) ||
    (parseFloat(style.borderLeftWidth) > 0 && !isTransparent(style.borderLeftColor));

const classify = (element: Element, style: CSSStyleDeclaration): ReplayElementTypeValue | null => {
    const tagName = element.tagName.toLowerCase();

    if (tagName === 'input') {
        const input = element as HTMLInputElement;
        const type = input.type.toLowerCase();
        if (type === 'hidden') return null;
        if (type === 'checkbox' || type === 'radio') {
            return input.checked ? ReplayElementType.SwitchOn : ReplayElementType.SwitchOff;
        }
        if (BUTTON_INPUT_TYPES.has(type)) return ReplayElementType.Button;
        return ReplayElementType.TextInput;
    }
    if (TEXT_INPUT_TAGS.has(tagName) || (element as HTMLElement).isContentEditable) {
        return ReplayElementType.TextInput;
    }
    if (IMAGE_TAGS.has(tagName)) return ReplayElementType.Image;

    const role = element.getAttribute('role');
    if (role === 'checkbox' || role === 'switch' || role === 'radio') {
        return element.getAttribute('aria-checked') === 'true'
            ? ReplayElementType.SwitchOn
            : ReplayElementType.SwitchOff;
    }
    if (BUTTON_TAGS.has(tagName) || (role !== null && BUTTON_ROLES.has(role))) {
        return ReplayElementType.Button;
    }
    if (style.backgroundImage.includes('url(')) {
        return ReplayElementType.BackgroundImage;
    }
    if (
        !isTransparent(style.backgroundColor) ||
        hasVisibleBorder(style) ||
        (style.boxShadow !== '' && style.boxShadow !== 'none')
    ) {
        return ReplayElementType.View;
    }
    return null;
};

const isLeafType = (type: ReplayElementTypeValue | null): boolean =>
    type === ReplayElementType.TextInput ||
    type === ReplayElementType.Image ||
    type === ReplayElementType.SwitchOn ||
    type === ReplayElementType.SwitchOff;

const intersectsViewport = (rect: DOMRect, viewport: Viewport): boolean =>
    rect.width > 0 &&
    rect.height > 0 &&
    rect.right > 0 &&
    rect.bottom > 0 &&
    rect.left < viewport.width &&
    rect.top < viewport.height;

/**
 * Walks the visible DOM and returns a flat `[type, x, y, width, height, ...]` array in CSS pixels
 * relative to the layout viewport, ordered back to front. The walk stops at `deadline`
 * (`performance.now()` time) and returns what it collected so far.
 */
export const collectReplayElements = (
    root: Element,
    viewport: Viewport,
    deadline: number = Number.POSITIVE_INFINITY,
): number[] => {
    const elements: number[] = [];
    let visited = 0;

    const push = (type: ReplayElementTypeValue, rect: DOMRect): void => {
        elements.push(
            type,
            Math.round(rect.left),
            Math.round(rect.top),
            Math.round(rect.width),
            Math.round(rect.height),
        );
    };

    const pushTextRects = (element: Element): void => {
        for (const child of Array.from(element.childNodes)) {
            if (child.nodeType !== Node.TEXT_NODE || !child.textContent?.trim()) continue;
            const range = document.createRange();
            range.selectNodeContents(child);
            for (const lineRect of Array.from(range.getClientRects())) {
                if (elements.length / 5 >= MAX_ELEMENTS) return;
                if (intersectsViewport(lineRect, viewport)) push(ReplayElementType.Label, lineRect);
            }
        }
    };

    const visit = (element: Element): void => {
        if (visited++ >= MAX_VISITED_NODES || elements.length / 5 >= MAX_ELEMENTS) return;
        if (performance.now() > deadline) return;

        const tagName = element.tagName.toLowerCase();
        if (SKIPPED_TAGS.has(tagName)) return;

        const style = window.getComputedStyle(element);
        if (style.display === 'none') return;

        const rect = element.getBoundingClientRect();
        const isRendered = style.visibility !== 'hidden' && parseFloat(style.opacity || '1') > 0;
        const isOnScreen = intersectsViewport(rect, viewport);

        if (element.hasAttribute('data-redacted')) {
            if (isRendered && isOnScreen) push(ReplayElementType.View, rect);
            return;
        }

        let type: ReplayElementTypeValue | null = null;
        if (isRendered && isOnScreen && element !== document.documentElement && element !== document.body) {
            type = classify(element, style);
            if (type !== null) push(type, rect);
        }
        if (isLeafType(type)) return;

        if (isRendered && style.display !== 'contents') pushTextRects(element);

        if (style.overflow !== 'visible' && !isOnScreen) return;

        for (const child of Array.from(element.children)) {
            visit(child);
        }
    };

    visit(root);
    return elements;
};

const captureSnapshot = (): void => {
    safeCall(() => {
        const viewport = { width: window.innerWidth, height: window.innerHeight };
        if (viewport.width <= 0 || viewport.height <= 0) return;
        const elements = collectReplayElements(document.documentElement, viewport, performance.now() + TIME_BUDGET_MS);
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
            captureSnapshot();
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
