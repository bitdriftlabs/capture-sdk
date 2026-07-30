// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

internal import CaptureLoggerBridge
import Foundation
import MetricKit

/// Abstracts away that a conformer likely relies on OS-version-gated APIs (e.g. `MetricManager`,
/// iOS 27+), so callers can hold and stop one without an `@available` check of their own.
protocol MetricKitDiagnosticManaging: AnyObject {
    func start()
    func stop()
}

@available(iOS 27.0, *)
protocol MetricDiagnosticReportSource {
    var diagnosticReports: any AsyncSequence<DiagnosticReport, Never> { get }
}

@available(iOS 27.0, *)
struct LiveMetricDiagnosticReportSource: MetricDiagnosticReportSource {
    let metricManager: MetricManager

    var diagnosticReports: any AsyncSequence<DiagnosticReport, Never> {
        self.metricManager.diagnosticReports
    }
}

/// Consumes Apple's `MetricManager` (MetricKit, iOS 27+) `diagnosticReports` `AsyncSequence` and
/// writes bitdrift reports from `.crash`/`.hang`/`.memoryException` diagnostics, in parallel with
/// `DiagnosticEventReporter` (which continues to serve the `MXMetricManager` path on iOS <27).
///
/// This class is deliberately self-contained with respect to `DiagnosticEventReporter`: it doesn't
/// share the `NSDictionary`-based enrichment/writer-adapter code with it, so that
/// `DiagnosticEventReporter` can be deleted outright, without any entanglement, once the SDK's
/// minimum supported OS reaches iOS 27. Payload-shape-agnostic pure helpers (signal/exception
/// naming, termination-context parsing) are shared via `MetricKitDiagnosticParsing` instead of
/// being duplicated, since those have no dependency on either payload shape.
@available(iOS 27.0, *)
final class MetricKitDiagnosticManager: MetricKitDiagnosticManaging {
    private enum Constants {
        static let defaultHangName = "App Hang"
        static let defaultOOMExceptionName = "EXC_RESOURCE"
        static let defaultOOMDescription = "Out Of Memory"
        static let sdkID = "io.bitdrift.capture-apple"
    }

    private let outputDir: URL
    private let sdkVersion: String
    private let memoryPressureLevel: MemoryPressureLevel
    private let fileSizeOptimizationEnabled: Bool
    private let useStackOverlapMatching: Bool
    private let crashReporting: any CrashReporting
    private let parsing: MetricKitDiagnosticParsing
    private let crashEnrichmentSummaryHandler: (([String: String]?) -> Void)?
    private let completionHandler: (() -> Void)?
    private let diagnosticSource: any MetricDiagnosticReportSource
    private let fileManager: FileManager

    private var task: Task<Void, Never>?

    init(
        outputDir: URL,
        sdkVersion: String,
        memoryPressureLevel: MemoryPressureLevel,
        fileSizeOptimizationEnabled: Bool,
        useStackOverlapMatching: Bool,
        crashReporting: any CrashReporting,
        parsing: MetricKitDiagnosticParsing = MetricKitDiagnosticParsing(),
        diagnosticSource: any MetricDiagnosticReportSource = LiveMetricDiagnosticReportSource(metricManager: MetricManager()),
        fileManager: FileManager = .default,
        crashEnrichmentSummaryHandler: (([String: String]?) -> Void)? = nil,
        completionHandler: (() -> Void)? = nil
    ) {
        self.outputDir = outputDir
        self.sdkVersion = sdkVersion
        self.memoryPressureLevel = memoryPressureLevel
        self.fileSizeOptimizationEnabled = fileSizeOptimizationEnabled
        self.useStackOverlapMatching = useStackOverlapMatching
        self.crashReporting = crashReporting
        self.parsing = parsing
        self.diagnosticSource = diagnosticSource
        self.fileManager = fileManager
        self.crashEnrichmentSummaryHandler = crashEnrichmentSummaryHandler
        self.completionHandler = completionHandler
    }

