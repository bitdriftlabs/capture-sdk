// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::{RELEASE_SHA256, verify_checksum_output};

#[test]
fn accepts_the_expected_release_checksum() {
  assert_eq!(
    verify_checksum_output(&format!("{RELEASE_SHA256}  bitdrift-maestro.zip\n")),
    Ok(())
  );
}

#[test]
fn rejects_an_unexpected_release_checksum() {
  assert!(verify_checksum_output("not-the-release-checksum  bitdrift-maestro.zip\n").is_err());
}
