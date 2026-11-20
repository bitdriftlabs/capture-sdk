// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { colorParse } from './colorParse';
import { matchByRole, matchByTagName } from './utils';

type CaptureElement = {
  type:
    | 'input'
    | 'label'
    | 'button'
    | 'image'
    | 'transparent'
    | 'view'
    | 'webview';
  x: number;
  y: number;
  width: number;
  height: number;
};

enum ReplayViewType {
  label = 0,
  button = 1,
  input = 2,
  image = 3,
  view = 4,
  background = 5,
  switchOn = 6,
  switchOff = 7,
  map = 8,
  chevron = 9,
  transparent = 10,
  keyboard = 11,
  webview = 12,
}

const getTypeFromElement = (
  element: Element,
  targetWindow: Window,
): CaptureElement['type'] | null => {
  const styles = targetWindow.getComputedStyle(element);
  if (
    styles.display === 'none' ||
    styles.visibility === 'hidden' ||
    styles.opacity === '0' ||
    element.getAttribute('aria-hidden') === 'true'
  ) {
    return null;
  }

  if (
    matchByRole(element, ['heading', 'label', 'paragraph']) ||
    matchByTagName(element, ['span', 'p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6'])
  ) {
    return 'label';
  }

  if (
    matchByRole(element, ['image']) ||
    matchByTagName(element, ['img', 'svg', 'picture'])
  ) {
    return 'image';
  }

  if (
    matchByRole(element, ['button', 'link']) ||
    matchByTagName(element, ['button', 'a'])
  ) {
    return 'button';
  }

  if (
    matchByTagName(element, ['textarea']) ||
    (matchByTagName(element, ['input']) &&
      element.getAttribute('type') !== 'text')
  ) {
    return 'input';
  }

  if (matchByTagName(element, ['iframe'])) {
    return 'webview';
  }

  // Calculate background color alpha
  const bg = styles.backgroundColor;
  const a = colorParse(bg).alpha;

  if (a && a >= 0 && a < 1) {
    return 'transparent';
  }

  return 'view';
};

type ElementKey =
  `${CaptureElement['type']}~${number}~${number}~${number}~${number}`;

/**
 * Builds a typed element key from a capture element.
 * @param param0
 * @returns
 */
const keyFromCaptureElement = ({
  type,
  x,
  y,
  width,
  height,
}: CaptureElement): ElementKey => `${type}~${x}~${y}~${width}~${height}`;

/**
 * Calculates the z-index of an element.
 * @param element
 * @returns
 */
const zIndexFromElement = (element: Element) =>
  parseInt(getComputedStyle(element).zIndex, 10) || 0;

export type ScreenCaptureResult = {
  screen: [ReplayViewType, number, number, number, number][];
  durationMs: number;
};

/**
 * Given a target window, recursively traverses the DOM parsing elements into a screen capture wire frames.
 * @param targetWindow
 * @returns
 */
export const captureScreen = (targetWindow: Window): ScreenCaptureResult => {
  const elementsMap = new Map<string, CaptureElement>();

  /**
   * Handles the insertion or updating of an element in the elementsMap.
   * @param value
   */
  const upsertElement = (value: CaptureElement) => {
    const key = keyFromCaptureElement(value);
    const viewKey = keyFromCaptureElement({ ...value, type: 'view' });

    // Attempt to find an existing view element with the same coords (i.e. the same partial key).
    // If the new element is not a view, we should remove the existing view with the new element, so that it can be replaced with the new element.
    // This serves to remove container elements which will clutter the replay view.
    if (elementsMap.get(viewKey) && value.type !== 'view') {
      elementsMap.delete(viewKey);
    }

    elementsMap.set(key, value);
  };

  /**
   * Traverses an element, converting it into a capture element and adding it to the elementsMap.
   * Recursively traverses the elements children.
   * @param element
   * @returns
   */
  const traverse = (element: Element) => {
    const { x, y, width, height } = element.getBoundingClientRect();
    const type = getTypeFromElement(element, targetWindow);

    // Skip element if we can't determine the type.
    if (type) {
      upsertElement({
        type,
        x,
        y,
        width,
        height,
      });
    }

    // Early return to skip traversing svg elements. We may support traversing SVGs in the future.
    if (element.tagName.toLocaleLowerCase() === 'svg') return;

    // To ensure that elements are stacked in the correct order, we sort them by z-index.
    // This is done on a per-parent basis, since sorting _after_ traversal would
    // blow away the position within the DOM as a factor in stacking.
    const children = Array.from(element.children).sort(
      (a, b) => zIndexFromElement(a) - zIndexFromElement(b),
    );

    // Recurse into children
    children.forEach(traverse);
  };

  const p1 = performance.now();
  traverse(document.body);
  const p2 = performance.now();

  const elements = [
    {
      type: 'view' as const,
      x: 0,
      y: 0,
      width: targetWindow.innerWidth,
      height: targetWindow.innerHeight,
    },
    ...elementsMap.values(),
  ];

  const screen = elements.map(
    (el) =>
      [ReplayViewType[el.type], el.x, el.y, el.width, el.height] satisfies [
        number,
        number,
        number,
        number,
        number,
      ],
  );

  return { screen, durationMs: p2 - p1 };
};
