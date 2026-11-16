// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// Stage 1: the kernel's accounting of this process, from a single `TASK_VM_INFO` call.

#[cfg(test)]
#[path = "./counters_test.rs"]
mod tests;

use crate::report::{CountersReport, Ledgers};
use crate::sys::mach::available_memory;
use crate::sys::task_info::{TaskVmInfo, task_vm_info};
use anyhow::anyhow;
use std::mem::offset_of;

pub fn capture() -> anyhow::Result<CountersReport> {
  let reply = task_vm_info().ok_or_else(|| anyhow!("task_info failed"))?;
  let info = reply.info;

  let ledgers = reply
    .reaches::<i64>(offset_of!(
      TaskVmInfo,
      ledger_tag_neural_footprint_compressed
    ))
    .then(|| Ledgers {
      graphics: sum(
        info.ledger_tag_graphics_footprint,
        info.ledger_tag_graphics_footprint_compressed,
      ),
      media: sum(
        info.ledger_tag_media_footprint,
        info.ledger_tag_media_footprint_compressed,
      ),
      network: sum(
        info.ledger_tag_network_nonvolatile,
        info.ledger_tag_network_nonvolatile_compressed,
      ),
      neural: sum(
        info.ledger_tag_neural_footprint,
        info.ledger_tag_neural_footprint_compressed,
      ),
      purgeable_volatile: sum(
        info.ledger_purgeable_volatile,
        info.ledger_purgeable_volatile_compressed,
      ),
      purgeable_nonvolatile: sum(
        info.ledger_purgeable_nonvolatile,
        info.ledger_purgeable_novolatile_compressed,
      ),
    });

  let available = available_memory();
  Ok(CountersReport {
    phys_footprint: info.phys_footprint,
    phys_footprint_peak: reply
      .reaches::<i64>(offset_of!(TaskVmInfo, ledger_phys_footprint_peak))
      .then(|| clamp(info.ledger_phys_footprint_peak)),
    internal: info.internal,
    compressed: info.compressed,
    external: info.external,
    resident_size: info.resident_size,
    decompressions: reply
      .reaches::<i32>(offset_of!(TaskVmInfo, decompressions))
      .then(|| u64::try_from(info.decompressions).unwrap_or(0)),
    limit_bytes_remaining: reply
      .reaches::<u64>(offset_of!(TaskVmInfo, limit_bytes_remaining))
      .then_some(info.limit_bytes_remaining),
    available_before_kill: (available > 0).then_some(available),
    ledgers,
  })
}

pub fn phys_footprint() -> Option<u64> {
  task_vm_info().map(|reply| reply.info.phys_footprint)
}

fn clamp(value: i64) -> u64 {
  u64::try_from(value).unwrap_or(0)
}

fn sum(value: i64, compressed: i64) -> u64 {
  clamp(value) + clamp(compressed)
}
