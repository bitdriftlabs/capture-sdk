// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::*;

/// Offset of `uuid` in `dyld_cache_header`, from dyld's open source `dyld_cache_format.h`.
const CACHE_UUID_OFFSET: usize = 0x58;

#[test]
fn identity_describes_the_running_system() {
  let identity = identity().unwrap();
  assert!(!identity.product_type.is_empty());
  assert!(!identity.product_type.contains(' '));
  assert!(
    identity.os_build.chars().all(char::is_alphanumeric),
    "{}",
    identity.os_build
  );
  assert!(
    ["arm64e", "arm64", "x86_64", "x86_64h"].contains(&identity.cache_architecture.as_str()),
    "{}",
    identity.cache_architecture
  );
  assert_ne!(identity.shared_cache_uuid, [0; 16]);
}

#[test]
fn shared_cache_uuid_matches_the_cache_header() {
  let mut header_uuid = [0u8; 16];
  assert!(safe_read::read(
    shared_cache_start().unwrap() + CACHE_UUID_OFFSET,
    &mut header_uuid
  ));
  assert_eq!(shared_cache_uuid().unwrap(), header_uuid);
}

#[test]
fn architecture_is_read_from_the_cache_magic() {
  assert_eq!(
    architecture_from_magic(b"dyld_v1  arm64e\0").as_deref(),
    Some("arm64e")
  );
  assert_eq!(
    architecture_from_magic(b"dyld_v1   arm64\0").as_deref(),
    Some("arm64")
  );
  assert_eq!(architecture_from_magic(b"not a dyld cache"), None);
  assert_eq!(architecture_from_magic(b"dyld_v1        \0"), None);
}
