// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::SessionStrategy;
use assert_matches::assert_matches;
use bd_client_common::fb::root_as_log;
use bd_proto::protos::config::v1::config::{buffer_config, BufferConfigList};
use bd_runtime::runtime::FeatureFlag as _;
use bd_test_helpers::config_helper::{
  configuration_update,
  default_buffer_config,
  make_buffer_matcher_matching_everything,
};
use bd_test_helpers::runtime::{make_update, ValueKind};
use bd_test_helpers::test_api_server::StreamAction;

#[tokio::test]
async fn basic_test() {
  let mut server = bd_test_helpers::test_api_server::start_server(false, None);
  let tmp_dir = tempdir::TempDir::new("test").unwrap();

  let logger = super::RustLogger::new(
    "api-key".to_string(),
    &format!("http://localhost:{}", server.port),
    SessionStrategy::ActivityBased,
    tmp_dir.path().to_str().unwrap().to_string(),
    "app_id".to_string(),
    "app_version".to_string(),
    "os".to_string(),
    "os_version".to_string(),
    "locale".to_string(),
  )
  .unwrap();

  let stream = server.next_initialized_stream().await.unwrap();

  server
    .stream_action(
      stream,
      StreamAction::SendRuntime(make_update(
        vec![(
          bd_runtime::runtime::log_upload::BatchSizeFlag::path(),
          ValueKind::Int(1),
        )],
        "0".to_string(),
      )),
    )
    .await;

  server
    .stream_action(
      stream,
      StreamAction::SendConfiguration(configuration_update(
        "0",
        bd_proto::protos::client::api::configuration_update::StateOfTheWorld {
          buffer_config_list: Some(BufferConfigList {
            buffer_config: vec![default_buffer_config(
              buffer_config::Type::CONTINUOUS,
              make_buffer_matcher_matching_everything().into(),
            )],
            ..Default::default()
          })
          .into(),
          ..Default::default()
        },
      )),
    )
    .await;

  server.next_configuration_ack(stream).await;


  logger.log(1, "message".to_string(), vec![]);

  assert_matches!(server.next_log_upload().await, Some(log_upload) => {
    assert_eq!(log_upload.logs.len(), 1);
    assert_eq!(root_as_log(&log_upload.logs[0]).unwrap().message_as_string_data().unwrap().data(), "message");
  });
}
