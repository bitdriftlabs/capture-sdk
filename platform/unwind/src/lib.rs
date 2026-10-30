// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
//! Stack unwinding over caller-owned registers, memory and output buffers.
//!
//! This crate neither suspends threads nor installs crash monitors. The caller must keep the
//! captured memory consistent and choose a reader suitable for its execution context. No allocator,
//! logging, lazy initialization or synchronization is used by the unwinder. Custom readers must
//! preserve those properties when used with suspended threads or crash handlers.
//!
//! The caller supplies captured registers, known stack bounds and an output slice. Readers must
//! report unreadable memory without faulting. Only the returned output prefix is initialized; the
//! stop reason distinguishes a completed chain from an incomplete capture. Frame addresses retain
//! the distinction between a sampled PC and a return address.
//!
//! The implementation walks ARM64 frame pointers and stops explicitly at async boundaries.
//! Inlining, tail calls and interrupted prologues or epilogues can make an FP-only trace
//! incomplete. Compiling on `x86_64` preserves SDK build compatibility; it does not provide an
//! `x86_64` unwinder.
//!
//! Read image metadata with [`image::ImageInfo::read`] during normal execution. The caller must
//! keep the image mapped and consistent throughout the read.
//! Host tests alone do not establish signal-handler safety, remote-thread capture correctness or
//! physical-device coverage.

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
