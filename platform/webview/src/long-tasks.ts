// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { log, createMessage } from './bridge';
import type { LongTaskMessage } from './types';

/**
 * Initialize long task monitoring using PerformanceObserver.
 * Long tasks are tasks that block the main thread for > 50ms.
 */
export function initLongTaskMonitoring(): void {
    if (typeof PerformanceObserver === 'undefined') {
        return;
    }

    try {
        const observer = new PerformanceObserver((list) => {
            for (const entry of list.getEntries()) {
                const taskEntry = entry as PerformanceEntry & {
                    attribution?: {
                        name?: string;
                        containerType?: string;
                        containerSrc?: string;
                        containerId?: string;
                        containerName?: string;
                    }[];
                };

                const attribution = taskEntry.attribution?.[0];

                const message = createMessage<LongTaskMessage>({
                    type: 'longTask',
                    durationMs: entry.duration,
                    startTime: entry.startTime,
                    attribution: attribution,
                });
                log(message);
            }
        });

        observer.observe({ type: 'longtask', buffered: true });
    } catch {
        // Long task observer not supported
    }
}
