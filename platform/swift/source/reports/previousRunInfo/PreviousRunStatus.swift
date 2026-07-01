// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/// Deterministic launch-time status for the previous app run.
public enum PreviousRunStatus: String, Equatable {
    /// The app received a termination notification before dying.
    case cleanExit

    /// The in-process crash reporter captured a fatal crash during the previous run. Some termination
    /// causes aren't caught by these handlers (e.g. watchdog SIGKILLs).
    case fatalCrash

    /// The app or its binary changed since the last launch (e.g. an update was installed).
    case appUpdate

    /// The OS version changed since the last launch.
    case osUpdate

    /// The previous run had a debugger attached. Only reported in debug builds.
    case debuggerAttached

    /// The previous run's status couldn't be determined at startup.
    case unknown
}
