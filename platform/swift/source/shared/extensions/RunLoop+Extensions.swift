// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation

extension RunLoop {
    // Adds a timer to the run loop and triggers an execution of timer's action in an async way.
    //
    // - parameter timer: Timer to add to the run loop and fire.
    // - parameter mode:  A run loop mode to use for scheduling timer.
    func fireAndAdd(_ timer: Timer, forMode mode: RunLoop.Mode) {
        RunLoop.main.add(timer, forMode: mode)
        // Fire initial timer's action in an async way immediately upon method call.
        // The trigger happens in an async way to help with cases when Capture SDK is initialized
        // during an app launch. For cases like this, we want to defer timer's action to the next
        // run loop (or later) to reduce's framework impact on app launch and to make sure that
        // the app is done initializing itself (i.e., view hierarchy is set up).
        RunLoop.main.perform(inModes: [.default]) { [weak timer] in
            timer?.fire()
        }
    }
}
