// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use bd_unwind::image::ImageInfo;
#[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
use bd_unwind::{apple::TaskMemory, image::ImageError};
#[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
use std::ffi::CStr;
use std::sync::Arc;

//
// Image
//

/// Owned loader metadata. Names survive unloading alongside the image UUID and mapping.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct Image {
  pub info: ImageInfo,
  pub name: Arc<str>,
}

impl Image {
  #[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
  pub(crate) fn read(header: *const u8) -> Result<Self, ImageError> {
    let info = ImageInfo::read(&TaskMemory, header as u64)?;
    let mut details = std::mem::MaybeUninit::<libc::Dl_info>::uninit();
    // SAFETY: Called only inside dyld's add-image callback, which keeps this image mapped.
    // dladdr is public and thread-safe; querying the header avoids symbol-table lookup.
    // https://github.com/apple-oss-distributions/dyld/blob/main/dyld/DyldAPIs.cpp
    let name = unsafe {
      if libc::dladdr(header.cast(), details.as_mut_ptr()) != 0 {
        let details = details.assume_init();
        if !details.dli_fname.is_null() && details.dli_fbase == header.cast_mut().cast() {
          let path = CStr::from_ptr(details.dli_fname).to_string_lossy();
          Arc::from(path.rsplit('/').next().unwrap_or(&path))
        } else {
          Arc::from("")
        }
      } else {
        Arc::from("")
      }
    };
    Ok(Self { info, name })
  }
}
