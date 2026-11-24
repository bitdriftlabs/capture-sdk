// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::{CACHE, ImageCache, ImageSession};
use crate::CaptureError;
use bd_unwind::image::{ImageError, ImageInfo};
use bd_unwind::memory::AddressRange;

fn image(uuid: u8) -> ImageInfo {
  ImageInfo {
    uuid: Some([uuid; 16]),
    text: AddressRange::new(0x1000, 0x1000).unwrap(),
  }
}

#[test]
fn history_keeps_unloaded_images_and_distinguishes_address_reuse() {
  let mut cache = ImageCache::default();
  cache.add(0x1000, Ok(image(1)));
  cache.begin(4).unwrap();
  cache.active.remove(&0x1000);
  cache.add(0x1000, Ok(image(2)));
  cache.add(0x1000, Ok(image(2)));
  assert_eq!(cache.active.len(), 1);
  assert_eq!(cache.active[&0x1000], Ok(image(2)));
  assert_eq!(cache.history.unwrap().images, vec![image(1), image(2)]);
}

#[test]
fn history_is_bounded_and_reports_invalid_and_omitted_images() {
  let mut cache = ImageCache::default();
  cache.add(0x1000, Ok(image(1)));
  cache.add(0x2000, Err(ImageError::InvalidHeader));
  cache.begin(1).unwrap();
  cache.add(0x3000, Ok(image(2)));
  cache.add(0x4000, Err(ImageError::UnreadableMemory));
  let history = cache.history.unwrap();
  assert_eq!(history.images, vec![image(1)]);
  assert_eq!(history.dropped, 1);
  assert_eq!(history.errors, 2);
  assert_eq!(cache.active.len(), 4);
}

#[test]
fn cancelled_session_releases_history_and_next_session_uses_cached_images() {
  let _capture = crate::tests::CAPTURE_TEST_LOCK.lock().unwrap();
  let session = ImageSession::start(4096).unwrap();
  assert!(matches!(
    ImageSession::start(1),
    Err(CaptureError::AlreadyCapturing)
  ));
  assert!(!CACHE.lock().active.is_empty());
  drop(session);
  assert!(CACHE.lock().history.is_none());
  let history = ImageSession::start(4096).unwrap().finish();
  assert!(!history.images.is_empty());
  assert!(CACHE.lock().history.is_none());
}
