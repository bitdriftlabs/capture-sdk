// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use bd_api::Platform;
use std::collections::HashMap;
use std::sync::{Arc, LazyLock};

static SDK_VERSION: LazyLock<String> =
  LazyLock::new(|| include!(concat!(env!("OUT_DIR"), "/version.rs")).to_string());

// A collection of typed metadata that is used to identify the client when communicating with
// loop-api.
pub struct Mobile {
  /// The app id of the client, if one is provided.
  pub app_id: Option<String>,

  /// The app version of the client, if one is provided.
  pub app_version: Option<String>,

  pub platform: Platform,

  pub os: String,

  /// Provides current device installation identifier.
  pub device: Arc<bd_logger::Device>,

  pub model: String,
}

impl bd_api::Metadata for Mobile {
  fn sdk_version(&self) -> &'static str {
    &SDK_VERSION
  }

  fn platform(&self) -> &bd_api::Platform {
    &self.platform
  }

  fn os(&self) -> String {
    self.os.clone()
  }

  fn device_id(&self) -> String {
    self.device.id()
  }

  fn collect_inner(&self) -> HashMap<String, String> {
    let mut metadata_map = HashMap::new();

    if let Some(app_id) = self.app_id.as_ref() {
      metadata_map.insert("app_id".to_string(), app_id.to_string());
    }

    if let Some(app_version) = self.app_version.as_ref() {
      metadata_map.insert("app_version".to_string(), app_version.to_string());
    }

    metadata_map.insert("model".to_string(), self.model.clone());

    metadata_map
  }
}
