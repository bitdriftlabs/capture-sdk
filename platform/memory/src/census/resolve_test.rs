// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::*;

#[test]
fn itanium_names_are_cxx_type_names() {
  assert!(is_cxx_type_name(b"N7WebCore4NodeE"));
  assert!(is_cxx_type_name(b"9MyWidget"));
  assert!(is_cxx_type_name(b"St9exception"));
  assert!(is_cxx_type_name(b"*N3foo3BarE"));
}

#[test]
fn swift_names_are_not_cxx_type_names() {
  assert!(!is_cxx_type_name(b"_TtP7SwiftUI4View_"));
  assert!(!is_cxx_type_name(b"_TtC8MyModule6MyType"));
  assert!(!is_cxx_type_name(b"_T0So8NSObjectC"));
  assert!(!is_cxx_type_name(b"_$s8MyModule6MyTypeC"));
  assert!(!is_cxx_type_name(b"$s8MyModule6MyTypeC"));
}

#[test]
fn short_or_odd_names_are_not_cxx_type_names() {
  assert!(!is_cxx_type_name(b""));
  assert!(!is_cxx_type_name(b"N"));
  assert!(!is_cxx_type_name(b"-foo"));
}
