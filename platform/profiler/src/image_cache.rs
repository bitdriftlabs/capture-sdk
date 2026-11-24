// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./image_cache_test.rs"]
mod tests;

use crate::CaptureError;
use bd_unwind::apple::TaskMemory;
use bd_unwind::image::{ImageError, ImageInfo};
use parking_lot::Mutex;
use std::collections::BTreeMap;
use std::sync::{LazyLock, Once};

static CACHE: LazyLock<Mutex<ImageCache>> = LazyLock::new(|| Mutex::new(ImageCache::default()));
static REGISTER: Once = Once::new();

#[link(name = "System")]
unsafe extern "C" {
  fn _dyld_register_func_for_add_image(callback: extern "C" fn(*const u8, isize));
  fn _dyld_register_func_for_remove_image(callback: extern "C" fn(*const u8, isize));
}

// dyld keeps the image mapped during its callback. Parse once at load time and retain only owned
// metadata. Neither callback calls dyld, logs, or touches a target thread's suspension state.
extern "C" fn added(header: *const u8, _: isize) {
  let image = ImageInfo::read(&TaskMemory, header as u64);
  CACHE.lock().add(header as u64, image);
}

extern "C" fn removed(header: *const u8, _: isize) {
  CACHE.lock().active.remove(&(header as u64));
}

//
// ImageHistory
//

/// A request owns copies even after unload or address reuse. Capacity is the profile's existing
/// image budget; overflow counts omitted identities without growing this history.
pub(super) struct ImageHistory {
  pub(super) images: Vec<ImageInfo>,
  pub(super) dropped: u64,
  pub(super) errors: u64,
  capacity: usize,
}

impl ImageHistory {
  fn new(capacity: usize) -> Self {
    Self {
      images: Vec::with_capacity(capacity),
      dropped: 0,
      errors: 0,
      capacity,
    }
  }

  fn record(&mut self, image: Result<ImageInfo, ImageError>) {
    match image {
      Ok(image) if self.images.contains(&image) => {},
      Ok(image) if self.images.len() < self.capacity => self.images.push(image),
      Ok(_) => self.dropped += 1,
      Err(_) => self.errors += 1,
    }
  }
}

//
// ImageCache
//

#[derive(Default)]
struct ImageCache {
  active: BTreeMap<u64, Result<ImageInfo, ImageError>>,
  history: Option<ImageHistory>,
}

impl ImageCache {
  fn add(&mut self, header: u64, image: Result<ImageInfo, ImageError>) {
    self.active.insert(header, image);
    if let Some(history) = &mut self.history {
      history.record(image);
    }
  }

  fn begin(&mut self, capacity: usize) -> Result<(), CaptureError> {
    if self.history.is_some() {
      return Err(CaptureError::AlreadyCapturing);
    }
    let mut history = ImageHistory::new(capacity);
    for image in self.active.values() {
      history.record(*image);
    }
    self.history = Some(history);
    Ok(())
  }
}

//
// ImageSession
//

/// Only the request worker starts/finishes a history, always with all target threads resumed.
/// Registration is process-wide because dyld has no matching unregister API. It seeds the cache
/// synchronously with existing images, then delivers subsequent load/unload events.
/// <https://github.com/apple-oss-distributions/dyld/blob/main/include/mach-o/dyld.h>
pub(super) struct ImageSession {
  active: bool,
}

impl ImageSession {
  pub(super) fn start(capacity: usize) -> Result<Self, CaptureError> {
    REGISTER.call_once(|| unsafe {
      // Do not hold CACHE while registering: registration calls added synchronously, and dyld
      // callbacks acquire CACHE. No cache-locked path calls back into the loader.
      _dyld_register_func_for_remove_image(removed);
      _dyld_register_func_for_add_image(added);
    });
    CACHE.lock().begin(capacity)?;
    Ok(Self { active: true })
  }

  pub(super) fn finish(mut self) -> ImageHistory {
    // This session is the sole history owner; the process-wide profiler slot excludes other
    // requests. Taking it stops recording before the attachment is exported.
    let history = CACHE.lock().history.take();
    self.active = false;
    history.unwrap_or_else(|| ImageHistory::new(0))
  }
}

impl Drop for ImageSession {
  fn drop(&mut self) {
    // Cancellation/errors release the bounded request history without unregistering callbacks.
    if self.active {
      CACHE.lock().history = None;
    }
  }
}
