// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::*;
use std::mem::offset_of;

#[test]
fn all_image_infos_layout_matches_the_header() {
  assert_eq!(offset_of!(DyldAllImageInfos, shared_cache_uuid), 160);
  assert_eq!(
    offset_of!(DyldAllImageInfos, shared_cache_base_address),
    176
  );
  assert_eq!(count_of::<TaskDyldInfo>(), 5);
}
