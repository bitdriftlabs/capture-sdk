// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use super::mach::{KERN_SUCCESS, task_self};
use libc::{
  c_char,
  c_uint,
  c_void,
  kern_return_t,
  mach_port_t,
  vm_address_t,
  vm_range_t,
  vm_size_t,
};
use std::ffi::CStr;
use std::marker::PhantomData;
use std::slice;

pub const MALLOC_PTR_IN_USE_RANGE_TYPE: c_uint = 1;

type MemoryReader = unsafe extern "C" fn(
  task: mach_port_t,
  remote_address: vm_address_t,
  size: vm_size_t,
  local_memory: *mut *mut c_void,
) -> kern_return_t;

type RangeRecorder = unsafe extern "C" fn(
  task: mach_port_t,
  context: *mut c_void,
  type_mask: c_uint,
  ranges: *mut vm_range_t,
  count: c_uint,
);

type Enumerator = unsafe extern "C" fn(
  task: mach_port_t,
  context: *mut c_void,
  type_mask: c_uint,
  zone_address: vm_address_t,
  reader: MemoryReader,
  recorder: RangeRecorder,
) -> kern_return_t;

type ZoneFunction = unsafe extern "C" fn(zone: *mut MallocZone);

//
// MallocZone
//

/// The prefix of `malloc_zone_t` up to the introspection table.
#[repr(C)]
struct MallocZone {
  reserved: [*mut c_void; 2],
  functions: [*const c_void; 7],
  zone_name: *const c_char,
  batch_functions: [*const c_void; 2],
  introspect: *const MallocIntrospection,
}

//
// MallocIntrospection
//

/// The prefix of `malloc_introspection_t` up to `force_unlock`.
#[repr(C)]
struct MallocIntrospection {
  enumerator: Option<Enumerator>,
  functions: [*const c_void; 4],
  force_lock: Option<ZoneFunction>,
  force_unlock: Option<ZoneFunction>,
}

//
// MallocStatistics
//

#[repr(C)]
#[derive(Default)]
struct MallocStatistics {
  blocks_in_use: c_uint,
  size_in_use: usize,
  max_size_in_use: usize,
  size_allocated: usize,
}

unsafe extern "C" {
  fn malloc_get_all_zones(
    task: mach_port_t,
    reader: Option<MemoryReader>,
    addresses: *mut *mut vm_address_t,
    count: *mut c_uint,
  ) -> kern_return_t;

  fn malloc_zone_statistics(zone: *mut MallocZone, stats: *mut MallocStatistics);
}

/// The enumerated addresses are this process's own, so no copy is needed.
unsafe extern "C" fn identity_reader(
  _task: mach_port_t,
  remote_address: vm_address_t,
  _size: vm_size_t,
  local_memory: *mut *mut c_void,
) -> kern_return_t {
  // SAFETY: the enumerator passes a valid out pointer.
  unsafe { *local_memory = remote_address as *mut c_void };
  KERN_SUCCESS
}

//
// RangeSink
//

/// Receives the batches of blocks a zone reports. Runs with the zone locked, so implementations
/// must not allocate, lock or panic.
pub trait RangeSink {
  fn record(&mut self, type_mask: u32, ranges: &[vm_range_t]);
}

unsafe extern "C" fn record_ranges<S: RangeSink>(
  _task: mach_port_t,
  context: *mut c_void,
  type_mask: c_uint,
  ranges: *mut vm_range_t,
  count: c_uint,
) {
  // SAFETY: `context` is the `&mut S` that `ZoneLock::enumerate` passed, alive for the call.
  let sink = unsafe { &mut *context.cast::<S>() };
  let ranges = if ranges.is_null() {
    &[][..]
  } else {
    // SAFETY: the enumerator reports `count` ranges at `ranges`.
    unsafe { slice::from_raw_parts(ranges, count as usize) }
  };
  sink.record(type_mask, ranges);
}

//
// Zone
//

/// A registered malloc zone. Zones are not destroyed while the census runs.
pub struct Zone(*mut MallocZone);

impl Zone {
  pub fn all() -> Vec<Self> {
    let mut addresses: *mut vm_address_t = std::ptr::null_mut();
    let mut count: c_uint = 0;
    // SAFETY: both out pointers are valid; the reader maps addresses to themselves.
    let status = unsafe {
      malloc_get_all_zones(
        task_self(),
        Some(identity_reader),
        &raw mut addresses,
        &raw mut count,
      )
    };
    if status != KERN_SUCCESS || addresses.is_null() {
      return Vec::new();
    }
    // SAFETY: malloc reports `count` zone addresses at `addresses`.
    let addresses = unsafe { slice::from_raw_parts(addresses, count as usize) };
    addresses
      .iter()
      .filter(|address| **address != 0)
      .map(|address| Self(*address as *mut MallocZone))
      .collect()
  }

  fn introspection(&self) -> Option<&MallocIntrospection> {
    // SAFETY: registered zones and their introspection tables live as long as the process.
    unsafe { (*self.0).introspect.as_ref() }
  }

  pub fn name(&self) -> String {
    // SAFETY: as in `introspection`; the name is a NUL-terminated string the zone owns.
    let name = unsafe { (*self.0).zone_name };
    if name.is_null() {
      return "(unnamed)".to_string();
    }
    // SAFETY: checked for NULL above.
    unsafe { CStr::from_ptr(name) }
      .to_string_lossy()
      .into_owned()
  }

  pub fn is_enumerable(&self) -> bool {
    self.introspection().is_some_and(|introspection| {
      introspection.enumerator.is_some()
        && introspection.force_lock.is_some()
        && introspection.force_unlock.is_some()
    })
  }

  /// Blocks in use right now. Takes the zone lock briefly, so never call it with a zone locked.
  pub fn blocks_in_use(&self) -> usize {
    let mut statistics = MallocStatistics::default();
    // SAFETY: `self.0` is a registered zone and `statistics` a valid out pointer.
    unsafe { malloc_zone_statistics(self.0, &raw mut statistics) };
    statistics.blocks_in_use as usize
  }

  pub fn lock(&self) -> Option<ZoneLock<'_>> {
    let force_lock = self.introspection()?.force_lock?;
    // SAFETY: the zone's own lock function, called on the zone.
    unsafe { force_lock(self.0) };
    Some(ZoneLock {
      zone: self,
      locked: PhantomData,
    })
  }
}

//
// ZoneLock
//

/// Holds a zone's lock until dropped.
pub struct ZoneLock<'a> {
  zone: &'a Zone,
  locked: PhantomData<*mut ()>,
}

impl ZoneLock<'_> {
  /// Reports every block in use to `sink` in batches.
  pub fn enumerate<S: RangeSink>(&self, sink: &mut S) {
    let Some(enumerator) = self.zone.introspection().and_then(|table| table.enumerator) else {
      return;
    };
    // SAFETY: the zone is locked, the reader and recorder match the enumerator's contract, and
    // `sink` outlives the call.
    unsafe {
      enumerator(
        task_self(),
        (&raw mut *sink).cast::<c_void>(),
        MALLOC_PTR_IN_USE_RANGE_TYPE,
        self.zone.0 as vm_address_t,
        identity_reader,
        record_ranges::<S>,
      );
    }
  }
}

impl Drop for ZoneLock<'_> {
  fn drop(&mut self) {
    if let Some(force_unlock) = self
      .zone
      .introspection()
      .and_then(|table| table.force_unlock)
    {
      // SAFETY: unlocks the lock `Zone::lock` took.
      unsafe { force_unlock(self.zone.0) };
    }
  }
}
