// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// Which system symbols the backend should use to name addresses inside system images: the device,
// the OS build and the shared cache this process maps.

#[cfg(test)]
#[path = "./system_test.rs"]
mod tests;

use crate::report::SystemIdentity;
use crate::safe_read;
use crate::sys::system::{shared_cache_start, shared_cache_uuid, sysctl_string};

/// A cache header starts with `dyld_v1`, padded with spaces up to the architecture name. From
/// `dyld_cache_header.magic` in dyld's open source `dyld_cache_format.h`, not in the SDK.
const CACHE_MAGIC_PREFIX: &[u8] = b"dyld_v1";
const CACHE_MAGIC_SIZE: usize = 16;

/// None on the simulator, whose model and OS build would select physical-device symbols, and when
/// any part is unavailable: a partial identity would select the wrong symbols.
pub fn identity() -> Option<SystemIdentity> {
  if cfg!(target_abi = "sim") {
    return None;
  }
  Some(SystemIdentity {
    product_type: sysctl_string("hw.machine")?,
    os_build: sysctl_string("kern.osversion")?,
    cache_architecture: cache_architecture()?,
    shared_cache_uuid: shared_cache_uuid()?,
  })
}

fn cache_architecture() -> Option<String> {
  let mut magic = [0u8; CACHE_MAGIC_SIZE];
  if !safe_read::read(shared_cache_start()?, &mut magic) {
    return None;
  }
  architecture_from_magic(&magic)
}

fn architecture_from_magic(magic: &[u8]) -> Option<String> {
  let architecture = magic.strip_prefix(CACHE_MAGIC_PREFIX)?;
  let architecture = std::str::from_utf8(architecture).ok()?;
  let architecture = architecture.trim_matches(|character| character == ' ' || character == '\0');
  (!architecture.is_empty()).then(|| architecture.to_string())
}
