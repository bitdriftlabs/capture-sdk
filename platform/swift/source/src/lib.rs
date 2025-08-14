// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![deny(
  clippy::expect_used,
  clippy::panic,
  clippy::todo,
  clippy::unimplemented,
  clippy::unreachable,
  clippy::unwrap_used
)]

#[macro_use]
extern crate objc;

pub mod bridge;
pub mod conversion;
pub mod crash_report;
pub mod events;
pub mod ffi;
pub mod key_value_storage;
pub mod resource_utilization;
mod session;
pub mod session_replay;