    func start() {
        self.task = Task.detached { [weak self] in
            guard let self else {
                return
            }

            for await report in self.diagnosticSource.diagnosticReports {
                self.handle(report)
            }
        }
    }

    func stop() {
        self.task?.cancel()
        self.task = nil
    }

    // MARK: - Dispatch

    private func handle(_ report: DiagnosticReport) {
        guard self.ensureOutputDirectoryExists() else {
            return
        }

        let timestamp = report.timeRange.end.timeIntervalSince1970

        switch report.result {
        case let .crash(diagnostic):
            let capturedCrash = self.crashReporting.cachedPreviousCrash()
            self.processCrash(
                diagnostic,
                environment: report.environment,
                timestamp: timestamp,
                capturedCrash: capturedCrash
            )
        case let .memoryException(diagnostic):
            self.processMemoryException(diagnostic, environment: report.environment, timestamp: timestamp)
        default:
            // Everything else (.hang, .cpuException, .diskWriteException, .appLaunch) isn't
            // captured here; hangs are categorized as watchdog SIGKILLs in processCrash instead.
            break
        }

        self.completionHandler?()
    }

    private func ensureOutputDirectoryExists() -> Bool {
        if self.fileManager.fileExists(atPath: self.outputDir.path) {
            return true
        }
        
        do {
            try self.fileManager.createDirectory(
                at: self.outputDir,
                withIntermediateDirectories: true
            )
        } catch {
            return false
        }
        
        return true
    }

    // MARK: - Crash

    private func processCrash(
        _ diagnostic: CrashDiagnostic,
        environment: DiagnosticReport.Environment,
        timestamp: TimeInterval,
        capturedCrash: BitdriftPreviousCrash?
    ) {
        // The new MetricKit API exposes watchdog terminations directly via `terminationCategory`,
        // unlike the old API where this had to be inferred from exceptionType/signal/terminationReason
        // heuristics (see DiagnosticEventReporter.crashIsHangTermination:). Prefer the clean signal.
        let isHang = diagnostic.terminationCategory == .watchdog
        let effectiveCapturedCrash = isHang ? nil : capturedCrash
        let reportType: ReportType = isHang ? .appNotResponding : .nativeCrash

        var stringPool: [UnsafeMutablePointer<CChar>] = []
        defer {
            for ptr in stringPool {
                free(ptr)
            }
        }

        var handle: UnsafeRawPointer?
        withUnsafeMutablePointer(to: &handle) { handlePtr in
            bdrw_create_buffer_handle(
                handlePtr,
                reportType.rawValue,
                Constants.sdkID,
                self.sdkVersion,
                self.fileSizeOptimizationEnabled
            )

            let name = isHang ? Constants.defaultHangName : self.name(forCrash: diagnostic)
            let reason = self.reason(forCrash: diagnostic, name: name, capturedCrash: effectiveCapturedCrash)

            let adapterDict = MetricKitLegacyCrashAdapter.makeCrashDict(
                diagnostic: diagnostic,
                environment: environment
            )
            
            var summaryOut: NSDictionary?
            let enhanced = self.crashReporting.enhancedMetricKitReport(
                adapterDict,
                useStackOverlapMatching: self.useStackOverlapMatching,
                summaryOut: &summaryOut
            )
            if let summary = summaryOut as? [String: String] {
                self.crashEnrichmentSummaryHandler?(summary)
            }

            let threads = ExtractedThread.create(fromThreadsDictionary: enhanced)
            
            self.serializeErrorThreads(
                handlePtr,
                threads: threads,
                name: name,
                reason: reason,
                order: .innerToOuter,
                stringPool: &stringPool
            )
            
            self.serializeCrashInfo(
                handlePtr,
                threads: threads,
                diagnostic: diagnostic,
                capturedCrash: effectiveCapturedCrash,
                metricTime: timestamp,
                stringPool: &stringPool
            )

            self.serializeAppMetrics(handlePtr, environment: environment, stringPool: &stringPool)
            self.serializeDeviceMetrics(handlePtr, environment: environment, timestamp: timestamp, stringPool: &stringPool)

            self.finishReport(handlePtr, reportType: reportType, timestamp: timestamp)
        }
    }

