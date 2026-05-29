// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
@testable import CaptureLoggerBridge
@testable import CaptureMocks
import XCTest

class DispatchSourceMemoryMonitorTests: XCTestCase {
    private var memoryPressureProvider: MockMemoryPressureProvider!
    private let logger: MockCoreLogging = MockCoreLogging()

    private var sut: DispatchSourceMemoryMonitor!

    override func setUp() {
        memoryPressureProvider = MockMemoryPressureProvider()
    }

    func testOnInitSetsEventHandler() {
        givenDispatchSourceMemoryMonitor()
        thenEventHandlerIsSet()
    }

    func testOnStartActivatesMemoryPressureProvider() {
        givenDispatchSourceMemoryMonitor()
        whenInvokingStart()
        thenMemoryPressureProviderIsActivated()
    }

    func testOnStopCancelsMemoryPressureProvider() {
        givenDispatchSourceMemoryMonitor()
        whenInvokingStop()
        thenMemoryPressureProviderIsCanceled()
    }

    func testOnReceivingMemoryPressureEventLogsMemoryPresureEvent() throws {
        givenDispatchSourceMemoryMonitor()
        whenHandlerReceivesMemoryPressureEvent()
        try thenLoggerLogsAppMemPressureEvent()
    }

    func testOnReceivingMemoryPressureEventNotifiesMemoryPressure() {
        givenDispatchSourceMemoryMonitor()
        let randomEvent: DispatchSource.MemoryPressureEvent = [
            .critical,
            .warning,
            .normal,
            .all,
        ].randomElement()!
        whenHandlerReceivesMemoryPressureEvent(randomEvent)
        thenLoggerInvokesNotifyMemoryPressure(MemoryPressureLevel.from(randomEvent))
    }

    func testRuntimeOffDisablesMemoryPressureLogging() {
        givenDispatchSourceMemoryMonitor(enabled: false)
        whenHandlerReceivesMemoryPressureEvent()
        thenLoggerDoesntLogsAppMemPressureEvent()
        thenLoggerDoesntInvokeNotifyMemoryPressure()
    }

    func testMemoryPressureProviderIsCanceledDisablesMemoryPressureLogging() {
        givenDispatchSourceMemoryMonitor()
        givenMemoryPressureProviderIsCanceled()
        whenHandlerReceivesMemoryPressureEvent()
        thenLoggerDoesntLogsAppMemPressureEvent()
        thenLoggerDoesntInvokeNotifyMemoryPressure()
    }
}

private extension DispatchSourceMemoryMonitorTests {
    func givenDispatchSourceMemoryMonitor(
        enabled: Bool = true
    ) {
        logger.mockRuntimeVariable(.memoryStateChangeReporting, with: enabled)
        sut = DispatchSourceMemoryMonitor(
            logger: logger,
            dispatchSource: memoryPressureProvider,
            snapshotProvider: .init()
        )
    }

    func givenMemoryPressureProviderIsCanceled() {
        memoryPressureProvider.isCancelled = true
    }

    func whenInvokingStart() {
        sut.start()
    }

    func whenInvokingStop() {
        sut.stop()
    }

    func whenHandlerReceivesMemoryPressureEvent(_ memoryPressureEvent: DispatchSource.MemoryPressureEvent = .warning) {
        memoryPressureProvider.simulatePressureEvent(memoryPressureEvent)
    }

    func thenLoggerLogsAppMemPressureEvent() throws {
        let log = try XCTUnwrap(logger.logs.first {
            $0.message == "AppMemPressure"
        })
        XCTAssertEqual(log.type, .lifecycle)
        XCTAssertEqual(log.level, .info)
        XCTAssertNil(log.error)
        XCTAssertNotNil(log.fields!["_mem_level"])
    }

    func thenLoggerDoesntLogsAppMemPressureEvent() {
        XCTAssertFalse(logger.logs.contains { $0.message == "AppMemPressure" })
    }

    func thenLoggerDoesntInvokeNotifyMemoryPressure() {
        XCTAssertFalse(logger.didNotifyMemoryPressure)
    }

    func thenLoggerInvokesNotifyMemoryPressure(_ memoryLevel: MemoryPressureLevel) {
        XCTAssertTrue(logger.didNotifyMemoryPressure)
        XCTAssertEqual(logger.notifyMemoryPressureValue, memoryLevel)
    }

    func thenMemoryPressureProviderIsCanceled() {
        XCTAssertTrue(memoryPressureProvider.didCancel)
    }

    func thenMemoryPressureProviderIsActivated() {
        XCTAssertTrue(memoryPressureProvider.didActivate)
    }

    func thenEventHandlerIsSet() {
        XCTAssertTrue(memoryPressureProvider.didCallSetEventHandler)
    }
}
