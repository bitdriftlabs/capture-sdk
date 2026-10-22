// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// The JSON attachment of an `ios_memory_dump` invocation: one section per stage that ran.

use serde::Serialize;

pub const SCHEMA_VERSION: u32 = 1;

//
// Report
//

#[derive(Serialize)]
pub struct Report {
  pub schema_version: u32,
}