    // MARK: - Memory exception (new; no equivalent in the old MetricKit API)

    private func processMemoryException(
        _ diagnostic: MemoryExceptionDiagnostic,
        environment: DiagnosticReport.Environment,
        timestamp: TimeInterval
    ) {
        var stringPool: [UnsafeMutablePointer<CChar>] = []
        defer {
            for ptr in stringPool {
                free(ptr)
            }
        }

        var handle: UnsafeRawPointer?
        withUnsafeMutablePointer(to: &handle) { handlePtr in
            bdrw_create_buffer_handle(
                handlePtr,
                ReportType.nativeCrash.rawValue,
                Constants.sdkID,
                self.sdkVersion,
                self.fileSizeOptimizationEnabled
            )

            let threads = self.extractThreads(from: diagnostic.callStackTree)
            self.serializeErrorThreads(
                handlePtr,
                threads: threads,
                name: Constants.defaultOOMExceptionName,
                reason: Constants.defaultOOMDescription,
                order: .outerToInner,
                stringPool: &stringPool
            )

            self.serializeAppMetrics(handlePtr, environment: environment, stringPool: &stringPool)
            self.serializeDeviceMetrics(handlePtr, environment: environment, timestamp: timestamp, stringPool: &stringPool)

            self.finishReport(handlePtr, reportType: .nativeCrash, timestamp: timestamp)
        }
    }

    // MARK: - Report finalization

    private func finishReport(
        _ handle: UnsafeMutablePointer<UnsafeRawPointer?>,
        reportType: ReportType,
        timestamp: TimeInterval
    ) {
        var length: UInt64 = 0
        guard let contents = bdrw_get_completed_buffer(handle, &length) else {
            bdrw_dispose_buffer_handle(handle)
            return
        }

        let data = Data(bytes: contents, count: Int(length))
        let identifier = UUID().uuidString
        let typeName = reportType == .nativeCrash ? "crash" : "anr"
        let filename = "\(Int64(timestamp.rounded(.towardZero)))_\(typeName)_\(identifier).cap"
        let path = self.outputDir.appendingPathComponent(filename).path
        self.fileManager.createFile(atPath: path, contents: data)
        bdrw_dispose_buffer_handle(handle)
    }

    private func extractThreads(from tree: CallStackTree) -> [ExtractedThread] {
        ExtractedThread.create(fromCallStackTree: tree)
    }

    // MARK: - Writer: threads + error

    private func crashedThreadIndex(_ threads: [ExtractedThread]) -> Int {
        if let attributedIndex = threads.firstIndex(where: { $0.attributed }) {
            if !threads[attributedIndex].frames.isEmpty {
                return attributedIndex
            }
        }

        if let firstWithFrames = threads.firstIndex(where: { !$0.frames.isEmpty }) {
            return firstWithFrames
        }

        return 0
    }

