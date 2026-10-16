// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::{SecretVaultItem, SigningAssets};

#[test]
fn signing_assets_require_one_certificate_profiles_and_password() {
  let item = serde_json::from_str::<SecretVaultItem>(
    r#"{
      "id": "item-id",
      "attachments": [
        { "fileName": "development.p12" },
        { "fileName": "development.mobileprovision" }
      ],
      "fields": [{ "name": "p12_password", "value": "secret" }]
    }"#,
  )
  .unwrap();

  let assets = SigningAssets::from_item(&item).unwrap();
  assert_eq!(assets.certificate, "development.p12");
  assert_eq!(assets.profiles, ["development.mobileprovision"]);
}

#[test]
fn signing_assets_explain_the_required_password_field() {
  let item = serde_json::from_str::<SecretVaultItem>(
    r#"{
      "id": "item-id",
      "attachments": [
        { "fileName": "development.p12" },
        { "fileName": "development.mobileprovision" }
      ]
    }"#,
  )
  .unwrap();

  assert_eq!(
    SigningAssets::from_item(&item).unwrap_err(),
    "The secret-vault item needs a non-empty `p12_password` custom field."
  );
}
