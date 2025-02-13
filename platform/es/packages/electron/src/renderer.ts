// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import {
  addSessionReplayCapture,
  type AddSessionReplayCaptureOptions,
  exposeInMainWorld,
  type AutoExposeOptions,
  setSessionReplayCaptureInterval,
} from './lib/renderer';

export type InitRendererOptions = {
  /** A prefix to use for the IPC channel names. Will only be used if autoExposeInMainWorld or experimental.sessionReplayConfiguration is set. */
  channelPrefix?: string;
  /** Whether to automatically expose the logger in the main world. Defaults to false. */
  autoExposeInMainWorld?: boolean | AutoExposeOptions;
  experimental?: {
    /** Whether to enable session replay, and with what options. Defaults to false. */
    sessionReplayConfiguration?: boolean | AddSessionReplayCaptureOptions;
  };
};

export const initRenderer = (options: InitRendererOptions) => {
  if (options.autoExposeInMainWorld) {
    exposeInMainWorld(
      typeof options.autoExposeInMainWorld === 'boolean'
        ? { channelPrefix: options.channelPrefix }
        : {
            channelPrefix: options.channelPrefix,
            ...options.autoExposeInMainWorld,
          },
    );
  }

  if (options?.experimental?.sessionReplayConfiguration) {
    const sessionReplayOptions =
      typeof options.experimental.sessionReplayConfiguration === 'boolean'
        ? { channelPrefix: options.channelPrefix }
        : {
            channelPrefix: options.channelPrefix,
            ...options.experimental.sessionReplayConfiguration,
          };
    addSessionReplayCapture(sessionReplayOptions);
  }
};

// Export internals
export {
  exposeInMainWorld,
  type AutoExposeOptions,
  addSessionReplayCapture,
  type AddSessionReplayCaptureOptions,
  setSessionReplayCaptureInterval,
};
