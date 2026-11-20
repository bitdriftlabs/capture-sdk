// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { type BitdriftChannels } from './constants';
/**
 * Builds a channel name for the main process to listen to.
 * @param tail the tail of the channel name
 * @param prefix an optional prefix for the channel name
 * @returns
 */
export const buildChannelName = (tail: BitdriftChannels, prefix?: string) =>
  [prefix, 'bitdrift', tail.value].filter(Boolean).join(':');
