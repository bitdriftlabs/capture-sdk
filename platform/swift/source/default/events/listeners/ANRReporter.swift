// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

//
//  BSGAppHangDetector.m
//  Bugsnag
//
//  Created by Nick Dowell on 01/03/2021.
//  Copyright © 2021 Bugsnag Inc. All rights reserved.
//

// Copied and modified from
// https://github.com/bugsnag/bugsnag-cocoa/blob/b29cdabec2f0d396a6a80ca1cfe7ee0bfdf04992/Bugsnag/Helpers/BSGAppHangDetector.m
// swiftlint:disable:previous line_length

import Foundation

/// Application not responding (ANR) event reporter.
final class ANRReporter {
    enum Keys: String {
        case ANRDurationThreshold = "_threshold_duration_ms"
        case ANRDuration = "_duration_ms"
    }

    private let logger: CoreLogging
    private let appStateAttributes: AppStateAttributes

    private let isActive = Atomic(false)
    private var observer: CFRunLoopObserver?

    private var anrDurationThresholdMs = RuntimeVariable.applicationANRReporterThresholdMs.defaultValue

    private let mainRunLoopProcessingStarted = DispatchSemaphore(value: 0)
    private let mainRunLoopProcessingFinished = DispatchSemaphore(value: 0)
    private var mainRunLoopEventProcessingDeadline: DispatchTime = .distantFuture

    init(logger: CoreLogging, appStateAttributes: AppStateAttributes) {
        self.logger = logger
        self.appStateAttributes = appStateAttributes
    }

    func run() {
        while self.isActive.load() {
            self.detectANR()
        }
    }

    // MARK: - Private

    private func detectANR() {
        // swiftlint:disable:previous function_body_length
        // Wait for the main run loop processing to start.
        self.mainRunLoopProcessingStarted.wait()

        let mainRunLoopProcessingStartTime = Uptime()

        // Wait for the main run loop processing to complete.
        guard self.mainRunLoopProcessingFinished
            .wait(timeout: self.mainRunLoopEventProcessingDeadline) == .timedOut else
        {
            // Main run loop processing completed within the time threshold that constitutes an ANR.
            return
        }

        // Main run loop processing is still ongoing and it's taking enough time to constitute an ANR.
        // Figure out whether the ANR should be logged.

        var shouldLogANR = true
        if DispatchTime.now() > self.mainRunLoopEventProcessingDeadline + .seconds(1) {
            // The semaphore timeout happened long after the defined ANR threshold.
            // It's possible that the app was suspended. Do not report an App Hang to avoid false positives.
            shouldLogANR = false
        }

        if shouldLogANR && !self.appStateAttributes.isForeground {
            // Do not report ANRs if app is not in the foreground.
            shouldLogANR = false
        }

        if shouldLogANR && Debugger.isAttached() {
            // Do not report ANRs if debugger is attached.
            shouldLogANR = false
        }

        var fields: [String: FieldValue]?
        if shouldLogANR {
            fields = [
                Keys.ANRDurationThreshold.rawValue: String(self.anrDurationThresholdMs),
                // TODO: (Augustyniak): Consider using span events APIs if these events start resembling
                // spans. For now, we've added `_span_id` to allow connection between start and stop events.
                "_span_id": UUID().uuidString,
            ]

            self.logger.log(
                level: .error,
                message: "ANR",
                file: nil,
                line: nil,
                function: nil,
                fields: fields,
                matchingFields: nil,
                error: nil,
                type: .lifecycle,
                blocking: false
            )
        }

        guard self.mainRunLoopProcessingFinished.wait(timeout: .distantFuture) == .success else {
            return
        }

        // Present only if ANR log event was logged.
        guard var fields else {
            return
        }

        // The previous wait on the semaphore timed out so we must wait again to balance signal-wait
        // calls.
        // Use it as an opportunity to log when ANR completes.
        // TODO(Augustyniak): Move to DispatchTime and use `distance(to:)` once the support for iOS 12
        // is dropped.
        let duration = Uptime().timeIntervalSince(mainRunLoopProcessingStartTime)
        fields[Keys.ANRDuration.rawValue] = String(duration * 1_000)

        self.logger.log(
            level: .info,
            message: "ANREnd",
            file: nil,
            line: nil,
            function: nil,
            fields: fields,
            matchingFields: nil,
            error: nil,
            type: .lifecycle,
            blocking: false
        )
    }
}

extension ANRReporter: EventListener {
    func start() {
        guard self.logger.runtimeValue(.applicationANRReporting) else {
            return
        }

        guard !Environment.isRunningTests else {
            // Do not report events if executed as part of tests run.
            return
        }

        guard !Environment.isAppExtension else {
            // For now, we limit the reporting of ANR to the main app process only.
            return
        }

        self.anrDurationThresholdMs = self.logger.runtimeValue(.applicationANRReporterThresholdMs)

        self.isActive.update { $0 = true }

        let registeredActivity: CFRunLoopActivity = [.beforeSources, .beforeWaiting, .afterWaiting]
        var isMainRunLoopProcessingEvent = false

        let observer = CFRunLoopObserverCreateWithHandler(
            nil, registeredActivity.rawValue, true, .max
        ) { _, activity in
            if isMainRunLoopProcessingEvent {
                self.mainRunLoopProcessingFinished.signal()
            }

            if activity == .beforeSources || activity == .afterWaiting {
                self.mainRunLoopEventProcessingDeadline =
                    .now() + .milliseconds(Int(self.anrDurationThresholdMs))
                self.mainRunLoopProcessingStarted.signal()
                isMainRunLoopProcessingEvent = true
            } else {
                isMainRunLoopProcessingEvent = false
            }
        }

        // Start monitoring ANRs immediately.
        self.mainRunLoopEventProcessingDeadline = .now() + .milliseconds(Int(self.anrDurationThresholdMs))
        self.mainRunLoopProcessingStarted.signal()
        isMainRunLoopProcessingEvent = true

        CFRunLoopAddObserver(CFRunLoopGetMain(), observer, .commonModes)
        self.observer = observer

        var thread: pthread_t?
        let run: @convention(c) (UnsafeMutableRawPointer) -> UnsafeMutableRawPointer? = runANRsDetection
        pthread_create(
            &thread, nil, run, Unmanaged<ANRReporter>.passRetained(self).toOpaque()
        )
    }

    func stop() {
        self.isActive.update { $0 = false }
        if let observer = self.observer {
            CFRunLoopObserverInvalidate(observer)
            self.observer = nil
        }
    }
}

/// A global method that acts as a proxy between `ANRDetector.run` method and
///
/// - parameter context: An opaque context that contains a reference to an `ANRDetector` instance.
///
/// - returns: Always `nil`.
private func runANRsDetection(context: UnsafeMutableRawPointer) -> UnsafeMutableRawPointer? {
    let detector = Unmanaged<ANRReporter>.fromOpaque(context).takeRetainedValue()
    Thread.current.name = "io.bitdrift.capture.anr-reporter"
    detector.run()
    return nil
}
