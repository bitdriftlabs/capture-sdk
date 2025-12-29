// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import { log, createMessage } from "./bridge";
import type { PageViewMessage, LifecycleMessage } from "./types";

/** Current page view span ID */
let currentPageSpanId: string | null = null;

/** Start time of current page view (epoch ms) */
let pageViewStartTimeMs: number = 0;

/**
 * Generate a unique span ID
 */
function generateSpanId(): string {
  // Use crypto.randomUUID if available, otherwise fallback
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  // Fallback for older environments
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Get the current page view span ID.
 * Can be used by other modules to nest their spans/logs under the current page view.
 */
export function getCurrentPageSpanId(): string | null {
  return currentPageSpanId;
}

/**
 * Start a new page view span.
 * This will end any existing page view span first.
 * 
 * For the initial page view, we use performance.timeOrigin as the start time
 * so that web vitals (which are measured from navigation start) fall within
 * the page view span.
 */
export function startPageView(
  url: string,
  reason: "initial" | "navigation" = "navigation"
): void {
  // End previous page view if exists
  if (currentPageSpanId) {
    endPageView("navigation");
  }

  currentPageSpanId = generateSpanId();
  
  // For initial page view, use navigation start time (performance.timeOrigin)
  // For SPA navigations, use current time
  if (reason === "initial") {
    pageViewStartTimeMs = performance.timeOrigin;
  } else {
    pageViewStartTimeMs = Date.now();
  }

  const message: PageViewMessage = {
    v: 1,
    type: "pageView",
    action: "start",
    spanId: currentPageSpanId,
    url,
    reason,
    // Use our calculated start time, not Date.now()
    timestamp: pageViewStartTimeMs,
  };
  log(message);
}

/**
 * End the current page view span.
 */
export function endPageView(
  reason: "navigation" | "unload" | "hidden"
): void {
  if (!currentPageSpanId) {
    return;
  }

  const now = Date.now();
  const durationMs = now - pageViewStartTimeMs;

  const message: PageViewMessage = {
    v: 1,
    timestamp: now,
    type: "pageView",
    action: "end",
    spanId: currentPageSpanId,
    url: window.location.href,
    reason,
    durationMs,
  };
  log(message);

  currentPageSpanId = null;
  pageViewStartTimeMs = 0;
}

/**
 * Log a lifecycle event within the current page view.
 */
function logLifecycleEvent(
  event: "DOMContentLoaded" | "load" | "visibilitychange",
  details?: Record<string, string>
): void {
  const message = createMessage<LifecycleMessage>({
    type: "lifecycle",
    event,
    parentSpanId: currentPageSpanId ?? undefined,
    performanceTime: performance.now(),
    ...details,
  });
  log(message);
}

/**
 * Initialize page view tracking.
 * This sets up the initial page view and lifecycle event listeners.
 */
export function initPageViewTracking(): void {
  // Start initial page view
  startPageView(window.location.href, "initial");

  // Track DOMContentLoaded
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => {
      logLifecycleEvent("DOMContentLoaded");
    });
  } else {
    // Already loaded, log immediately with note
    logLifecycleEvent("DOMContentLoaded");
  }

  // Track window load
  if (document.readyState !== "complete") {
    window.addEventListener("load", () => {
      logLifecycleEvent("load");
    });
  } else {
    // Already loaded
    logLifecycleEvent("load");
  }

  // Track visibility changes
  document.addEventListener("visibilitychange", () => {
    logLifecycleEvent("visibilitychange", {
      visibilityState: document.visibilityState,
    });

    // End page view when hidden (user switched tabs/apps)
    // This ensures CLS/INP are captured before the page is hidden
    if (document.visibilityState === "hidden") {
      endPageView("hidden");
    } else if (document.visibilityState === "visible" && !currentPageSpanId) {
      // Resume page view when becoming visible again
      startPageView(window.location.href, "navigation");
    }
  });

  // Track page unload
  window.addEventListener("pagehide", () => {
    endPageView("unload");
  });

  // Fallback for browsers that don't support pagehide
  window.addEventListener("beforeunload", () => {
    endPageView("unload");
  });
}
