// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { collectReplayElements, initReplayCapture, ReplayElementType } from '../replay';
import { createMessageCollector } from './mocks';

const viewport = { width: 400, height: 800 };

const layout = (element: Element, x: number, y: number, width: number, height: number): void => {
    vi.spyOn(element, 'getBoundingClientRect').mockReturnValue(new DOMRect(x, y, width, height));
};

const chunk = (elements: number[]): number[][] => {
    const out: number[][] = [];
    for (let i = 0; i < elements.length; i += 5) out.push(elements.slice(i, i + 5));
    return out;
};

describe('replay', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        layout(document.documentElement, 0, 0, viewport.width, viewport.height);
        layout(document.body, 0, 0, viewport.width, viewport.height);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('classifies elements with the Electron SDK rules', () => {
        document.body.innerHTML = `
            <h1 id="heading">Title</h1>
            <button id="button">Go</button>
            <input id="text" type="text" />
            <img id="image" />
            <iframe id="frame"></iframe>
            <section id="section"></section>
        `;
        layout(document.getElementById('heading') as Element, 0, 0, 400, 30);
        layout(document.getElementById('button') as Element, 10, 40, 100, 40);
        layout(document.getElementById('text') as Element, 10, 90, 200, 30);
        layout(document.getElementById('image') as Element, 10, 130, 300, 200);
        layout(document.getElementById('frame') as Element, 0, 340, 400, 200);
        layout(document.getElementById('section') as Element, 0, 550, 400, 100);

        const elements = chunk(collectReplayElements(document.body, viewport));

        expect(elements).toEqual([
            [ReplayElementType.View, 0, 0, 400, 800],
            [ReplayElementType.Label, 0, 0, 400, 30],
            [ReplayElementType.Button, 10, 40, 100, 40],
            [ReplayElementType.TextInput, 10, 90, 200, 30],
            [ReplayElementType.Image, 10, 130, 300, 200],
            [ReplayElementType.WebView, 0, 340, 400, 200],
            [ReplayElementType.View, 0, 550, 400, 100],
        ]);
    });

    it('maps translucent backgrounds to transparent views', () => {
        document.body.innerHTML = '<div id="overlay" style="background-color: rgba(0, 0, 0, 0.5)"></div>';
        layout(document.getElementById('overlay') as Element, 0, 0, 400, 100);

        const elements = chunk(collectReplayElements(document.body, viewport));

        expect(elements).toContainEqual([ReplayElementType.TransparentView, 0, 0, 400, 100]);
    });

    it('replaces a container view with a typed child of the same bounds', () => {
        document.body.innerHTML = '<div id="wrapper"><button id="button">Go</button></div>';
        layout(document.getElementById('wrapper') as Element, 10, 10, 100, 40);
        layout(document.getElementById('button') as Element, 10, 10, 100, 40);

        const elements = chunk(collectReplayElements(document.body, viewport));

        expect(elements).toEqual([
            [ReplayElementType.View, 0, 0, 400, 800],
            [ReplayElementType.Button, 10, 10, 100, 40],
        ]);
    });

    it('orders siblings by z-index', () => {
        document.body.innerHTML = `
            <button id="front" style="z-index: 2">Front</button>
            <button id="back" style="z-index: 1">Back</button>
        `;
        layout(document.getElementById('front') as Element, 0, 0, 100, 40);
        layout(document.getElementById('back') as Element, 0, 50, 100, 40);

        const elements = chunk(collectReplayElements(document.body, viewport));

        expect(elements.slice(1)).toEqual([
            [ReplayElementType.Button, 0, 50, 100, 40],
            [ReplayElementType.Button, 0, 0, 100, 40],
        ]);
    });

    it('skips hidden and off-screen elements', () => {
        document.body.innerHTML = `
            <button id="hidden" style="display: none">Hidden</button>
            <button id="invisible" style="visibility: hidden">Invisible</button>
            <button id="aria-hidden" aria-hidden="true">Aria hidden</button>
            <button id="offscreen">Offscreen</button>
        `;
        layout(document.getElementById('hidden') as Element, 0, 0, 100, 40);
        layout(document.getElementById('invisible') as Element, 0, 0, 100, 40);
        layout(document.getElementById('aria-hidden') as Element, 0, 0, 100, 40);
        layout(document.getElementById('offscreen') as Element, 0, 900, 100, 40);

        expect(chunk(collectReplayElements(document.body, viewport))).toEqual([
            [ReplayElementType.View, 0, 0, 400, 800],
        ]);
    });

    it('renders redacted elements as an opaque block without their contents', () => {
        document.body.innerHTML = '<div id="secret" data-redacted><button id="inner">Pay</button></div>';
        layout(document.getElementById('secret') as Element, 0, 0, 200, 50);
        layout(document.getElementById('inner') as Element, 0, 0, 100, 40);

        const elements = chunk(collectReplayElements(document.body, viewport));

        expect(elements).toEqual([
            [ReplayElementType.View, 0, 0, 400, 800],
            [ReplayElementType.View, 0, 0, 200, 50],
        ]);
    });

    it('stops walking once the deadline has passed', () => {
        expect(collectReplayElements(document.body, viewport, performance.now() - 1)).toEqual([]);
    });

    describe('initReplayCapture', () => {
        const setUpBridge = () => {
            const collector = createMessageCollector();
            let listener: ((event: { data: unknown }) => void) | undefined;
            (window.BitdriftLogger as { addEventListener?: unknown }).addEventListener = (
                _type: string,
                callback: (event: { data: unknown }) => void,
            ) => {
                listener = callback;
            };
            const request = (force = false) =>
                listener?.({ data: JSON.stringify({ type: 'replaySnapshotRequest', force }) });
            return { collector, request };
        };

        it('snapshots only when native requests it and the page changed', async () => {
            const { collector, request } = setUpBridge();
            vi.stubGlobal('requestIdleCallback', (callback: () => void) => callback());
            initReplayCapture();

            expect(collector.getMessagesByType('replaySnapshot')).toHaveLength(0);

            request();
            request();
            expect(collector.getMessagesByType('replaySnapshot')).toHaveLength(1);

            window.dispatchEvent(new Event('resize'));
            request();
            expect(collector.getMessagesByType('replaySnapshot')).toHaveLength(2);

            request(true);
            expect(collector.getMessagesByType('replaySnapshot')).toHaveLength(3);
        });
    });
});
