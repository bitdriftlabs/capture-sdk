// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { colorParse, type ParsedColor } from './colorParse'; // Adjust the import path as needed

describe('colorParse', () => {
  test('parses numeric RGB input', () => {
    expect(colorParse(0xff0000)).toEqual<ParsedColor>({
      space: 'rgb',
      values: [255, 0, 0],
      alpha: 1,
    });

    expect(colorParse(0x00ff00)).toEqual<ParsedColor>({
      space: 'rgb',
      values: [0, 255, 0],
      alpha: 1,
    });

    expect(colorParse(0x0000ff)).toEqual<ParsedColor>({
      space: 'rgb',
      values: [0, 0, 255],
      alpha: 1,
    });
  });

  test('parses short hex colors (#RGB, #RGBA)', () => {
    expect(colorParse('#f00')).toEqual<ParsedColor>({
      space: 'hex',
      values: [255, 0, 0],
      alpha: 1,
    });

    expect(colorParse('#0f08')).toEqual<ParsedColor>({
      space: 'hex',
      values: [0, 255, 0],
      alpha: 0.53, // 8 / 15
    });
  });

  test('parses full hex colors (#RRGGBB, #RRGGBBAA)', () => {
    expect(colorParse('#ff0000')).toEqual<ParsedColor>({
      space: 'hex',
      values: [255, 0, 0],
      alpha: 1,
    });

    expect(colorParse('#00ff0080')).toEqual<ParsedColor>({
      space: 'hex',
      values: [0, 255, 0],
      alpha: 0.5,
    });

    expect(colorParse('#abcdef')).toEqual<ParsedColor>({
      space: 'hex',
      values: [171, 205, 239],
      alpha: 1,
    });
  });

  test('parses rgb() colors', () => {
    expect(colorParse('rgb(255, 0, 0)')).toEqual<ParsedColor>({
      space: 'rgb',
      values: [255, 0, 0],
      alpha: 1,
    });

    expect(colorParse('rgb(0, 255, 0, 0.5)')).toEqual<ParsedColor>({
      space: 'rgb',
      values: [0, 255, 0],
      alpha: 0.5,
    });

    expect(colorParse('rgb(100, 150, 200)')).toEqual<ParsedColor>({
      space: 'rgb',
      values: [100, 150, 200],
      alpha: 1,
    });
  });

  test('parses hsl() colors', () => {
    expect(colorParse('hsl(0, 100%, 50%)')).toEqual<ParsedColor>({
      space: 'hsl',
      values: [0, 100, 50],
      alpha: 1,
    });

    expect(colorParse('hsl(120, 50%, 50%, 0.3)')).toEqual<ParsedColor>({
      space: 'hsl',
      values: [120, 50, 50],
      alpha: 0.3,
    });
  });

  test('handles special case "transparent"', () => {
    expect(colorParse('transparent')).toEqual<ParsedColor>({
      space: 'rgb',
      values: [0, 0, 0],
      alpha: 0,
    });
  });

  test('returns default black for invalid inputs', () => {
    expect(colorParse('invalid')).toEqual<ParsedColor>({
      space: 'rgb',
      values: [0, 0, 0],
      alpha: 1,
    });

    expect(colorParse('#xyz')).toEqual<ParsedColor>({
      space: 'rgb',
      values: [0, 0, 0],
      alpha: 1,
    });
  });
});
