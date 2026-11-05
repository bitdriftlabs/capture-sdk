// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::write_json;
use crate::CancellationToken;
use crate::tests::sample_snapshot;
use std::io;

#[test]
fn byte_limit_accepts_the_exact_encoded_size_and_rejects_one_byte_less() {
  let snapshot = sample_snapshot();
  let cancellation = CancellationToken::default();
  let mut expected = Vec::new();
  write_json(&snapshot, &mut expected, usize::MAX, &cancellation).unwrap();
  let mut output = Vec::new();
  write_json(&snapshot, &mut output, expected.len(), &cancellation).unwrap();
  assert_eq!(output, expected);
  output.clear();
  let error = write_json(&snapshot, &mut output, expected.len() - 1, &cancellation).unwrap_err();
  assert_eq!(error.kind(), io::ErrorKind::FileTooLarge);
  assert!(output.len() < expected.len());
}
