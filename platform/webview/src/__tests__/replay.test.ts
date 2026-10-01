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

if (!Range.prototype.getClientRects) {
    Range.prototype.getClientRects = () => [] as unknown as DOMRectList;
}

const chunk = (elements: number[]): number[][] => {
    const out: number[][] = [];
    for (let i = 0; i < elements.length; i += 5) out.push(elements.slice(i, i + 5));
    return out;
};

describe('replay', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        vi.spyOn(Range.prototype, 'getClientRects').mockReturnValue([] as unknown as DOMRectList);
        layout(document.documentElement, 0, 0, viewport.width, viewport.height);
        layout(document.body, 0, 0, viewport.width, viewport.height);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('classifies interactive and media elements', () => {
        document.body.innerHTML = `
            <button id="button">Go</button>
            <input id="text" type="text" />
            <input id="checked" type="checkbox" checked />
            <input id="unchecked" type="checkbox" />
            <img id="image" />
        `;
        layout(document.getElementById('button') as Element, 10, 20, 100, 40);
        layout(document.getElementById('text') as Element, 10, 70, 200, 30);
        layout(document.getElementById('checked') as Element, 10, 110, 20, 20);
        layout(document.getElementById('unchecked') as Element, 40, 110, 20, 20);
        layout(document.getElementById('image') as Element, 10, 140, 300, 200);

        const elements = chunk(collectReplayElements(document.documentElement, viewport));

        expect(elements).toEqual([
            [ReplayElementType.Button, 10, 20, 100, 40],
            [ReplayElementType.TextInput, 10, 70, 200, 30],
            [ReplayElementType.SwitchOn, 10, 110, 20, 20],
            [ReplayElementType.SwitchOff, 40, 110, 20, 20],
            [ReplayElementType.Image, 10, 140, 300, 200],
        ]);
    });

    it('emits painted containers and skips transparent ones', () => {
        document.body.innerHTML = `
            <div id="painted" style="background-color: rgb(255, 0, 0)"></div>
            <div id="transparent"></div>
        `;
        layout(document.getElementById('painted') as Element, 0, 0, 400, 100);
        layout(document.getElementById('transparent') as Element, 0, 100, 400, 100);

        const elements = chunk(collectReplayElements(document.documentElement, viewport));

        expect(elements).toEqual([[ReplayElementType.View, 0, 0, 400, 100]]);
    });

    it('skips hidden and off-screen elements', () => {
        document.body.innerHTML = `
            <button id="hidden" style="display: none">Hidden</button>
            <button id="invisible" style="visibility: hidden">Invisible</button>
            <button id="offscreen">Offscreen</button>
        `;
        layout(document.getElementById('hidden') as Element, 0, 0, 100, 40);
        layout(document.getElementById('invisible') as Element, 0, 0, 100, 40);
        layout(document.getElementById('offscreen') as Element, 0, 900, 100, 40);

        expect(collectReplayElements(document.documentElement, viewport)).toEqual([]);
    });

    it('emits a label per rendered text line', () => {
        document.body.innerHTML = '<p id="paragraph">Hello world</p>';
        vi.mocked(Range.prototype.getClientRects).mockReturnValue([
            new DOMRect(8, 10, 120, 18),
            new DOMRect(8, 28, 60, 18),
        ] as unknown as DOMRectList);
        layout(document.getElementById('paragraph') as Element, 8, 10, 384, 36);

        const elements = chunk(collectReplayElements(document.documentElement, viewport));

        expect(elements).toEqual([
            [ReplayElementType.Label, 8, 10, 120, 18],
            [ReplayElementType.Label, 8, 28, 60, 18],
        ]);
    });

    it('renders redacted elements as an opaque block without their contents', () => {
        document.body.innerHTML = '<div id="secret" data-redacted><button id="inner">Pay</button></div>';
        layout(document.getElementById('secret') as Element, 0, 0, 200, 50);
        layout(document.getElementById('inner') as Element, 0, 0, 100, 40);

        const elements = chunk(collectReplayElements(document.documentElement, viewport));

        expect(elements).toEqual([[ReplayElementType.View, 0, 0, 200, 50]]);
    });

    it('stops walking once the deadline has passed', () => {
        document.body.innerHTML = '<button id="button">Go</button>';
        layout(document.getElementById('button') as Element, 0, 0, 100, 40);

        expect(collectReplayElements(document.documentElement, viewport, performance.now() - 1)).toEqual([]);
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
