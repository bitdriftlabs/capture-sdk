// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./metadata_test.rs"]
mod metadata_test;

use bd_api::Platform;
use bd_logger::LogFields;
use std::collections::HashMap;
use std::sync::{Arc, LazyLock};

pub static SDK_VERSION: LazyLock<String> =
  LazyLock::new(|| include!(concat!(env!("OUT_DIR"), "/version.rs")).to_string());

//
// AndroidStaticFields
//

/// Android-specific fields that are immutable for the lifetime of the process.
pub struct AndroidStaticFields {
  /// The device manufacturer (`_manufacturer`).
  pub manufacturer: String,

  /// The internal application version number (`_app_version_code`).
  pub app_version_code: i64,

  /// The Android SDK level (`_os_api_level`).
  pub os_api_level: i32,

  /// The current CPU architecture (`_architecture`).
  pub architecture: String,
}

//
// AppleStaticFields
//

/// Apple-specific fields that are immutable for the lifetime of the process.
pub struct AppleStaticFields {
  /// The application build number (`_build_number`).
  pub build_number: String,
}

//
// PlatformStaticFields
//

enum PlatformStaticFields {
  Android(AndroidStaticFields),
  Apple(AppleStaticFields),
  Electron,
}

// A collection of typed metadata that is used to identify the client when communicating with
// loop-api.
pub struct Mobile {
  /// The bundle or package identifier of the client (`app_id`), if one is provided.
  pub app_id: Option<String>,

  /// The application release version (`app_version`), if one is provided.
  pub app_version: Option<String>,

  pub platform: Platform,

  /// The lowercase operating-system name used for client metadata.
  pub os: String,

  /// Provides current device installation identifier.
  pub device: Arc<bd_logger::Device>,

  /// The host operating-system version (`os_version`), if one is provided.
  pub os_version: Option<String>,

  /// The host device model (`model`).
  pub model: String,

  platform_static_fields: PlatformStaticFields,
}

impl Mobile {
  #[must_use]
  pub fn android(
    app_id: Option<String>,
    app_version: Option<String>,
    os_version: Option<String>,
    device: Arc<bd_logger::Device>,
    model: String,
    static_fields: AndroidStaticFields,
  ) -> Self {
    Self {
      app_id,
      app_version,
      platform: Platform::Android,
      os: "android".to_string(),
      device,
      os_version,
      model,
      platform_static_fields: PlatformStaticFields::Android(static_fields),
    }
  }

  #[must_use]
  pub fn apple(
    app_id: Option<String>,
    app_version: Option<String>,
    os_version: Option<String>,
    device: Arc<bd_logger::Device>,
    model: String,
    static_fields: AppleStaticFields,
  ) -> Self {
    Self {
      app_id,
      app_version,
      platform: Platform::Apple,
      os: "ios".to_string(),
      device,
      os_version,
      model,
      platform_static_fields: PlatformStaticFields::Apple(static_fields),
    }
  }

  #[must_use]
  pub const fn electron(
    app_id: Option<String>,
    app_version: Option<String>,
    os: String,
    os_version: Option<String>,
    device: Arc<bd_logger::Device>,
    model: String,
  ) -> Self {
    Self {
      app_id,
      app_version,
      platform: Platform::Electron,
      os,
      device,
      os_version,
      model,
      platform_static_fields: PlatformStaticFields::Electron,
    }
  }

  /// Returns immutable fields that belong on every log line as OOTB metadata.
  ///
  /// The log `os` value uses the platform's established casing (for example, `Android` and
  /// `iOS`), which differs from the lowercase metadata value used for client identification.
  #[must_use]
  pub fn static_log_fields(&self) -> LogFields {
    let mut fields = LogFields::default();

    if let Some(app_id) = self.app_id.as_ref() {
      fields.insert("app_id".into(), app_id.clone().into());
    }
    if let Some(app_version) = self.app_version.as_ref() {
      fields.insert("app_version".into(), app_version.clone().into());
    }
    if let Some(os_version) = self.os_version.as_ref() {
      fields.insert("os_version".into(), os_version.clone().into());
    }
    match &self.platform_static_fields {
      PlatformStaticFields::Android(android) => {
        fields.insert("_manufacturer".into(), android.manufacturer.clone().into());
        fields.insert(
          "_app_version_code".into(),
          android.app_version_code.to_string().into(),
        );
        fields.insert(
          "_os_api_level".into(),
          android.os_api_level.to_string().into(),
        );
        fields.insert("_architecture".into(), android.architecture.clone().into());
      },
      PlatformStaticFields::Apple(apple) => {
        fields.insert("_build_number".into(), apple.build_number.clone().into());
      },
      PlatformStaticFields::Electron => {},
    }

    let log_os = match self.platform {
      Platform::Android => "Android",
      Platform::Apple => "iOS",
      Platform::Electron => &self.os,
    };
    fields.insert("os".into(), log_os.into());
    fields.insert("model".into(), self.model.clone().into());
    fields
  }

  /// Returns Android-specific immutable metadata when this is an Android client.
  #[must_use]
  pub const fn android_static_fields(&self) -> Option<&AndroidStaticFields> {
    match &self.platform_static_fields {
      PlatformStaticFields::Android(fields) => Some(fields),
      PlatformStaticFields::Apple(_) | PlatformStaticFields::Electron => None,
    }
  }
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
      metadata_map.insert("app_id".to_string(), app_id.clone());
    }

    if let Some(app_version) = self.app_version.as_ref() {
      metadata_map.insert("app_version".to_string(), app_version.clone());
    }

    if let Some(os_version) = self.os_version.as_ref() {
      metadata_map.insert("os_version".to_string(), os_version.clone());
    }

    if let PlatformStaticFields::Android(android) = &self.platform_static_fields {
      metadata_map.insert("_manufacturer".to_string(), android.manufacturer.clone());
    }

    metadata_map.insert("model".to_string(), self.model.clone());

    metadata_map
  }
}