    private func serializeErrorThreads(
        _ handle: UnsafeMutablePointer<UnsafeRawPointer?>,
        threads: [ExtractedThread],
        name: String?,
        reason: String?,
        order: FrameOrder,
        stringPool: inout [UnsafeMutablePointer<CChar>]
    ) {
        var registeredImages = Set<String>()
        let crashedIndex = self.crashedThreadIndex(threads)

        for (threadIndex, thread) in threads.enumerated() {
            if thread.frames.isEmpty && threadIndex != crashedIndex {
                continue
            }

            let stackFrames: [BDStackFrame] = thread.frames.map { frame in
                if !registeredImages.contains(frame.binaryUUID) {
                    var image = BDBinaryImage(
                        id: self.cString(frame.binaryUUID, pool: &stringPool),
                        path: self.cString(frame.binaryName, pool: &stringPool),
                        load_address: frame.address - frame.offsetIntoBinaryTextSegment
                    )
                    bdrw_add_binary_image(handle, &image)
                    registeredImages.insert(frame.binaryUUID)
                }

                return BDStackFrame(
                    type_: 2, // FrameType.DWARF
                    frame_address: frame.address,
                    symbol_address: 0,
                    symbol_name: nil,
                    class_name: nil,
                    file_name: nil,
                    line: 0,
                    column: 0,
                    image_id: self.cString(frame.binaryUUID, pool: &stringPool),
                    state_count: 0,
                    state: nil,
                    reg_count: 0,
                    regs: nil
                )
            }

            // Handle differing frame ordering for MXDiagnostic types (FB18377370): insertion order
            // is most recent to oldest.
            let orderedStack = order == .innerToOuter ? stackFrames : Array(stackFrames.reversed())

            var bdThread = BDThread(
                name: self.cString(thread.name, pool: &stringPool),
                state: nil,
                active: threadIndex == crashedIndex,
                index: UInt32(threadIndex),
                priority: 0,
                quality_of_service: -1
            )

            orderedStack.withUnsafeBufferPointer { stackPtr in
                bdrw_add_thread(handle, UInt16(threads.count), &bdThread, UInt32(stackPtr.count), stackPtr.baseAddress)

                if threadIndex == crashedIndex {
                    bdrw_add_error(
                        handle,
                        self.cString(name, pool: &stringPool),
                        self.cString(reason, pool: &stringPool),
                        0,
                        UInt32(stackPtr.count),
                        stackPtr.baseAddress
                    )
                }
            }
        }

        if threads.isEmpty {
            bdrw_add_error(handle, self.cString(name, pool: &stringPool), self.cString(reason, pool: &stringPool), 0, 0, nil)
        }
    }

    // MARK: - Writer: BDAppleCrashInfo (MetricKit side + bitdrift in-process side)

    private func serializeCrashInfo(
        _ handle: UnsafeMutablePointer<UnsafeRawPointer?>,
        threads: [ExtractedThread],
        diagnostic: CrashDiagnostic,
        capturedCrash: BitdriftPreviousCrash?,
        metricTime: TimeInterval,
        stringPool: inout [UnsafeMutablePointer<CChar>]
    ) {
        let metricKitThreadDetails = self.buildCrashInfoThreadDetails(threads: threads, stringPool: &stringPool)
        var payload = self.buildMetricKitPayload(diagnostic, stringPool: &stringPool)

        var seconds: UInt64 = 0
        var nanos: UInt32 = 0
        self.parsing.timestampComponents(for: metricTime, seconds: &seconds, nanos: &nanos)
        metricKitThreadDetails.withThreadDetailsPointer { detailsPtr in
            bdrw_add_apple_crash_info(
                handle,
                CrashReporterScopeValue.outOfProcess.rawValue,
                CrashReporterValue.appleMetricKit.rawValue,
                seconds,
                nanos,
                &payload,
                detailsPtr
            )
        }

        guard
            capturedCrash?.kind == .nsException,
            let nsexception = capturedCrash?.nsexception
        else {
            return
        }

        let capturedFrames = (nsexception.frames as? [BitdriftCrashStackFrame]) ?? []
        self.registerBinaryImages(forCapturedFrames: capturedFrames, handle: handle, stringPool: &stringPool)
        let capturedThreadDetails = self.buildCrashInfoThreadDetails(
            fromCapturedFrames: capturedFrames,
            stringPool: &stringPool
        )

        var capturedPayload = BDAppleCrashInfoPayload()
        capturedPayload.has_nsexception = true
        capturedPayload.nsexception = BDNSException(
            name: self.cString(nsexception.name, pool: &stringPool),
            reason: self.cString(nsexception.reason, pool: &stringPool)
        )

        var capturedSeconds: UInt64 = 0
        var capturedNanos: UInt32 = 0
        self.parsing.timestampComponents(
            for: capturedCrash?.crashDate.timeIntervalSince1970 ?? 0,
            seconds: &capturedSeconds,
            nanos: &capturedNanos
        )
        capturedThreadDetails.withThreadDetailsPointer { detailsPtr in
            bdrw_add_apple_crash_info(
                handle,
                CrashReporterScopeValue.inProcess.rawValue,
                CrashReporterValue.appleBitdriftCrashReporter.rawValue,
                capturedSeconds,
                capturedNanos,
                &capturedPayload,
                detailsPtr
            )
        }
    }

