// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::*;
use bd_proto::protos::logging::payload::{ArrayData, BinaryData, MapData};

fn data(data_type: Data_type) -> Data {
  Data {
    data_type: Some(data_type),
    ..Default::default()
  }
}

fn binary(payload: Vec<u8>) -> Data {
  data(Data_type::BinaryData(BinaryData {
    payload,
    ..Default::default()
  }))
}

// Arguments reach the handler with the type and value the backend sent.

#[test]
fn binary_argument_bytes_reach_the_platform_unchanged() {
  // Not valid UTF-8 on purpose: a JPEG header, a NUL and a lone continuation byte.
  let payload = vec![0xFF, 0xD8, 0xFF, 0x00, 0x80];

  let delivered = platform_argument("image", binary(payload.clone())).unwrap();

  assert_eq!(
    delivered,
    PlatformArgument::Binary(payload),
    "binary payload was altered on its way to the handler"
  );
}

#[test]
fn integer_and_string_arguments_stay_distinguishable() {
  // The backend can send `0` as a number or as the text "0"; the handler must be able to tell.
  let number = platform_argument("n", data(Data_type::IntData(0))).unwrap();
  let text = platform_argument("n", data(Data_type::StringData("0".into()))).unwrap();

  assert_ne!(number, text, "numeric argument lost its type");
  assert_eq!(number, PlatformArgument::UnsignedInteger(0));
  assert_eq!(text, PlatformArgument::Text("0".into()));
}

#[test]
fn bool_and_string_arguments_stay_distinguishable() {
  let flag = platform_argument("f", data(Data_type::BoolData(true))).unwrap();
  let text = platform_argument("f", data(Data_type::StringData("true".into()))).unwrap();

  assert_ne!(flag, text, "bool argument lost its type");
  assert_eq!(flag, PlatformArgument::Bool(true));
}

#[test]
fn signed_and_double_arguments_keep_their_exact_values() {
  assert_eq!(
    platform_argument("i", data(Data_type::SintData(i64::MIN))).unwrap(),
    PlatformArgument::SignedInteger(i64::MIN)
  );
  assert_eq!(
    platform_argument("u", data(Data_type::IntData(u64::MAX))).unwrap(),
    PlatformArgument::UnsignedInteger(u64::MAX)
  );
  assert_eq!(
    platform_argument("d", data(Data_type::DoubleData(0.1))).unwrap(),
    PlatformArgument::Decimal(0.1)
  );
}

#[test]
fn nested_arguments_are_rejected_rather_than_silently_emptied() {
  // A nested value must fail the invocation rather than reach the handler as an empty value.
  let map = platform_argument("m", data(Data_type::MapData(MapData::default())));
  let array = platform_argument("a", data(Data_type::ArrayData(ArrayData::default())));
  let empty = platform_argument("e", Data::default());

  assert_eq!(
    map.unwrap_err(),
    "argument m has an unsupported nested value"
  );
  assert_eq!(
    array.unwrap_err(),
    "argument a has an unsupported nested value"
  );
  assert_eq!(empty.unwrap_err(), "argument e has no value");
}

#[test]
fn one_bad_argument_fails_the_whole_invocation_before_dispatch() {
  let arguments: HashMap<String, Data> = [
    ("ok".to_string(), data(Data_type::StringData("fine".into()))),
    (
      "bad".to_string(),
      data(Data_type::MapData(MapData::default())),
    ),
  ]
  .into();

  let error = platform_arguments(arguments).unwrap_err();

  assert_eq!(error, "argument bad has an unsupported nested value");
}

#[test]
fn type_codes_match_the_kotlin_and_ios_contract() {
  assert_eq!(PlatformArgument::Text(String::new()).type_code(), 0);
  assert_eq!(PlatformArgument::Binary(Vec::new()).type_code(), 1);
  assert_eq!(PlatformArgument::UnsignedInteger(0).type_code(), 2);
  assert_eq!(PlatformArgument::Decimal(0.0).type_code(), 3);
  assert_eq!(PlatformArgument::SignedInteger(0).type_code(), 4);
  assert_eq!(PlatformArgument::Bool(false).type_code(), 5);
}

// Attachments carry a required filename, as the `filename` attachment state field, and content
// type.

#[test]
fn completed_attachment_records_filename_and_content_type() {
  let result = completed_result(
    LogFields::default(),
    Some(PlatformAttachment {
      bytes: vec![0xFF, 0xD8],
      filename: "screen.jpg".to_string(),
      content_type: "image/jpeg".to_string(),
    }),
  );

  let CommandResult::Completed {
    attachment: Some(attachment),
    ..
  } = result
  else {
    panic!("expected a completed result with an attachment");
  };
  assert_eq!(attachment.content_type.as_deref(), Some("image/jpeg"));
  assert_eq!(
    attachment
      .state
      .get("filename")
      .and_then(|value| value.as_str()),
    Some("screen.jpg"),
    "filename must travel as the `filename` attachment state field"
  );
}

#[test]
fn completed_result_without_attachment_has_no_attachment_state() {
  let result = completed_result(LogFields::default(), None);

  let CommandResult::Completed { attachment, .. } = result else {
    panic!("expected a completed result");
  };
  assert!(attachment.is_none());
}
