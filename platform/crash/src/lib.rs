// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

//! Crash reporter support for capture-sdk.
//!
//! The crate persists a small crash record into an `mmap`-backed file so the next launch can
//! inspect whether the previous run terminated while an installed crash monitor was active.
//! Today the only supported crash kind is uncaught `NSException` on Apple platforms.

#![deny(
  clippy::expect_used,
  clippy::panic,
  clippy::todo,
  clippy::unimplemented,
  clippy::unreachable,
  clippy::unwrap_used
)]

mod coordinator;
mod ffi;
mod monitors;
mod previous;
mod schema;
mod store;
#[cfg(test)]
mod test_support;
mod writer;
