// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
//! Bounded stack unwinding over caller-owned registers, memory and output buffers.
//!
//! This crate neither suspends threads nor installs crash monitors. The caller must keep the
//! captured memory consistent and choose a reader suitable for its execution context. No allocator,
//! logging, lazy initialization or synchronization is used by the unwinder.

#![no_std]
#![deny(
  clippy::expect_used,
  clippy::panic,
  clippy::todo,
  clippy::unimplemented,
  clippy::unreachable,
  clippy::unwrap_used
)]

pub mod aarch64;
#[cfg(target_vendor = "apple")]
pub mod apple;
pub mod image;
pub mod memory;

#[cfg(test)]
extern crate std;

#[cfg(test)]
mod test_support;
