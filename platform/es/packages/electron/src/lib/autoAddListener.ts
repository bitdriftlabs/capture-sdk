// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { Logger } from '@bitdrift/core';
import { ipcMain } from 'electron';
import { buildChannelName } from './utils';
import { BitdriftChannels } from './constants';
import { constructArrayBuffer } from './renderer/replay';

/**
 * Options for automatically exposing objects in the main world.
 */
export type AutoExposeOptions = Partial<{
  /** The prefix to use for the channel names. Defaults to ''. */
  channelPrefix: string;
  /** Experimental options */
  experimental?: {
    /** Whether to auto register session replay event channel. Defaults to false. */
    sessionReplayEnabled?: boolean;
  };
}>;

const DEFAULT_OPTIONS: AutoExposeOptions = {
  channelPrefix: undefined,
  experimental: {
    sessionReplayEnabled: false,
  },
};

export const autoAddListener = (
  logger: InstanceType<typeof Logger>,
  options?: AutoExposeOptions,
) => {
  const mergedOptions = {
    ...DEFAULT_OPTIONS,
    ...options,
    experimental: { ...DEFAULT_OPTIONS.experimental, ...options?.experimental },
  };

  // Wire up the IPC channel to the logger
  ipcMain.on(
    buildChannelName(BitdriftChannels.log, mergedOptions.channelPrefix),
    (_, level, message, fields) => {
      logger.log(level, message, fields ?? {});
    },
  );

  // Wire up the IPC channel to the logger

  if (mergedOptions.experimental?.sessionReplayEnabled) {
    ipcMain.on(
      buildChannelName(BitdriftChannels.replay, mergedOptions.channelPrefix),
      (
        _,
        payload: {
          screen: [number, number, number, number, number][];
          durationMs: number;
        },
      ) => {
        const screenBuffer = constructArrayBuffer(payload.screen);
        logger.logSessionReplayScreen(
          {
            screen: Buffer.from(screenBuffer),
          },
          payload.durationMs,
        );
      },
    );
  }
};
