// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use bd_key_value::Storage;
use bd_logger::{
  AnnotatedLogField,
  AnnotatedLogFields,
  InitParams,
  LogField,
  LogFieldKind,
  LogLevel,
  LogType,
};
use bd_metadata::Platform;
use bd_session::fixed::{self, UUIDCallbacks};
use bd_session::{Store, Strategy};
use std::collections::HashMap;
use std::hash::{DefaultHasher, Hash, Hasher};
use std::path::PathBuf;
use std::sync::Arc;

// TODO(snowp): This is very similar setup to envoy, we should refactor this into a helper
// potentially.

//
// RustLogger
//

const PLATFORM: Platform = Platform::Other("electron", "electron");

pub struct RustLogger {
  _logger: bd_logger::Logger,
  handle: bd_logger::LoggerHandle,
  session_strategy: Arc<Strategy>,
  device: Arc<bd_logger::Device>,
}

impl RustLogger {
  pub fn new(
    api_key: String,
    api_address: String,
    sdk_directory: String,
    app_id: String,
    app_version: String,
    os: String,
    os_version: String,
    locale: String,
  ) -> anyhow::Result<Self> {
    let (tx, rx) = tokio::sync::oneshot::channel();
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

    std::thread::spawn(move || {
      let shutdown = bd_shutdown::ComponentShutdownTrigger::default();

      let (network, handle) =
        bd_hyper_network::HyperNetwork::new(&api_address, shutdown.make_shutdown());

      let metadata_provider = Arc::new(MetadataProvider::new(
        app_id.clone(),
        app_version.clone(),
        os,
        os_version,
        locale,
      ));

      let static_metadata = Arc::new(StaticMetadata::new(
        app_id.clone(),
        app_version.clone(),
        // TODO(snowp): This hard codes electron for now, we may want to make this derived from the
        // env so that we can have this code work for both electron apps and other node-based
        // programs.
        // We set both the OS and kind to electron for now, arguably OS should use the system OS
        // but we don't use this for anything right now. Using electron avoids conflating this with
        // ios/android platforms.
        &PLATFORM,
      ));

      // TODO(snowp): Error handling

      let (logger, _, logger_future) = bd_logger::LoggerBuilder::new(InitParams {
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
        platform: PLATFORM,
        static_metadata,
      })
      .with_mobile_features(true)
      .build()
      .unwrap();

      let _ignored = tx.send(logger);

      tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap()
        .block_on(async {
          tokio::spawn(network.start());

          logger_future.await.unwrap();
        });
    });

    let logger = rx.blocking_recv().unwrap();
    let handle = logger.new_logger_handle();

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
      AnnotatedLogField {
        field: LogField {
          key: "app_id".to_string(),
          value: self.app_id.clone().into(),
        },
        kind: LogFieldKind::Ootb,
      },
      AnnotatedLogField {
        field: LogField {
          key: "app_version".to_string(),
          value: self.app_version.clone().into(),
        },
        kind: LogFieldKind::Ootb,
      },
      AnnotatedLogField {
        field: LogField {
          key: "os".to_string(),
          value: self.os.clone().into(),
        },
        kind: LogFieldKind::Ootb,
      },
      AnnotatedLogField {
        field: LogField {
          key: "os_version".to_string(),
          value: self.os_version.clone().into(),
        },
        kind: LogFieldKind::Ootb,
      },
      AnnotatedLogField {
        field: LogField {
          key: "_locale".to_string(),
          value: self.locale.clone().into(),
        },
        kind: LogFieldKind::Ootb,
      },
    ])
  }
}

struct StaticMetadata {
  app_id: String,
  app_version: String,
  platform: &'static Platform,
}

impl StaticMetadata {
  pub const fn new(app_id: String, app_version: String, platform: &'static Platform) -> Self {
    Self {
      app_id,
      app_version,
      platform,
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
    "0.1.0"
  }

  fn platform(&self) -> &Platform {
    self.platform
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
