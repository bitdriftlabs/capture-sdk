// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use bd_client_common::error::handle_unexpected;
use bd_key_value::Storage;
use bd_logger::{
  AnnotatedLogField,
  AnnotatedLogFields,
  InitParams,
  LogLevel,
  LogType,
  LoggerBuilder,
};
use bd_metadata::Platform;
use bd_session::fixed::{self, UUIDCallbacks};
use bd_session::{Store, Strategy};
use std::collections::HashMap;
use std::hash::{DefaultHasher, Hash, Hasher};
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Instant;
use tokio::try_join;

//
// RustLogger
//

pub struct RustLogger {
  _logger: bd_logger::Logger,
  handle: bd_logger::LoggerHandle,
  session_strategy: Arc<Strategy>,
  device: Arc<bd_logger::Device>,
}

impl RustLogger {
  pub fn new(
    api_key: String,
    api_address: &str,
    sdk_directory: String,
    app_id: String,
    app_version: String,
    os: String,
    os_version: String,
    locale: String,
  ) -> anyhow::Result<Self> {
    let start = Instant::now();
    let sdk_directory = PathBuf::from(sdk_directory);

    let storage = Box::new(DiskStorage::new(sdk_directory.join("storage"))?);
    let store = Arc::new(Store::new(storage));
    let device = Arc::new(bd_device::Device::new(store.clone()));
    let device_clone = device.clone();
    let session_strategy = Arc::new(Strategy::Fixed(fixed::Strategy::new(
      store.clone(),
      Arc::new(UUIDCallbacks),
    )));
    let session_strategy_clone = session_strategy.clone();
    let shutdown = bd_shutdown::ComponentShutdownTrigger::default();

    let metadata_provider = Arc::new(MetadataProvider::new(
      app_id.clone(),
      app_version.clone(),
      os,
      os_version,
      locale,
    ));

    let static_metadata = Arc::new(StaticMetadata::new(
      app_id,
      app_version,
      // TODO(fz): We should add Platform::Server
      Platform::Electron,
      device.id(),
    ));

    let (network, handle) =
      bd_hyper_network::HyperNetwork::new(api_address, shutdown.make_shutdown());

    let reporter = {
      let (reporter, handle) =
        bd_hyper_network::ErrorReporter::new(api_address.to_string(), api_key.clone());

      let handle = bd_client_common::error::MetadataErrorReporter::new(
        Arc::new(handle),
        Arc::new(SessionProvider {
          strategy: session_strategy.clone(),
        }),
        static_metadata.clone(),
      );

      bd_client_common::error::UnexpectedErrorHandler::set_reporter(Arc::new(handle));

      reporter
    };

    let (logger, _, logger_future) = LoggerBuilder::new(InitParams {
      sdk_directory,
      api_key,
      session_strategy: session_strategy_clone,
      store,
      metadata_provider,
      resource_utilization_target: Box::new(EmptyTarget),
      session_replay_target: Box::new(EmptyTarget),
      events_listener_target: Box::new(EmptyTarget),
      device: device_clone,
      network: Box::new(handle),
      static_metadata,
    })
    .with_client_stats(true)
    .build()?;

    LoggerBuilder::run_logger_runtime(async {
      // Make sure we hold onto the shutdown handle to avoid an immediate shutdown.
      #[allow(clippy::no_effect_underscore_binding)]
      let _shutdown = shutdown;

      // Since the error reporting relies on the reporter future we need to make sure that we give
      // the reporter a chance to report on the error returned from the top level task. To
      // accomplish this we run the reporter in a separate task that we allow to finish after the
      // logger future has completed.

      let reporter_task = tokio::spawn(reporter.start());
      handle_unexpected(try_join!(network.start(), logger_future), "top level task");
      reporter_task.await?;

      Ok(())
    })?;

    let handle = logger.new_logger_handle();

    handle.log_sdk_start(vec![], start.elapsed().try_into().unwrap_or_default());

    Ok(Self {
      _logger: logger,
      handle,
      session_strategy,
      device,
    })
  }

  pub fn log(&self, log_level: LogLevel, message: String, fields: AnnotatedLogFields) {
    self.handle.log(
      log_level,
      LogType::Normal,
      message.into(),
      fields,
      vec![],
      None,
      false,
    );
  }

  pub fn session_id(&self) -> String {
    self.session_strategy.session_id()
  }

  pub fn device_id(&self) -> String {
    self.device.id()
  }
}

struct SessionProvider {
  strategy: Arc<Strategy>,
}

impl bd_client_common::error::SessionProvider for SessionProvider {
  fn session_id(&self) -> String {
    self.strategy.session_id()
  }
}