    /// Owns the `BDStackFrame`/`BDCrashInfoThread` buffers for one `bdrw_add_apple_crash_info` call.
    /// C strings referenced by these buffers come from the shared `stringPool` and are freed there.
    private final class CrashInfoThreadDetailsStorage {
        private var stacks: [[BDStackFrame]] = []
        private var threads: [BDThread] = []

        func addThread(_ thread: BDThread, stack: [BDStackFrame]) {
            self.stacks.append(stack)
            self.threads.append(thread)
        }

        /// Calls `body` with a valid `BDCrashInfoThreadDetails*` (or `nil` if there are no threads),
        /// keeping every backing buffer alive for the duration of the call.
        func withThreadDetailsPointer(_ body: (UnsafePointer<BDCrashInfoThreadDetails>?) -> Void) {
            guard !self.threads.isEmpty else {
                body(nil)
                return
            }

            self.bindStacks(index: 0, entries: []) { entries in
                entries.withUnsafeBufferPointer { entriesPtr in
                    var details = BDCrashInfoThreadDetails(
                        count: UInt16(entries.count),
                        threads_count: UInt(entries.count),
                        threads: entriesPtr.baseAddress
                    )
                    withUnsafePointer(to: &details) { body($0) }
                }
            }
        }

        private func bindStacks(
            index: Int,
            entries: [BDCrashInfoThread],
            _ completion: ([BDCrashInfoThread]) -> Void
        ) {
            if index == self.stacks.count {
                completion(entries)
                return
            }

            self.stacks[index].withUnsafeBufferPointer { stackPtr in
                var nextEntries = entries
                nextEntries.append(BDCrashInfoThread(
                    thread: self.threads[index],
                    stack_count: UInt32(stackPtr.count),
                    stack: stackPtr.baseAddress
                ))
                self.bindStacks(index: index + 1, entries: nextEntries, completion)
            }
        }
    }

    private func buildCrashInfoThreadDetails(
        threads: [ExtractedThread],
        stringPool: inout [UnsafeMutablePointer<CChar>]
    ) -> CrashInfoThreadDetailsStorage {
        let storage = CrashInfoThreadDetailsStorage()
        guard !threads.isEmpty else {
            return storage
        }

        let crashedIndex = self.crashedThreadIndex(threads)

        for (threadIndex, thread) in threads.enumerated() where !thread.frames.isEmpty {
            // Binary images aren't registered here: this always runs right after
            // serializeErrorThreads over the same threads, which already registered every image.
            let stack: [BDStackFrame] = thread.frames.map { frame in
                BDStackFrame(
                    type_: 2,
                    frame_address: frame.address,
                    symbol_address: 0,
                    symbol_name: nil,
                    class_name: nil,
                    file_name: nil,
                    line: 0,
                    column: 0,
                    image_id: self.cString(frame.binaryUUID, pool: &stringPool),
                    state_count: 0,
                    state: nil,
                    reg_count: 0,
                    regs: nil
                )
            }

            let bdThread = BDThread(
                name: self.cString(thread.name, pool: &stringPool),
                state: nil,
                active: threadIndex == crashedIndex,
                index: UInt32(threadIndex),
                priority: 0,
                quality_of_service: -1
            )
            storage.addThread(bdThread, stack: stack)
        }

        return storage
    }

