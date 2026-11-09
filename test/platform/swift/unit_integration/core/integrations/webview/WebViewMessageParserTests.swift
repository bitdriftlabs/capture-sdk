// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@testable import Capture
import XCTest

final class WebViewMessageParserTests: XCTestCase {
    func testDecodeDispatchesEachTypeToItsConcreteMessageType() throws {
        for testCase in self.makeCatalogTestCases() {
            let message = try givenDecodedMessage(from: testCase.json)
            thenMessage(message, isOfType: testCase.expectedType, forType: testCase.type)
        }
    }

    func testDecodeWithNonStringBodyThrowsInvalidBody() throws {
        do {
            _ = try WebViewMessageParser.decode(from: NSNull())
            XCTFail("expected decode to throw")
        } catch WebViewMessageParser.ParsingError.invalidBody(let typeDescription) {
            XCTAssertEqual(typeDescription, String(describing: NSNull.self))
        }
    }

    func testDecodeWithMissingTypeKeyThrowsDecodingError() throws {
        XCTAssertThrowsError(try WebViewMessageParser.decode(from: "{}")) { error in
            XCTAssertTrue(error is DecodingError)
        }
    }

    func testDecodeWithUnknownTypeValueThrowsDecodingError() throws {
        let json = """
        {"tag":"bitdrift-webview-sdk","v":1,"type":"unknownType","timestamp":1}
        """
        XCTAssertThrowsError(try WebViewMessageParser.decode(from: json)) { error in
            XCTAssertTrue(error is DecodingError)
        }
    }

    func testDecodeWithMalformedJSONThrowsDecodingError() throws {
        XCTAssertThrowsError(try WebViewMessageParser.decode(from: "not json")) { error in
            XCTAssertTrue(error is DecodingError)
        }
    }
}

private extension WebViewMessageParserTests {
    struct CatalogTestCase {
        let type: WebViewMessageType
        let json: String
        let expectedType: WebViewMessage.Type
    }

    func givenDecodedMessage(from json: String) throws -> any WebViewMessage {
        try WebViewMessageParser.decode(from: json)
    }

    func thenMessage(
        _ message: any WebViewMessage,
        isOfType expectedType: WebViewMessage.Type,
        forType type: WebViewMessageType
    ) {
        XCTAssertTrue(
            Swift.type(of: message) == expectedType,
            "expected \(type) to decode to \(expectedType), got \(Swift.type(of: message))"
        )
    }

    // swiftlint:disable:next function_body_length
    func makeCatalogTestCases() -> [CatalogTestCase] {
        [
            CatalogTestCase(
                type: .customLog,
                json: """
                {"tag":"t","v":1,"type":"customLog","timestamp":1,"level":"info","message":"m"}
                """,
                expectedType: CustomLogMessage.self
            ),
            CatalogTestCase(
                type: .bridgeReady,
                json: """
                {"tag":"t","v":1,"type":"bridgeReady","timestamp":1,"url":"https://example.com","instrumentationConfig":null}
                """,
                expectedType: BridgeReadyMessage.self
            ),
            CatalogTestCase(
                type: .webVital,
                json: """
                {"tag":"t","v":1,"type":"webVital","timestamp":1,"metric":\
                {"name":"CLS","value":0,"rating":"good","delta":0,"id":"i","navigationType":"navigate","entries":[]},\
                "parentSpanId":null,"url":null}
                """,
                expectedType: WebVitalMessage.self
            ),
            CatalogTestCase(
                type: .networkRequest,
                json: """
                {"tag":"t","v":1,"type":"networkRequest","timestamp":1,"requestId":"r","method":"GET",\
                "url":"https://example.com","statusCode":200,"durationMs":1,"success":true,"error":null,\
                "requestType":"fetch","timing":null}
                """,
                expectedType: NetworkRequestMessage.self
            ),
            CatalogTestCase(
                type: .navigation,
                json: """
                {"tag":"t","v":1,"type":"navigation","timestamp":1,"fromUrl":"a","toUrl":"b","method":"pushState"}
                """,
                expectedType: NavigationMessage.self
            ),
            CatalogTestCase(
                type: .pageView,
                json: """
                {"tag":"t","v":1,"type":"pageView","timestamp":1,"action":"start","spanId":"s",\
                "url":"https://example.com","reason":"navigation","durationMs":null}
                """,
                expectedType: PageViewMessage.self
            ),
            CatalogTestCase(
                type: .lifecycle,
                json: """
                {"tag":"t","v":1,"type":"lifecycle","timestamp":1,"event":"load","performanceTime":1,\
                "visibilityState":null}
                """,
                expectedType: LifecycleMessage.self
            ),
            CatalogTestCase(
                type: .error,
                json: """
                {"tag":"t","v":1,"type":"error","timestamp":1,"name":"Error","message":"m","stack":null,\
                "filename":null,"lineno":null,"colno":null}
                """,
                expectedType: ErrorMessage.self
            ),
            CatalogTestCase(
                type: .longTask,
                json: """
                {"tag":"t","v":1,"type":"longTask","timestamp":1,"durationMs":1,"startTime":1,"attribution":null}
                """,
                expectedType: LongTaskMessage.self
            ),
            CatalogTestCase(
                type: .resourceError,
                json: """
                {"tag":"t","v":1,"type":"resourceError","timestamp":1,"resourceType":"image","url":"u",\
                "tagName":"img"}
                """,
                expectedType: ResourceErrorMessage.self
            ),
            CatalogTestCase(
                type: .console,
                json: """
                {"tag":"t","v":1,"type":"console","timestamp":1,"level":"info","message":"m","args":null}
                """,
                expectedType: ConsoleMessage.self
            ),
            CatalogTestCase(
                type: .promiseRejection,
                json: """
                {"tag":"t","v":1,"type":"promiseRejection","timestamp":1,"reason":"r","stack":null}
                """,
                expectedType: PromiseRejectionMessage.self
            ),
            CatalogTestCase(
                type: .userInteraction,
                json: """
                {"tag":"t","v":1,"type":"userInteraction","timestamp":1,"interactionType":"click",\
                "tagName":"div","elementId":null,"className":null,"textContent":null,"isClickable":false,\
                "clickCount":null,"timeWindowMs":null,"duration":null}
                """,
                expectedType: UserInteractionMessage.self
            ),
            CatalogTestCase(
                type: .internalAutoInstrumentation,
                json: """
                {"tag":"t","v":1,"type":"internalAutoInstrumentation","timestamp":1,"event":"captureErrors"}
                """,
                expectedType: InternalAutoInstrumentationMessage.self
            ),
        ]
    }
}
