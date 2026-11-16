// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// Owner attribution: an unnamed buffer has no type to read, but usually exactly one live object
// points at it. Every word of every named block is checked against the unnamed blocks. A named
// block pointing at the start of a buffer beats one pointing inside it, and among equals the first
// one found is credited. Blocks above INTERIOR_MAX_SIZE need a pointer to their start.
//
// Runs after the locks, so named blocks may have been freed since they were recorded. Each one is
// copied through a safe read and the copy is scanned. Known limits:
// - an integer that equals a block address counts as a pointer;
// - a buffer several objects point at equally well goes to the first one scanned;
// - only blocks of classes in the runtime's class list are sources.

use super::Census;
use crate::safe_read;
use crate::sys::mach::now_ns;

/// Named blocks are copied in windows of adjacent blocks, so a heap of small objects costs one
/// read per window instead of one per object.
const SCRATCH_SIZE: usize = 256 * 1024;

impl Census<'_> {
  /// Credits unnamed blocks to the named blocks pointing at them, then releases the tables. Must
  /// run after resolution, with no zone locked.
  pub(super) fn attribute_owners(&mut self) {
    let Some(mut tables) = self.owners.take() else {
      return;
    };
    tables.unnamed.sort_unstable_by_key(|block| block.address);
    tables.named.sort_unstable_by_key(|block| block.address);
    let (Some(lowest), Some(highest)) = (tables.unnamed.first(), tables.unnamed.last()) else {
      return;
    };
    // Every target lies between the lowest and highest unnamed block, which rejects most words
    // before any search.
    let bounds = (
      lowest.address,
      highest
        .address
        .saturating_add(usize::try_from(highest.size).unwrap_or(usize::MAX)),
    );

    let started = now_ns();
    let named = std::mem::take(&mut tables.named);
    let mut scratch = vec![0u8; SCRATCH_SIZE];
    let mut index = 0;
    while index < named.len() {
      let window_start = named[index].address;
      let Some(readable_end) = self.ranges.readable_end(window_start) else {
        index += 1;
        continue;
      };

      // Extend the window over the following blocks while they fit in the scratch buffer and the
      // same readable range.
      let mut last = index;
      while let Some(next) = named.get(last + 1) {
        let next_end = next.address + next.size as usize;
        if next_end - window_start > SCRATCH_SIZE || next_end > readable_end {
          break;
        }
        last += 1;
      }
      let window_end = named[last].address + named[last].size as usize;
      let window_fits = window_end - window_start <= SCRATCH_SIZE && window_end <= readable_end;

      if window_fits && safe_read::read(window_start, &mut scratch[.. window_end - window_start]) {
        for block in &named[index ..= last] {
          let offset = block.address - window_start;
          let copy = &scratch[offset .. offset + block.size as usize];
          tables.scan_words(
            block,
            copy,
            &mut self.classes,
            &self.candidates,
            &mut self.summary,
            bounds,
          );
        }
      } else {
        // Part of the window went away, or a single block is larger than the scratch buffer.
        for block in &named[index ..= last] {
          let size = (block.size as usize).min(SCRATCH_SIZE);
          if safe_read::read(block.address, &mut scratch[.. size]) {
            tables.scan_words(
              block,
              &scratch[.. size],
              &mut self.classes,
              &self.candidates,
              &mut self.summary,
              bounds,
            );
          }
        }
      }
      index = last + 1;
    }
    self.summary.owners_ns = now_ns() - started;
  }
}
