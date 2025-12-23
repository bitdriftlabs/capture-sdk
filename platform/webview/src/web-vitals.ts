// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

<<<<<<< HEAD
import { onLCP, onCLS, onINP, onFCP, onTTFB, type MetricType } from 'web-vitals';
import { log, createMessage } from './bridge';
import { getCurrentPageSpanId } from './page-view';
=======
import { onLCP, onCLS, onINP, onFCP, onTTFB, type Metric } from 'web-vitals';
import { log, createMessage } from './bridge';
>>>>>>> a7d8e8b7 (first pass on setting up JS bridge with some performance tracking)
import type { WebVitalMessage } from './types';

/**
 * Initialize Core Web Vitals monitoring using the web-vitals library.
 * Reports: LCP, CLS, INP, FCP, TTFB
 */
<<<<<<< HEAD
export const initWebVitals = (): void => {
    const reportMetric = (metric: MetricType): void => {
        const parentSpanId = getCurrentPageSpanId();
        const message = createMessage<WebVitalMessage>({
            type: 'webVital',
            metric,
            ...(parentSpanId && { parentSpanId }),
        });
        log(message);
    };

    // Report all Core Web Vitals
    // Using reportAllChanges: false (default) to get final values
    onLCP(reportMetric);
    onCLS(reportMetric);
    onINP(reportMetric);
    onFCP(reportMetric);
    onTTFB(reportMetric);
};
=======
export function initWebVitals(): void {
  const reportMetric = (metric: Metric): void => {
    const message = createMessage<WebVitalMessage>({
      type: 'webVital',
      name: metric.name,
      value: metric.value,
      rating: metric.rating,
      navigationType: metric.navigationType,
    });
    log(message);
  };

  // Report all Core Web Vitals
  // Using reportAllChanges: false (default) to get final values
  onLCP(reportMetric);
  onCLS(reportMetric);
  onINP(reportMetric);
  onFCP(reportMetric);
  onTTFB(reportMetric);
}
>>>>>>> a7d8e8b7 (first pass on setting up JS bridge with some performance tracking)
