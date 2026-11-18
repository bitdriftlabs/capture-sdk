// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

//! Android system-triggered crash-profile attribution. The logger seeds and grows the report
//! candidates and runs adoption. Kotlin encodes its process facts through
//! `CaptureJniLibrary.encodeCrashProfileSource` and hands successful
//! platform results to the latest logger's attribution through
//! [`Java_io_bitdrift_capture_CaptureJniLibrary_submitCrashProfile`].

use bd_artifact_upload::ReportAttribution;
use bd_artifact_upload::crash_profile::Kind;
use bd_artifact_upload::crash_profile_attribution::CrashProfileAttribution;
use bd_error_reporter::reporter::with_handle_unexpected;
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{JNI_TRUE, jboolean};
use parking_lot::Mutex;
use std::path::PathBuf;
use std::sync::Arc;
use uuid::Uuid;

// The API level that introduced system-triggered ANR profiles.
const MIN_API_LEVEL: i32 = 36;

static ATTRIBUTION: Mutex<Option<Arc<CrashProfileAttribution>>> = Mutex::new(None);

/// Creates attribution for a new logger, replacing any earlier logger's. The SDK device ID scopes
/// reports and profiles, which share the app's private storage.
pub(crate) fn new_attribution(
  device_id: &str,
  os_api_level: i32,
) -> Option<Arc<dyn ReportAttribution>> {
  if os_api_level < MIN_API_LEVEL {
    return None;
  }
  let installation_id = Uuid::parse_str(device_id).ok()?;
  let attribution = Arc::new(CrashProfileAttribution::new(installation_id));
  *ATTRIBUTION.lock() = Some(attribution.clone());
  Some(attribution)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_submitCrashProfile(
  env: JNIEnv<'_>,
  _class: JClass<'_>,
  path: JString<'_>,
  kind: JString<'_>,
  retain_unmatched: jboolean,
) {
  with_handle_unexpected(
    || -> anyhow::Result<()> {
      let path = PathBuf::from(
        unsafe { env.get_string_unchecked(&path) }?
          .to_string_lossy()
          .to_string(),
      );
      let kind = match unsafe { env.get_string_unchecked(&kind) }?
        .to_string_lossy()
        .as_ref()
      {
        "anr" => Kind::Anr,
        "oom" => Kind::JavaOutOfMemory,
        kind => anyhow::bail!("unsupported crash profile kind {kind}"),
      };
      match ATTRIBUTION.lock().as_ref() {
        Some(attribution) => attribution.submit(path, kind, retain_unmatched == JNI_TRUE),
        None => log::debug!("crash profile submitted without attribution"),
      }
      Ok(())
    },
    "jni submit crash profile",
  );
}
