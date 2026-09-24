// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { onLCP, onCLS, onINP, onFCP, onTTFB, type MetricType } from 'web-vitals';
import { log, createMessage } from './bridge';
import { safeCall, makeSafe } from './safe-call';
import { getCurrentPageSpanId, getLatestPageSpanId, getPageSpanIdAtTime } from './page-view';

const MAX_RETAINED_METRIC_PARENTS = 512;

export const makeCloneableMetric = (metric: MetricType): MetricType => {
    const { entries, ...rest } = metric;
    // The Metric object from web-vitals contains some cloneable properties, namely PerformanceResourceTiming instances, which have circular references and non-enumerable properties that cause issues when we try to serialize the metric to send it across the native bridge. To work around this, we create a shallow clone of the metric object that only includes the enumerable properties, which are sufficient for our use case.
    return {
        ...rest,
        entries: metric.entries.map((entry) => entry.toJSON()),
    };
};

/**
 * Initialize Core Web Vitals monitoring using the web-vitals library.
 * Reports: LCP, CLS, INP, FCP, TTFB
 */
export const initWebVitals = (): void => {
    safeCall(() => {
        // Metrics without entries still belong to the document in which monitoring began.
        const initialPageSpanId = getCurrentPageSpanId();
        const metricParentSpanIds = new Map<string, string>();

        const reportMetric = makeSafe((metric: MetricType): void => {
            let parentSpanId = metricParentSpanIds.get(metric.id) ?? null;
            if (!parentSpanId) {
                const firstEntry = metric.entries[0];
                parentSpanId = firstEntry
                    ? getPageSpanIdAtTime(firstEntry.startTime)
                    : metric.navigationType === 'back-forward-cache'
                      ? getLatestPageSpanId()
                      : initialPageSpanId;

                if (parentSpanId) {
                    metricParentSpanIds.set(metric.id, parentSpanId);
                    if (metricParentSpanIds.size > MAX_RETAINED_METRIC_PARENTS) {
                        const oldestMetricId = metricParentSpanIds.keys().next().value;
                        if (oldestMetricId) {
                            metricParentSpanIds.delete(oldestMetricId);
                        }
                    }
                }
            }
            const message = createMessage({
                type: 'webVital',
                metric: makeCloneableMetric(metric),
                ...(parentSpanId && { parentSpanId }),
                // Include the URL of the page where the metric was recorded
                // We're purposefully stripping query params and fragments to reduce PII and cardinality.
                url: `${window.location.origin}${window.location.pathname}`,
            });
            log(message);
        });

        // Report all Core Web Vitals
        // Using reportAllChanges: false (default) to get final values
        onLCP(reportMetric);
        onCLS(reportMetric);
        onINP(reportMetric);
        onFCP(reportMetric);
        onTTFB(reportMetric);
    });
};
