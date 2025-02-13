// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

export type ParsedColor = {
  space: 'rgb' | 'hsl' | 'hex';
  values: number[];
  alpha: number;
};

export function colorParse(input: string | number): ParsedColor {
  if (typeof input === 'number') {
    return {
      space: 'rgb',
      values: [(input >> 16) & 255, (input >> 8) & 255, input & 255],
      alpha: 1,
    };
  }

  const str = input.toLowerCase().trim();

  if (str === 'transparent') {
    return { space: 'rgb', values: [0, 0, 0], alpha: 0 };
  }

  const hexMatch = str.match(/^#([0-9a-f]{3,8})$/i);
  if (hexMatch) {
    let hex = hexMatch[1] as string;
    if (hex.length === 3 || hex.length === 4) {
      hex = hex
        .split('')
        .map((c) => c + c)
        .join('');
    }
    const rgb = [
      parseInt(hex.slice(0, 2), 16),
      parseInt(hex.slice(2, 4), 16),
      parseInt(hex.slice(4, 6), 16),
    ];
    const alpha = hex.length === 8 ? parseInt(hex.slice(6, 8), 16) / 255 : 1;
    return {
      space: 'hex',
      values: rgb,
      alpha: Number.parseFloat(alpha.toFixed(2)),
    };
  }

  const rgbMatch = str.match(/^rgb\(([^)]+)\)$/);
  if (rgbMatch !== undefined && rgbMatch !== null) {
    // @ts-ignore
    const values = rgbMatch[1].split(/,\s*/).map(Number);
    return {
      space: 'rgb',
      values: values.slice(0, 3),
      alpha: Number.parseFloat((values[3] ?? 1).toFixed(2)),
    };
  }

  const hslMatch = str.match(/^hsl\(([^)]+)\)$/);
  if (hslMatch) {
    const values =
    // @ts-ignore
      hslMatch?.[1].split(/,\s*/).map((v) => Number(v.replace('%', ''))) ?? [];
    return {
      space: 'hsl',
      values: values.slice(0, 3),
      alpha: values[3] ?? 1,
    };
  }

  return { space: 'rgb', values: [0, 0, 0], alpha: 1 };
}