    private func buildCrashInfoThreadDetails(
        fromCapturedFrames frames: [BitdriftCrashStackFrame],
        stringPool: inout [UnsafeMutablePointer<CChar>]
    ) -> CrashInfoThreadDetailsStorage {
        let storage = CrashInfoThreadDetailsStorage()
        let stack: [BDStackFrame] = frames.compactMap { frame in
            guard let imageID = frame.imageID else {
                return nil
            }
            return BDStackFrame(
                type_: 2,
                frame_address: frame.frameAddress,
                symbol_address: 0,
                symbol_name: nil,
                class_name: nil,
                file_name: nil,
                line: 0,
                column: 0,
                image_id: self.cString(imageID, pool: &stringPool),
                state_count: 0,
                state: nil,
                reg_count: 0,
                regs: nil
            )
        }

        guard !stack.isEmpty else {
            return storage
        }

        let bdThread = BDThread(name: nil, state: nil, active: true, index: 0, priority: 0, quality_of_service: -1)
        storage.addThread(bdThread, stack: stack)
        return storage
    }

    private func registerBinaryImages(
        forCapturedFrames frames: [BitdriftCrashStackFrame],
        handle: UnsafeMutablePointer<UnsafeRawPointer?>,
        stringPool: inout [UnsafeMutablePointer<CChar>]
    ) {
        var seenImages = Set<String>()
        for frame in frames {
            guard let imageID = frame.imageID, let binaryName = frame.binaryName, !seenImages.contains(imageID) else {
                continue
            }

            var image = BDBinaryImage(
                id: self.cString(imageID, pool: &stringPool),
                path: self.cString(binaryName, pool: &stringPool),
                load_address: frame.imageLoadAddress
            )
            bdrw_add_binary_image(handle, &image)
            seenImages.insert(imageID)
        }
    }

    private func buildMetricKitPayload(
        _ diagnostic: CrashDiagnostic,
        stringPool: inout [UnsafeMutablePointer<CChar>]
    ) -> BDAppleCrashInfoPayload {
        var payload = BDAppleCrashInfoPayload()

        if let exceptionType = diagnostic.exceptionType {
            payload.has_mach_exception = true
            payload.mach_exception = BDMachException(
                type_: UInt32(exceptionType),
                code: diagnostic.exceptionCode ?? 0,
                subcode: 0
            )
        }

        if let signal = diagnostic.signal {
            payload.has_posix_signal = true
            payload.posix_signal = BDPosixSignal(
                number: Int32(signal),
                code: 0,
                errno_value: 0,
                has_fault_address: false,
                fault_address: 0
            )
        }

        let terminationReason = diagnostic.terminationReason?.rawValue
        var terminationContext: [String: String] = [:]
        if diagnostic.signal == Int(SIGKILL) {
            terminationContext = self.parsing.parseTerminationContext(terminationReason ?? "")
        }

        if diagnostic.signal == Int(SIGKILL), (terminationReason?.isEmpty == false || !terminationContext.isEmpty) {
            payload.has_termination = true
            payload.termination = BDAppleTermination(
                domain: self.cString(terminationContext["domain"], pool: &stringPool),
                code: self.cString(terminationContext["code"], pool: &stringPool),
                explanation: self.cString(terminationContext["explanation"], pool: &stringPool),
                process_visibility: self.cString(terminationContext["process_visibility"], pool: &stringPool),
                process_state: self.cString(terminationContext["process_state"], pool: &stringPool),
                watchdog_event: self.cString(terminationContext["watchdog_event"], pool: &stringPool),
                watchdog_visibility: self.cString(terminationContext["watchdog_visibility"], pool: &stringPool)
            )
        }

        return payload
    }

    // MARK: - App / device metrics

    private func serializeAppMetrics(
        _ handle: UnsafeMutablePointer<UnsafeRawPointer?>,
        environment: DiagnosticReport.Environment,
        stringPool: inout [UnsafeMutablePointer<CChar>]
    ) {
        let bundleVersion = "\(environment.applicationVersion).\(environment.applicationBuildVersion)"
        var app = BDAppMetrics(
            app_id: self.cString(environment.bundleIdentifier, pool: &stringPool),
            version: self.cString(environment.applicationVersion, pool: &stringPool),
            version_code: 0,
            cf_bundle_version: self.cString(bundleVersion, pool: &stringPool),
            running_state: nil,
            memory_used: 0,
            memory_free: 0,
            memory_total: 0,
            memory_pressure_level: self.memoryPressureLevel.rawValue,
            region_format: self.cString(environment.regionFormat, pool: &stringPool)
        )
        bdrw_add_app(handle, &app)
    }

