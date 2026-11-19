// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Benchmark
@testable import LoggerBenchmarks
import XCTest

final class BenchmarkTests: XCTestCase {
    func testBenchmarks() {
        // This does not benchmark anything, it just tests that our benchmarks
        // still compile & can run without crasing or running into fatal issues.
        let profiler = ClockTimeProfiler()
        runTests(suites: profiler.suites)
    }
}
