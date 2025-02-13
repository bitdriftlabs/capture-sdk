// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

export const BitdriftLogLevels = Object.freeze({
  trace: 0,
  debug: 1,
  info: 2,
  warn: 3,
  error: 4,
});

export const BitdriftChannels = Object.freeze({
  log: {
    type: 'bitdriftChannel',
    value: 'log',
  },
  replay: {
    type: 'bitdriftChannel',
    value: 'replay',
  },
});

export type BitdriftChannels =
  (typeof BitdriftChannels)[keyof typeof BitdriftChannels];
