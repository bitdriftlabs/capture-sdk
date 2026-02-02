// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { onLCP, onCLS, onINP, onFCP, onTTFB, type MetricType } from 'web-vitals';
import { log, createMessage } from './bridge';
import { safeCall, makeSafe } from './safe-call';
import { getCurrentPageSpanId } from './page-view';

/**
 * Initialize Core Web Vitals monitoring using the web-vitals library.
 * Reports: LCP, CLS, INP, FCP, TTFB
 */
export const initWebVitals = (): void => {
    safeCall(() => {
        const reportMetric = makeSafe((metric: MetricType): void => {
            const parentSpanId = getCurrentPageSpanId();
            
            // Build fields with metric data and all performance API fields
            const fields: Record<string, string> = {
                _metric: metric.name,
                _value: metric.value.toString(),
                _rating: metric.rating,
            };
            
            if (metric.delta !== undefined) fields._delta = metric.delta.toString();
            if (metric.id) fields._metric_id = metric.id;
            if (metric.navigationType) fields._navigation_type = metric.navigationType;
            if (parentSpanId) fields._span_parent_id = parentSpanId;
            
            // Extract all relevant entry fields based on metric type
            const entry = metric.entries?.[0];
            if (entry) {
                // Common fields
                if (entry.startTime !== undefined) fields._start_time = entry.startTime.toString();
                if (entry.entryType) fields._entry_type = entry.entryType;
                
                // LCP-specific fields
                if ('element' in entry && entry.element) fields._element = entry.element as string;
                if ('url' in entry && entry.url) fields._url = entry.url as string;
                if ('size' in entry && entry.size !== undefined) fields._size = (entry.size as number).toString();
                if ('renderTime' in entry && entry.renderTime !== undefined) fields._render_time = (entry.renderTime as number).toString();
                if ('loadTime' in entry && entry.loadTime !== undefined) fields._load_time = (entry.loadTime as number).toString();
                
                // FCP-specific fields
                if ('name' in entry && entry.name && metric.name === 'FCP') fields._paint_type = entry.name as string;
                
                // TTFB-specific fields (PerformanceNavigationTiming)
                if ('domainLookupStart' in entry && entry.domainLookupStart !== undefined) fields._dns_start = (entry.domainLookupStart as number).toString();
                if ('domainLookupEnd' in entry && entry.domainLookupEnd !== undefined) fields._dns_end = (entry.domainLookupEnd as number).toString();
                if ('connectStart' in entry && entry.connectStart !== undefined) fields._connect_start = (entry.connectStart as number).toString();
                if ('connectEnd' in entry && entry.connectEnd !== undefined) fields._connect_end = (entry.connectEnd as number).toString();
                if ('secureConnectionStart' in entry && entry.secureConnectionStart !== undefined) fields._tls_start = (entry.secureConnectionStart as number).toString();
                if ('requestStart' in entry && entry.requestStart !== undefined) fields._request_start = (entry.requestStart as number).toString();
                if ('responseStart' in entry && entry.responseStart !== undefined) fields._response_start = (entry.responseStart as number).toString();
                
                // INP-specific fields
                if ('processingStart' in entry && entry.processingStart !== undefined) fields._processing_start = (entry.processingStart as number).toString();
                if ('processingEnd' in entry && entry.processingEnd !== undefined) fields._processing_end = (entry.processingEnd as number).toString();
                if ('duration' in entry && entry.duration !== undefined) fields._duration = (entry.duration as number).toString();
                if ('interactionId' in entry && entry.interactionId !== undefined) fields._interaction_id = (entry.interactionId as number).toString();
                if ('name' in entry && entry.name && metric.name === 'INP') fields._event_type = entry.name as string;
            }
            
            // CLS-specific: Extract all entries for layout shift data
            if (metric.name === 'CLS' && metric.entries && metric.entries.length > 0) {
                let largestShiftValue = 0;
                let largestShiftTime = 0;
                
                for (const clsEntry of metric.entries) {
                    const shiftValue = 'value' in clsEntry ? (clsEntry.value as number) : 0;
                    if (shiftValue > largestShiftValue) {
                        largestShiftValue = shiftValue;
                        largestShiftTime = clsEntry.startTime ?? 0;
                    }
                }
                
                if (largestShiftValue > 0) {
                    fields._largest_shift_value = largestShiftValue.toString();
                    fields._largest_shift_time = largestShiftTime.toString();
                }
                fields._shift_count = metric.entries.length.toString();
            }
            
            const message = createMessage({
                type: 'webVital',
                metric,
                ...(parentSpanId && { parentSpanId }),
                fields,
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
