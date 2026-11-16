// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// The only declarations of foreign functions and layouts. Each one is wrapped in a safe function
// next to it, so the rest of the crate makes no foreign calls of its own.

pub mod mach;
pub mod task_info;
