// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { log, createMessage } from './bridge';
import type { NetworkRequestMessage, ResourceTimingData } from './types';

let requestCounter = 0;

function generateRequestId(): string {
  return `req_${Date.now()}_${++requestCounter}`;
}

/**
 * Try to get resource timing data for a URL
 */
function getResourceTiming(url: string): ResourceTimingData | undefined {
  if (typeof performance === 'undefined' || !performance.getEntriesByType) {
    return undefined;
  }

  const entries = performance.getEntriesByType('resource') as PerformanceResourceTiming[];
  // Find the most recent entry for this URL
  const entry = entries.filter(e => e.name === url).pop();

  if (!entry) {
    return undefined;
  }

  return {
    dnsMs: entry.domainLookupEnd - entry.domainLookupStart,
    connectMs: entry.connectEnd - entry.connectStart,
    tlsMs: entry.secureConnectionStart > 0 
      ? entry.connectEnd - entry.secureConnectionStart 
      : undefined,
    ttfbMs: entry.responseStart - entry.requestStart,
    downloadMs: entry.responseEnd - entry.responseStart,
    transferSize: entry.transferSize,
  };
}

/**
 * Intercept fetch requests
 */
function interceptFetch(): void {
  const originalFetch = window.fetch;
  
  if (!originalFetch) {
    return;
  }

  window.fetch = async function(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    const requestId = generateRequestId();
    const startTime = performance.now();
    
    // Extract URL and method
    let url: string;
    let method: string;
    
    if (input instanceof Request) {
      url = input.url;
      method = input.method;
    } else {
      url = input.toString();
      method = init?.method ?? 'GET';
    }

    try {
      const response = await originalFetch.call(window, input, init);
      const endTime = performance.now();
      
      const message = createMessage<NetworkRequestMessage>({
        type: 'networkRequest',
        requestId,
        method: method.toUpperCase(),
        url,
        statusCode: response.status,
        durationMs: Math.round(endTime - startTime),
        success: response.ok,
        requestType: 'fetch',
        timing: getResourceTiming(url),
      });
      
      log(message);
      return response;
    } catch (error) {
      const endTime = performance.now();
      
      const message = createMessage<NetworkRequestMessage>({
        type: 'networkRequest',
        requestId,
        method: method.toUpperCase(),
        url,
        statusCode: 0,
        durationMs: Math.round(endTime - startTime),
        success: false,
        error: error instanceof Error ? error.message : String(error),
        requestType: 'fetch',
      });
      
      log(message);
      throw error;
    }
  };
}

/**
 * Intercept XMLHttpRequest
 */
function interceptXHR(): void {
  const originalOpen = XMLHttpRequest.prototype.open;
  const originalSend = XMLHttpRequest.prototype.send;

  XMLHttpRequest.prototype.open = function(
    method: string,
    url: string | URL,
    async: boolean = true,
    username?: string | null,
    password?: string | null
  ): void {
    // Store request info on the XHR instance
    (this as XMLHttpRequest & { _bitdrift?: { method: string; url: string; requestId: string } })._bitdrift = {
      method: method.toUpperCase(),
      url: url.toString(),
      requestId: generateRequestId(),
    };
    
    return originalOpen.call(this, method, url, async, username, password);
  };

  XMLHttpRequest.prototype.send = function(body?: Document | XMLHttpRequestBodyInit | null): void {
    const xhr = this as XMLHttpRequest & { _bitdrift?: { method: string; url: string; requestId: string } };
    const info = xhr._bitdrift;
    
    if (!info) {
      return originalSend.call(this, body);
    }

    const startTime = performance.now();

    const handleComplete = (): void => {
      const endTime = performance.now();
      
      const message = createMessage<NetworkRequestMessage>({
        type: 'networkRequest',
        requestId: info.requestId,
        method: info.method,
        url: info.url,
        statusCode: xhr.status,
        durationMs: Math.round(endTime - startTime),
        success: xhr.status >= 200 && xhr.status < 400,
        requestType: 'xhr',
        timing: getResourceTiming(info.url),
      });
      
      log(message);
    };

    const handleError = (): void => {
      const endTime = performance.now();
      
      const message = createMessage<NetworkRequestMessage>({
        type: 'networkRequest',
        requestId: info.requestId,
        method: info.method,
        url: info.url,
        statusCode: 0,
        durationMs: Math.round(endTime - startTime),
        success: false,
        error: 'Network error',
        requestType: 'xhr',
      });
      
      log(message);
    };

    xhr.addEventListener('load', handleComplete);
    xhr.addEventListener('error', handleError);
    xhr.addEventListener('abort', handleError);
    xhr.addEventListener('timeout', handleError);

    return originalSend.call(this, body);
  };
}

/**
 * Initialize network interception for both fetch and XHR
 */
export function initNetworkInterceptor(): void {
  interceptFetch();
  interceptXHR();
}