struct MetadataProvider {
  app_id: String,
  app_version: String,
  os: String,
  os_version: String,
  locale: String,
}

impl MetadataProvider {
  const fn new(
    app_id: String,
    app_version: String,
    os: String,
    os_version: String,
    locale: String,
  ) -> Self {
    Self {
      app_id,
      app_version,
      os,
      os_version,
      locale,
    }
  }
}

impl bd_logger::MetadataProvider for MetadataProvider {
  fn timestamp(&self) -> anyhow::Result<time::OffsetDateTime> {
    Ok(time::OffsetDateTime::now_utc())
  }

  fn fields(&self) -> anyhow::Result<bd_logger::AnnotatedLogFields> {
    Ok(vec![
      AnnotatedLogField::new_ootb("app_id".to_string(), self.app_id.clone().into()),
      AnnotatedLogField::new_ootb("app_version".to_string(), self.app_version.clone().into()),
      AnnotatedLogField::new_ootb("os".to_string(), self.os.clone().into()),
      AnnotatedLogField::new_ootb("os_version".to_string(), self.os_version.clone().into()),
      AnnotatedLogField::new_ootb("_locale".to_string(), self.locale.clone().into()),
    ])
  }
}

struct StaticMetadata {
  app_id: String,
  app_version: String,
  platform: Platform,
  device_id: String,
}

impl StaticMetadata {
  pub const fn new(
    app_id: String,
    app_version: String,
    platform: Platform,
    device_id: String,
  ) -> Self {
    Self {
      app_id,
      app_version,
      platform,
      device_id,
    }
  }
}

impl bd_metadata::Metadata for StaticMetadata {
  fn collect_inner(&self) -> HashMap<String, String> {
    [
      ("app_version".to_string(), self.app_version.clone()),
      ("app_id".to_string(), self.app_id.clone()),
    ]
    .into()
  }

  fn sdk_version(&self) -> &'static str {
    // TODO(fz): Figure out the story for the SDK version.
    "0.1.0"
  }

  fn device_id(&self) -> String {
    self.device_id.clone()
  }

  fn platform(&self) -> &Platform {
    &self.platform
  }

  fn os(&self) -> String {
    if cfg!(target_os = "macos") {
      "macos".to_string()
    } else if cfg!(target_os = "linux") {
      "linux".to_string()
    } else if cfg!(target_os = "windows") {
      "windows".to_string()
    } else {
      "unknown".to_string()
    }
  }
}

//
// Naive in-memory key-storage backed by disk files
//

pub struct DiskStorage {
  root: PathBuf,
  state: parking_lot::Mutex<HashMap<String, String>>,
}

impl DiskStorage {
  pub fn new(root: PathBuf) -> Result<Self, std::io::Error> {
    std::fs::create_dir_all(&root)?;

    Ok(Self {
      root,
      state: parking_lot::Mutex::new(HashMap::new()),
    })
  }

  fn path(&self, key: &str) -> PathBuf {
    let mut hasher = DefaultHasher::new();
    key.hash(&mut hasher);
    self.root.join(hasher.finish().to_string())
  }
}

impl Storage for DiskStorage {
  fn set_string(&self, key: &str, value: &str) -> anyhow::Result<()> {
    let mut guard = self.state.lock();
    std::fs::write(self.path(key), value.as_bytes())?;

    let mut state = guard.clone();
    state.insert(key.to_string(), value.to_string());
    *guard = state;

    Ok(())
  }

  fn get_string(&self, key: &str) -> anyhow::Result<Option<String>> {
    let mut guard = self.state.lock();
    if guard.contains_key(key) {
      return Ok(guard.get(key).cloned());
    }

    let mut state = guard.clone();
    std::fs::read_to_string(self.path(key))
      .map(|value| {
        state.insert(key.to_string(), value.to_string());
        *guard = state;
        Some(value)
      })
      .or_else(|_| Ok(None))
  }

  fn delete(&self, key: &str) -> anyhow::Result<()> {
    self.state.lock().remove(key);
    let _ = std::fs::remove_file(self.path(key));
    Ok(())
  }
}

//
// EmptyTarget
//

pub struct EmptyTarget;

impl bd_resource_utilization::Target for EmptyTarget {
  fn tick(&self) {}
}

impl bd_session_replay::Target for EmptyTarget {
  fn capture_screen(&self) {}
  fn capture_screenshot(&self) {}
}

impl bd_events::ListenerTarget for EmptyTarget {
  fn start(&self) {}
  fn stop(&self) {}
}
