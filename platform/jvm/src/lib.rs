// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

// Thin cdylib wrapper around capture-core that exports JNI functions
// This allows capture-core to be an rlib (for tests) while capture is a cdylib (for production)

// Re-export everything from capture-core
pub use capture_core::*;
