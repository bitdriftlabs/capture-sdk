// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Capture
@testable import LoggerBenchmarks
import SwiftUI

struct Config {
    var isClockTimeProfilingRunning = false
    var isSimpleConfigBenchmarkRunning = false
    var isDefaultConfigBenchmarkRunning = false

    var isRunning: Bool {
        self.isClockTimeProfilingRunning
            || self.isSimpleConfigBenchmarkRunning
            || self.isDefaultConfigBenchmarkRunning
    }
}

struct ContentView: View {
    private let clockTimeProfiler = ClockTimeProfiler()
    private let resourceProfiler = ResourceProfiler()

    @State var config: Config = Config()

    var body: some View {
        VStack {
            Spacer()
            VStack {
                Text("ACTIONSsss")
                Button(action: { self.clockTimeProfiler.run() }) {
                    Text(
                        self.config.isClockTimeProfilingRunning
                            ? "Running..."
                            : "Run Clock Time Profiler"
                    ).frame(maxWidth: .infinity)
                }
                .disabled(self.config.isRunning)
                Button(action: {
                    if self.config.isSimpleConfigBenchmarkRunning {
                        self.resourceProfiler.stop()
                    } else {
                        self.resourceProfiler
                            .startSimple(isRunning: self.$config.isSimpleConfigBenchmarkRunning)
                    }
                }) {
                    Text(
                        self.config.isSimpleConfigBenchmarkRunning
                            ? "Stop"
                            : "Run \"Simple\" Config Benchmark"
                    ).frame(maxWidth: .infinity)
                }
                .disabled(self.config.isRunning && !self.config.isSimpleConfigBenchmarkRunning)
                Button(action: {
                    if self.config.isDefaultConfigBenchmarkRunning {
                        self.resourceProfiler.stop()
                    } else {
                        self.resourceProfiler
                            .startDefault(isRunning: self.$config.isDefaultConfigBenchmarkRunning)
                    }
                }) {
                    Text(
                        self.config.isDefaultConfigBenchmarkRunning
                            ? "Stop"
                            : "Run \"Default\" Config Benchmark"
                    ).frame(maxWidth: .infinity)
                }
                .disabled(self.config.isRunning && !self.config.isDefaultConfigBenchmarkRunning)
                RepresentedSessionReplayBenchmarkView()
            }
            .padding(20)
            .modify { view in
                if #available(iOS 15, *) {
                    view.buttonStyle(.bordered)
                }
            }
            Spacer()
        }
        .padding(5)
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}

extension View {
    func modify(@ViewBuilder _ modifier: (Self) -> some View) -> some View {
        return modifier(self)
    }
}
