// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use bd_session::Strategy;
use std::sync::Arc;

pub struct SessionProvider {
  strategy: Arc<Strategy>,
}

impl SessionProvider {
  pub const fn new(strategy: Arc<Strategy>) -> Self {
    Self { strategy }
  }
}

impl bd_client_common::error::SessionProvider for SessionProvider {
  fn session_id(&self) -> String {
    self.strategy.session_id()
  }
}