    private func serializeDeviceMetrics(
        _ handle: UnsafeMutablePointer<UnsafeRawPointer?>,
        environment: DiagnosticReport.Environment,
        timestamp: TimeInterval,
        stringPool: inout [UnsafeMutablePointer<CChar>]
    ) {
        var seconds: UInt64 = 0
        var nanos: UInt32 = 0
        self.parsing.timestampComponents(for: timestamp, seconds: &seconds, nanos: &nanos)

        var device = BDDeviceMetrics(
            time_seconds: seconds,
            time_nanos: nanos,
            timezone: nil,
            manufacturer: nil,
            model: self.cString(environment.deviceType, pool: &stringPool),
            os_version: self.cString(environment.osVersion.number, pool: &stringPool),
            os_brand: self.cString(environment.osVersion.platform, pool: &stringPool),
            os_fingerprint: nil,
            os_kernversion: self.cString(environment.osVersion.buildNumber, pool: &stringPool),
            power_state: 0,
            power_charge_percent: 0,
            network_state: 0,
            architecture: self.parsing.architectureConstant(for: environment.platformArchitecture),
            display_height: 0,
            display_width: 0,
            display_density_dpi: 0,
            platform: 0,
            rotation: 0,
            cpu_abi_count: 0,
            cpu_abis: nil,
            low_power_mode_enabled: environment.lowPowerModeEnabled
        )
        bdrw_add_device(handle, &device)
    }

    // MARK: - Crash naming

    private func name(forCrash diagnostic: CrashDiagnostic) -> String? {
        self.parsing.name(forExceptionType: Int32(diagnostic.exceptionType ?? 0))
            ?? self.parsing.name(forSignal: Int32(diagnostic.signal ?? 0))
    }

    private func reason(
        forCrash diagnostic: CrashDiagnostic,
        name: String?,
        capturedCrash: BitdriftPreviousCrash?
    ) -> String? {
        var components: [String] = []
        var hasMetricKitExceptionReason = false

        if let exceptionReason = diagnostic.exceptionReason {
            hasMetricKitExceptionReason = true
            components.append("\(exceptionReason.exceptionName): \(exceptionReason.composedMessage)")
        }

        if !hasMetricKitExceptionReason,
           capturedCrash?.kind == .nsException,
           let nsexception = capturedCrash?.nsexception,
           let reason = nsexception.reason {
            components.append("\(nsexception.name): \(reason)")
        }

        if let terminationReason = diagnostic.terminationReason?.rawValue, !terminationReason.isEmpty {
            components.append(terminationReason)
        }

        if let vmRegionInfo = diagnostic.virtualMemoryRegionInfo {
            components.append(vmRegionInfo)
        }

        if !components.isEmpty {
            return components.joined(separator: ".\n")
        }

        let signalName = self.parsing.name(forSignal: Int32(diagnostic.signal ?? 0))
        if let exceptionCode = diagnostic.exceptionCode {
            return "code: \(exceptionCode), signal: \(signalName ?? "unknown")"
        }

        let reason = signalName ?? "unknown"
        return reason == name ? nil : reason
    }

    // MARK: - Small helpers

    /// Copies `string` into a heap-allocated, NUL-terminated C string owned by `pool`, freed by the
    /// caller once the `bdrw_*` calls that consumed it are done. Returns `nil` for a `nil` input so
    /// optional `BDThread.name`-style fields stay `nil` rather than pointing at "".
    private func cString(_ string: String?, pool: inout [UnsafeMutablePointer<CChar>]) -> UnsafePointer<CChar>? {
        guard let string else {
            return nil
        }
        guard let ptr = strdup(string) else {
            return nil
        }
        pool.append(ptr)
        return UnsafePointer(ptr)
    }
}
