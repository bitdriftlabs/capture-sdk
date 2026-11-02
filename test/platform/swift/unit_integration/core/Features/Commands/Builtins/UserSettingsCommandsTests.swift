// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@_spi(ExperimentalAPI) @testable import Capture
import XCTest

class UserSettingsCommandsTestCase: XCTestCase {
    fileprivate let standard = UserDefaults.standard
    fileprivate let special = UserDefaults(suiteName: "special")!

    override func tearDown() {
        standard.removePersistentDomain(forName: Bundle.main.bundleIdentifier!)
        standard.removePersistentDomain(forName: "special")
    }
}

final class UserSettingsCommandsGetTests: UserSettingsCommandsTestCase {
    private let sut = UserSettingsCommands.defaultsGet

    func testGetNonexistentValue() throws {
        let result = sut(["key": "a".asArgument()])
        XCTAssertEqual(result, Result.failure(CommandError(title: "Unable to fetch value", description: "key 'a' is not set")))
    }

    func testGetNonexistentValueSpecificSuite() throws {
        UserDefaults(suiteName: "special")?.set("x", forKey: "3")
        let result = sut(["key": "a".asArgument(), "suite_name": "other".asArgument()])
        XCTAssertEqual(result, Result.failure(CommandError(title: "Unable to fetch value", description: "key 'a' is not set")))
    }

    func testGetInvalidArguments() throws {
        for args in [["key": false.asArgument()], [:]] {
            XCTContext.runActivity(named: "run get() with args \(args) expecting invalid argument error") { _ in
                XCTAssertEqual(sut(args), Result.failure(CommandError.invalidArguments(description: "must specify string <key>")))
            }
        }
    }

    func testGetExistingValue() throws {
        for (input, output): (Any, String) in [
            ("abc", "abc"),
            (-21, "-21"),
            (45.4333, "45.4333"),
            (false, "0"),
            (true, "1"),
        ] {
            XCTContext.runActivity(named: "run get() with inserted value \(input) expecting \(output)") { _ in
                standard.set(input, forKey: "a")
                XCTAssertEqual(sut(["key": "a".asArgument()]), Result.success(CommandResult(context: ["a": output])))
            }
        }
    }

    func testGetSpecificSuite() throws {
        special.set("x", forKey: "3")
        XCTAssertEqual(sut(["key": "3".asArgument(), "suite_name": "special".asArgument()]), Result.success(CommandResult(context: ["3": "x"])))
    }
}

final class UserSettingsCommandSetTests: UserSettingsCommandsTestCase {
    private let sut = UserSettingsCommands.defaultsSet

    func testSetInvalidArguments() throws {
        for (args, description) in [
            ([:], "must specify string <key>"),
            (["key": Double(49).asArgument()], "must specify string <key>"),
            (["key": "b".asArgument()], "must specify <value> and string <key> to set"),
            (["value": Int64(9).asArgument()], "must specify string <key>"),
        ] {
            XCTContext.runActivity(named: "run set() command with args: \(args) expects error '\(description)'") { _ in
                XCTAssertEqual(sut(args), Result.failure(CommandError.invalidArguments(description: description)))
            }
        }
    }

    func testSet() throws {
        for value: any CommandArgumentConvertible in [Int64(-0), Double(84), UInt64(9016378), "abc", false, true, "oh no".data(using: .utf8)!] {
            XCTContext.runActivity(named: "run set() for \(value.self)-type value") { _ in
                XCTAssertEqual(sut(["key": "c".asArgument(), "value": value.asArgument()]), Result.success(CommandResult()))
                AssertAnyEqual(value, standard.value(forKey: "c"))
            }
        }
    }

    func testSetSpecificSuite() throws {
        for value: any CommandArgumentConvertible in [Int64(-0), Double(84), UInt64(9016378), "abc", false, true, "oh no".data(using: .utf8)!] {
            XCTContext.runActivity(named: "run set() for \(value.self)-type value") { _ in
                let result = sut(["key": "c".asArgument(), "value": value.asArgument(), "suite_name": "special".asArgument()])
                XCTAssertEqual(result, Result.success(CommandResult()))
                AssertAnyEqual(value, special.value(forKey: "c"))
            }
        }
    }
}

final class UserSettingsCommandRemoveTests: UserSettingsCommandsTestCase {
    private let sut = UserSettingsCommands.defaultsRemove

    func testRemoveInvalidArguments() throws {
        for args in [["key": 993.asArgument()], [:]] {
            XCTAssertEqual(sut(args), Result.failure(CommandError.invalidArguments(description: "must specify string <key>")))
        }
    }

    func testRemove() throws {
        standard.set(false, forKey: "a")
        standard.set(40, forKey: "b")

        XCTAssertEqual(sut(["key": "nonexistent".asArgument()]), Result.success(CommandResult()))
        AssertAnyEqual(false, standard.value(forKey: "a"))
        AssertAnyEqual(40, standard.value(forKey: "b"))

        XCTAssertEqual(sut(["key": "b".asArgument()]), Result.success(CommandResult()))
        AssertAnyEqual(false, standard.value(forKey: "a"))
        XCTAssertNil(standard.value(forKey: "b"))
    }

    func testRemoveSpecificSuite() throws {
        standard.set(false, forKey: "a")
        standard.set(40, forKey: "b")
        special.set("yes", forKey: "c")

        XCTAssertEqual(sut(["key": "nonexistent".asArgument(), "suite_name": "special".asArgument()]), Result.success(CommandResult()))
        AssertAnyEqual(false, standard.value(forKey: "a"))
        AssertAnyEqual(40, standard.value(forKey: "b"))
        AssertAnyEqual("yes", special.value(forKey: "c"))

        XCTAssertEqual(sut(["key": "c".asArgument()]), Result.success(CommandResult()))
        AssertAnyEqual(false, standard.value(forKey: "a"))
        AssertAnyEqual(40, standard.value(forKey: "b"))
        AssertAnyEqual("yes", special.value(forKey: "c"))

        XCTAssertEqual(sut(["key": "c".asArgument(), "suite_name": "special".asArgument()]), Result.success(CommandResult()))
        AssertAnyEqual(false, standard.value(forKey: "a"))
        AssertAnyEqual(40, standard.value(forKey: "b"))
        XCTAssertNil(special.value(forKey: "c"))
    }
}

final class UserSettingsCommandsClearTests: UserSettingsCommandsTestCase {
    private let sut = UserSettingsCommands.defaultsClear

    override func setUp() {
        standard.set(false, forKey: "a")
        standard.set(40, forKey: "b")
        special.set("yes", forKey: "c")

        // pathological checks
        AssertAnyEqual(false, standard.value(forKey: "a"))
        AssertAnyEqual(40, standard.value(forKey: "b"))
        AssertAnyEqual("yes", special.value(forKey: "c"))
    }

    func testDefaultsClear() throws {
        XCTAssertEqual(sut([:]), Result.success(CommandResult()))
        XCTAssertNil(standard.value(forKey: "a"))
        XCTAssertNil(standard.value(forKey: "b"))
        AssertAnyEqual("yes", special.value(forKey: "c"))
    }

    func testDefaultsClearSpecificSuite() throws {
        XCTAssertEqual(sut(["suite_name": "special".asArgument()]), Result.success(CommandResult()))
        AssertAnyEqual(false, standard.value(forKey: "a"))
        AssertAnyEqual(40, standard.value(forKey: "b"))
        XCTAssertNil(special.value(forKey: "c"))
    }
}
