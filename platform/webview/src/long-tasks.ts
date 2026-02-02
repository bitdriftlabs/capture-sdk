// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { log, createMessage } from './bridge';
import { makeSafe, safeCall } from './safe-call';

/**
 * Initialize long task monitoring using PerformanceObserver.
 * Long tasks are tasks that block the main thread for > 50ms.
 */
export const initLongTaskMonitoring = (): void => {
    if (typeof PerformanceObserver === 'undefined') {
        return;
    }

    try {
        const observer = new PerformanceObserver(
            makeSafe((list) => {
                for (const entry of list.getEntries()) {
                    safeCall(() => {
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

                        // Build fields with proper naming
                        const fields: Record<string, string> = {
                            _duration_ms: entry.duration.toString(),
                            _start_time: entry.startTime.toString(),
                        };

                        // Flatten attribution object
                        if (attribution) {
                            if (attribution.name) fields._attribution_name = attribution.name;
                            if (attribution.containerType) fields._container_type = attribution.containerType;
                            if (attribution.containerSrc) fields._container_src = attribution.containerSrc;
                            if (attribution.containerId) fields._container_id = attribution.containerId;
                            if (attribution.containerName) fields._container_name = attribution.containerName;
                        }

                        const message = createMessage({
                            type: 'longTask',
                            durationMs: entry.duration,
                            startTime: entry.startTime,
                            attribution: attribution,
                            fields,
                        });
                        log(message);
                    });
                }
            }),
        );

        observer.observe({ type: 'longtask', buffered: true });
    } catch {
        // Long task observer not supported
    }
};
