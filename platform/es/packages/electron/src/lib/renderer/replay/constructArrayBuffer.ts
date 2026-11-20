// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

type RawElement = [
  number, // type
  number, // x
  number, // y
  number, // width
  number, // height
];

/**
 * Construct an ArrayBuffer in the bitdrift replay formal from an array of raw elements.
 * @param data 
 * @returns 
 */
export const constructArrayBuffer = (data: RawElement[]): ArrayBuffer => {
  const totalSize = data.length * 9; // 5 numbers per tuple

  const buffer = new ArrayBuffer(totalSize);
  const dataView = new DataView(buffer);

  let offset = 0;

  for (const [type, x, y, width, height] of data) {
    const typeWithMask = type | 0b11110000;
    dataView.setInt8(offset, typeWithMask);
    offset += 1;
    dataView.setInt16(offset, x, false);
    offset += 2;
    dataView.setInt16(offset, y, false);
    offset += 2;
    dataView.setInt16(offset, width, false);
    offset += 2;
    dataView.setInt16(offset, height, false);
    offset += 2;
  }

  return buffer;
};
