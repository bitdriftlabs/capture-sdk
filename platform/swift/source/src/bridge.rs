// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./bridge_tests.rs"]
mod bridge_tests;

use crate::bridge::ffi::make_nsstring;
use protobuf::Enum as _;
use bd_proto::protos::logging::payload::LogType;
use crate::ffi::{make_empty_nsstring, nsstring_into_string};
use crate::key_value_storage::UserDefaultsStorage;
use crate::{events, ffi, resource_utilization, session_replay};
use anyhow::anyhow;
use bd_api::{Platform, PlatformNetworkManager, PlatformNetworkStream, StreamEvent};
use bd_error_reporter::reporter::{
  handle_unexpected,
  with_handle_unexpected,
  with_handle_unexpected_or,
  MetadataErrorReporter,
  UnexpectedErrorHandler,
};
use bd_logger::{
  Block,
  CaptureSession,
  LogAttributesOverrides,
  LogFieldKind,
  LogFields,
  LogLevel,
  MetadataProvider,
  ReportProcessingSession,
};
use bd_noop_network::NoopNetwork;
use objc::rc::StrongPtr;
use objc::runtime::Object;
use platform_shared::metadata::{self, Mobile};
use platform_shared::{read_global_state_snapshot, LoggerHolder, LoggerId};
use std::borrow::{Borrow, Cow};
use std::boxed::Box;
use std::collections::HashMap;
use std::convert::From;
use std::ffi::CStr;
use std::ops::DerefMut;
use std::os::raw::c_char;
use std::sync::{Arc, Once};
use time::{Duration, OffsetDateTime};
use tracing_subscriber::filter::LevelFilter;
use tracing_subscriber::layer::SubscriberExt;
use tracing_subscriber::util::SubscriberInitExt;
use tracing_subscriber::EnvFilter;

static LOGGING_INIT: Once = Once::new();

fn initialize_logging() {
  LOGGING_INIT.call_once(|| {
    // ANSI is disabled since they don't render properly in Xcode.
    let stderr = tracing_subscriber::fmt::layer()
      .with_writer(std::io::stderr)
      .with_ansi(false)
      .compact();

    let filter = EnvFilter::builder()
      .with_default_directive(LevelFilter::INFO.into())
      .from_env_lossy();

    // Use try_init() to avoid situations where the logger has already been initialized by test
    // harnesses. This is not ideal but it's the easiest fix for now.
    let _ = tracing_subscriber::Registry::default()
      .with(filter)
      .with(stderr)
      .try_init();
  });
}

// StrongPtr is Send iff T is sync, which we assume for the Network implementation.
// TODO(snowp): objc2 allows describing this better as Id<T> is generic, fix if we switch over.
#[allow(clippy::non_send_fields_in_send_ty)]
struct SwiftNetworkHandle {
  // NSObject that assumed to be conforming to the Network protocol.
  network_nsobject: objc::rc::StrongPtr,
}

// This NetworkHandle only calls methods on the underlying NSObject. We don't ever mutate it,
// and assume that the underlying calls for BitdriftNetwork are also threadsafe, and therefore
// we assume that SwiftNetworkHandle is threadsafe for Sync + Send.
unsafe impl Sync for SwiftNetworkHandle {}
unsafe impl Send for SwiftNetworkHandle {}

#[async_trait::async_trait]
impl PlatformNetworkManager<bd_runtime::runtime::ConfigLoader> for SwiftNetworkHandle {
  #[inline(never)]
  async fn start_stream(
    &self,
    event_tx: tokio::sync::mpsc::Sender<bd_api::StreamEvent>,
    runtime: &bd_runtime::runtime::ConfigLoader,
    headers: &HashMap<&str, &str>,
  ) -> anyhow::Result<Box<dyn bd_api::PlatformNetworkStream>> {
    let (stream_state_tx, stream_state_rx) = tokio::sync::watch::channel(State::Open);

    // By releasing the pointer and providing direct access to the state, we avoid having to worry
    // about manually tracking the stream states on our end. This comes at the cost of some
    // manual memory management, but given our control of the Swift StreamState this
    // seems OK. See capture_api_release_stream for cleanup.
    let stream_state = Box::into_raw(Box::new(StreamState {
      event_tx,
      stream_state_tx,
    }));

    let stream = objc::rc::autoreleasepool(|| {
      let objc_headers = ffi::convert_map(headers)?;

      Ok::<_, anyhow::Error>(unsafe {
        objc::rc::StrongPtr::retain(
          msg_send![*self.network_nsobject, startStream:stream_state headers:*objc_headers],
        )
      })
    })?;

    Ok(Box::new(SwiftNetworkStream {
      stream_writer: UrlSessionStreamWriter(stream),
      stream_state_rx,
      emit_send_data_timeout_error: runtime.register_bool_watch(),
      send_data_timeout: runtime.register_int_watch(),
    }))
  }
}

#[derive(PartialEq, Eq)]
enum State {
  Open,
  Closed,
}

struct StreamState {
  event_tx: bd_api::StreamEventSender,
  stream_state_tx: tokio::sync::watch::Sender<State>,
}

trait StreamWriter {
  /// Writes the provided slice to the stream. Returns the amount of bytes written or -1 if the
  /// write could not be performed.
  fn write_to_stream(&mut self, data: &[u8]) -> isize;

  /// Used in test to determine whether we timed out writing to the stream. This should get elided
  /// in non-test code.
  fn timeout(&mut self);

  /// Called to tear down the stream during shut down.
  fn shutdown(&mut self);
}

struct UrlSessionStreamWriter(objc::rc::StrongPtr);

// StrongPtr is Send iff T is sync, which we assume for the Stream implementation. Sending data
// and closing the stream is all safe to do from any thread, and the fact that we send everything
// from the event loop means that we won't interleave data.
// TODO(snowp): objc2 allows describing this better as Id<T> is generic, fix if we switch over.
#[allow(clippy::non_send_fields_in_send_ty)]
unsafe impl Send for UrlSessionStreamWriter {}

impl StreamWriter for UrlSessionStreamWriter {
  fn write_to_stream(&mut self, data: &[u8]) -> isize {
    objc::rc::autoreleasepool(|| unsafe {
      msg_send![*self.0, sendData:data.as_ptr() count:data.len()]
    })
  }

  fn timeout(&mut self) {}

  fn shutdown(&mut self) {
    objc::rc::autoreleasepool(|| {
      let () = unsafe { msg_send![*self.0, shutdown] };
    });
  }
}

struct SwiftNetworkStream<W: StreamWriter> {
  stream_writer: W,
  stream_state_rx: tokio::sync::watch::Receiver<State>,
  emit_send_data_timeout_error: bd_runtime::runtime::Watch<bool, flags::ReportSendDataTimeoutError>,
  send_data_timeout: bd_runtime::runtime::Watch<u32, flags::SendDataTimeout>,
}

#[async_trait::async_trait]
impl<W: StreamWriter + Send> PlatformNetworkStream for SwiftNetworkStream<W> {
  #[inline(never)]
  async fn send_data(&mut self, data: &[u8]) -> anyhow::Result<()> {
    const PER_SLICE_SLEEP_MS: u64 = 100;

    // Set a long minute timeout (see runtime flag for default) for a single write attempt. As we
    // might be dealing with process suspension via backgrounding etc., we prefer using a series
    // of smaller timeouts over a single long deadline. This should allow us to better capture
    // the idea that we want to let the system attempt to send for a period of time, and not
    // fire immediately if the process is suspended for >3m.
    let timeout_across_suspension =
      std::time::Duration::from_secs((*self.send_data_timeout.read()).into());

    // Dividing the total timeout by the slice duration gets us the number of times we want to
    // retry this operation.
    let mut timeout_slices: u128 =
      timeout_across_suspension.as_millis() / u128::from(PER_SLICE_SLEEP_MS);

    let mut slice = data;

    loop {
      let remaining = slice.len();
      let written = self.stream_writer.write_to_stream(slice);

      if written < 0 {
        log::trace!("failed to send data (rval {written}), trying again in 100ms");
        // Error. Retry after a wait.
        tokio::select! {
          () = tokio::time::sleep(std::time::Duration::from_millis(PER_SLICE_SLEEP_MS)) => {
            debug_assert!(timeout_slices > 0);
            timeout_slices -= 1;

            if timeout_slices == 0 {
              // Erroring out here if we hit the timeout to send an error report, but returning Ok.
              // Things will be pretty broken if we don't get the stream close timeout properly.
              if *self.emit_send_data_timeout_error.read() {
                handle_unexpected::<(), anyhow::Error>(Err(anyhow!("timed out!")), "send_data");
              }

              // Used for testing the timeout path. Ideally we'd do a stat here but this proves
              // difficult with how the crates are set up (stats depends on api).
              self.stream_writer.timeout();

              return Ok(());
            }
          },
          _ = self.stream_state_rx.changed() => {
            if *self.stream_state_rx.borrow() == State::Closed {
              return Ok(())
            }
          }
        }

        continue;
      }

      // Already made sure written will be positive.
      #[allow(clippy::cast_sign_loss)]
      let written = written as usize;

      if written == remaining {
        return Ok(());
      }

      slice = &slice[written ..];
    }
  }
}

impl<W: StreamWriter> Drop for SwiftNetworkStream<W> {
  fn drop(&mut self) {
    if *self.stream_state_rx.borrow() != State::Closed {
      self.stream_writer.shutdown();
    }
  }
}

#[no_mangle]
extern "C" fn capture_api_received_data(stream_id: &StreamState, data: *const u8, size: usize) {
  with_handle_unexpected(
    || -> anyhow::Result<()> {
      let data = unsafe { std::slice::from_raw_parts(data.cast(), size) };

      let _ignored = stream_id
        .event_tx
        .blocking_send(StreamEvent::Data(data.into()));

      Ok(())
    },
    "swift received chunk",
  );
}

#[no_mangle]
extern "C" fn capture_api_stream_closed(stream_id: &StreamState, reason: *const c_char) {
  with_handle_unexpected(
    || -> anyhow::Result<()> {
      let reason = unsafe { CStr::from_ptr(reason) }.to_str()?;
      let _ignored = stream_id.stream_state_tx.send(State::Closed);
      let _ignored = stream_id
        .event_tx
        .blocking_send(StreamEvent::StreamClosed(reason.to_string()));

      Ok(())
    },
    "swift stream closed",
  );
}

// Safety: On the Swift end we ensure that we call this in the deinit function, at which point we
// are guaranteed exclusive access to the object.
#[no_mangle]
extern "C" fn capture_api_release_stream(stream_id: *mut StreamState) {
  // When we're closing the stream, take ownership over the pointer to ensure that it gets cleaned
  // up. The Swift code ensures that this will be called when the stream is dropped, which avoids us
  // leaking memory.
  unsafe { drop(Box::from_raw(stream_id)) };
}

// TODO(vincentho): Figure out a better way to represent a wrapped NSObject that doesn't require all
// this copy/paste.
pub struct SwiftErrorReporter {
  // NSObject that assumed to be conforming to the ErrorReporter protocol.
  error_reporter_nsobject: StrongPtr,
}

impl SwiftErrorReporter {
  /// Constructs a wrapper around a pointer to an Objective-C object conforming to the
  /// `ErrorReporter` protocol.
  ///
  /// # Safety
  /// The provided pointer must point to a valid object conforming to the `ErrorReporter` protocol.
  #[must_use]
  pub unsafe fn new(error_reporter_nsobject: *mut Object) -> Self {
    Self {
      error_reporter_nsobject: StrongPtr::retain(error_reporter_nsobject),
    }
  }
}

// This SwiftErrorReporter only calls methods on the underlying id<ErrorReporter>, which we
// guarantee to be thread safe. For the default UrlSession implementation, each call fires off a
// oneoff request via the ephemeral URLSession instance, maintaining no mutable state.
unsafe impl Sync for SwiftErrorReporter {}
#[allow(clippy::non_send_fields_in_send_ty)]
// TODO(snowp): objc2 allows describing this better as Id<T> is generic, fix if we switch over.
unsafe impl Send for SwiftErrorReporter {}

impl bd_error_reporter::reporter::Reporter for SwiftErrorReporter {
  #[inline(never)]
  fn report(
    &self,
    message: &str,
    _details: &Option<String>,
    fields: &HashMap<Cow<'_, str>, Cow<'_, str>>,
  ) {
    objc::rc::autoreleasepool(|| {
      // This should never fail in safe code because this only fails when there is an internal null
      // character, but we want to be super safe in this error handler.
      if let Ok(c_str) = std::ffi::CString::new(message) {
        if let Ok(fields) = ffi::convert_map::<std::hash::RandomState>(
          &fields
            .iter()
            .map(|(k, v)| (k.borrow(), v.borrow()))
            .collect(),
        ) {
          unsafe {
            let () =
              msg_send![*self.error_reporter_nsobject, reportError:c_str.as_ptr() fields:*fields];
          };
        }
      }
    });
  }
}

/// Wrapper around a objc handle that implements the `LogMetadataProvider` protocol.
struct LogMetadataProvider {
  /// Retains strong ownership over the reference. This allows the provider's lifetime to live past
  /// the function scope.
  ptr: objc::rc::StrongPtr,
}

// The implementation of LogMetadata has only final fields, all interior mutability behind locks.
// This relies on the user provided providers also being Send + Sync, and so care must be taken that
// this is the case.
unsafe impl Sync for LogMetadataProvider {}
// TODO(snowp): objc2 allows describing this better as Id<T> is generic, fix if we switch over.
#[allow(clippy::non_send_fields_in_send_ty)]
unsafe impl Send for LogMetadataProvider {}

impl MetadataProvider for LogMetadataProvider {
  #[allow(clippy::cast_possible_truncation)]
  fn timestamp(&self) -> anyhow::Result<time::OffsetDateTime> {
    objc::rc::autoreleasepool(|| {
      // Safety: Since we receive MetadataProvider as a typed protocol, we know that it
      // responds to `timestamp` and will return a TimeInterval, which is backed by a double.
      let timestamp_double: f64 = unsafe { msg_send![*self.ptr, timestamp] };

      // To get the seconds component, get the integral part of the double. This can safely be
      // cast to i64 as it must be integral (due to trunc()).
      let seconds = time::OffsetDateTime::from_unix_timestamp(timestamp_double.trunc() as i64)?;

      #[allow(clippy::cast_sign_loss)]
      // To the get nanos, take the fractional part of the double to get the fractional time in
      // seconds, then multiply it with 1E9 to convert it to nanoseconds. Truncating this
      // drops sub-ns precision, and it should always fit within a u64 since it must be <= 1E9
      // which is < 2^31.
      let nanos = Duration::nanoseconds((timestamp_double.fract() * 1E9).trunc() as i64);

      Ok(seconds + nanos)
    })
  }

  fn fields(&self) -> anyhow::Result<(LogFields, LogFields)> {
    // Safety: Since we receive MetadataProvider as a typed protocol, we know that it
    // responds to `ootbFields` and `customFields` selectors.
    objc::rc::autoreleasepool(|| unsafe {
      let ootb_fields = ffi::convert_fields(msg_send![*self.ptr, ootbFields])?;

      let custom_fields = ffi::convert_fields(msg_send![*self.ptr, customFields])?;

      Ok((custom_fields, ootb_fields))
    })
  }
}

#[no_mangle]
extern "C" fn capture_report_error(error_message: *const c_char) {
  let error_message = unsafe { CStr::from_ptr(error_message) }
    .to_str()
    .unwrap_or_default();
  handle_unexpected::<(), anyhow::Error>(
    std::result::Result::Err(anyhow!(error_message)),
    "swift_platform_layer",
  );
}

#[no_mangle]
extern "C" fn capture_create_logger(
  path: *const c_char,
  api_key: *const c_char,
  session_strategy: *mut Object,
  provider: *mut Object,
  resource_utilization_target: *mut Object,
  session_replay_target: *mut Object,
  events_listener_target: *mut Object,
  app_id: *const c_char,
  app_version: *const c_char,
  model: *const c_char,
  bd_network_nsobject: *mut Object,
  error_reporter_ns_object: *mut Object,
  start_in_sleep_mode: bool,
) -> LoggerId<'static> {
  initialize_logging();

  // Safety: Guaranteed to be a valid Id per the Objective-C signature.
  let metadata_provider = Arc::new(LogMetadataProvider {
    ptr: (unsafe { objc::rc::StrongPtr::retain(provider) }),
  });

  with_handle_unexpected_or(
    || {
      let storage = Box::<UserDefaultsStorage>::default();
      let store = Arc::new(bd_key_value::Store::new(storage));
      let previous_run_global_state = read_global_state_snapshot(store.clone());

      let session_strategy =
        crate::session::SessionStrategy::new(session_strategy).create(store.clone())?;

      let device: Arc<bd_device::Device> = Arc::new(bd_device::Device::new(store.clone()));

      let static_metadata = Arc::new(Mobile {
        // String conversion can fail if the provided string is not UTF-8.
        app_id: Some(unsafe { CStr::from_ptr(app_id) }.to_str()?.to_string()),
        app_version: Some(unsafe { CStr::from_ptr(app_version) }.to_str()?.to_string()),
        platform: Platform::Apple,
        // TODO(mattklein123): Pass this from the platform layer when we want to support other OS.
        // Further, "os" as sent as a log tag is hard coded as "iOS" so we have a casing
        // mismatch. We need to untangle all of this but we can do that when we send all fixed
        // fields as metadata and only use the fixed fields on logs for matching.
        os: "ios".to_string(),
        device: device.clone(),
        model: unsafe { CStr::from_ptr(model) }.to_str()?.to_string(),
      });

      let error_reporter = MetadataErrorReporter::new(
        Arc::new(unsafe { SwiftErrorReporter::new(error_reporter_ns_object) }),
        Arc::new(platform_shared::error::SessionProvider::new(
          session_strategy.clone(),
        )),
        static_metadata.clone(),
      );

      // Errors emitted up until this point are not reported to bitdrift remote.
      // TODO(Augustyniak): Make it more obvious that as much work as possible should be done after
      // the error reporter is set up.
      UnexpectedErrorHandler::set_reporter(Arc::new(error_reporter));

      let network_manager: Box<dyn PlatformNetworkManager<bd_runtime::runtime::ConfigLoader>> =
        if bd_network_nsobject.is_null() {
          // Intended to be used for testing purposes only.
          Box::new(NoopNetwork {})
        } else {
          Box::new(SwiftNetworkHandle {
            network_nsobject: unsafe { objc::rc::StrongPtr::retain(bd_network_nsobject) },
          })
        };

      let path = unsafe { CStr::from_ptr(path) }.to_str()?;
      let logger = bd_logger::LoggerBuilder::new(bd_logger::InitParams {
        sdk_directory: path.into(),
        api_key: unsafe { CStr::from_ptr(api_key) }.to_str()?.to_string(),
        session_strategy,
        metadata_provider,
        resource_utilization_target: Box::new(resource_utilization::Target::new(
          resource_utilization_target,
        )),
        session_replay_target: Box::new(session_replay::Target::new(session_replay_target)),
        events_listener_target: Box::new(events::Target::new(events_listener_target)),
        network: network_manager,
        store,
        device,
        static_metadata,
        start_in_sleep_mode,
        feature_flags_file_size_bytes: 1024 * 1024,
        feature_flags_high_watermark: 0.8,
      })
      .with_internal_logger(true)
      .build()
      .map(|(logger, _, future, _)| LoggerHolder::new(logger, future, previous_run_global_state))?;

      Ok(logger.into_raw())
    },
    unsafe { LoggerId::from_raw(-1) },
    "swift create logger",
  )
}

#[no_mangle]
extern "C" fn capture_start_logger(logger_id: LoggerId<'_>) {
  with_handle_unexpected_or(
    move || {
      logger_id.start();
      Ok(())
    },
    (),
    "swift start logger",
  );
}

#[no_mangle]
extern "C" fn capture_shutdown_logger(logger_id: LoggerId<'_>, blocking: bool) {
  logger_id.shutdown(blocking);
}

#[no_mangle]
extern "C" fn capture_process_crash_reports(mut logger_id: LoggerId<'_>) {
  with_handle_unexpected(
    || -> anyhow::Result<()> {
      logger_id
        .deref_mut()
        .process_crash_reports(ReportProcessingSession::PreviousRun);
      Ok(())
    },
    "swift process crash reports",
  );
}

#[no_mangle]
extern "C" fn capture_runtime_bool_variable_value(
  logger_id: LoggerId<'_>,
  variable_name: *const c_char,
  default_value: bool,
) -> bool {
  with_handle_unexpected_or(
    move || {
      let variable_name = unsafe { CStr::from_ptr(variable_name) }.to_str()?;
      Ok(
        logger_id
          .runtime_snapshot()
          .get_bool(variable_name, default_value),
      )
    },
    default_value,
    "swift runtime feature check",
  )
}

#[no_mangle]
extern "C" fn capture_runtime_uint32_variable_value(
  logger_id: LoggerId<'_>,
  variable_name: *const c_char,
  default_value: u32,
) -> u32 {
  with_handle_unexpected_or(
    move || {
      let variable_name = unsafe { CStr::from_ptr(variable_name) }.to_str()?;
      Ok(
        logger_id
          .runtime_snapshot()
          .get_integer(variable_name, default_value),
      )
    },
    default_value,
    "swift runtime int value",
  )
}

#[no_mangle]
extern "C" fn capture_write_log(
  logger_id: LoggerId<'_>,
  log_level: LogLevel,
  log_type: u32,
  log: *const c_char,
  fields: *const Object,
  matching_fields: *const Object,
  blocking: bool,
  override_occurred_at_unix_milliseconds: i64,
) {
  with_handle_unexpected(
    move || -> anyhow::Result<()> {
      let log_str = unsafe { CStr::from_ptr(log) }.to_str()?.to_string();

      // TODO(Augustyniak): Differentiate between incoming OOTB and custom log fields.
      let fields = unsafe { ffi::convert_annotated_fields(fields, LogFieldKind::Ootb) }?;

      let matching_fields =
        unsafe { ffi::convert_annotated_fields(matching_fields, LogFieldKind::Ootb) }?;

      let attributes_overrides = if override_occurred_at_unix_milliseconds <= 0 {
        None
      } else {
        Some(LogAttributesOverrides::OccurredAt(
          unix_milliseconds_to_date(override_occurred_at_unix_milliseconds)?,
        ))
      };

      logger_id.log(
        log_level,
        LogType::from_i32(log_type.try_into().unwrap_or_default()).unwrap_or(LogType::NORMAL),
        log_str.into(),
        fields,
        matching_fields,
        attributes_overrides,
        if blocking {
          Block::Yes(std::time::Duration::from_secs(1))
        } else {
          Block::No
        },
        &CaptureSession::default(),
      );

      Ok(())
    },
    "swift write log",
  );
}

#[no_mangle]
extern "C" fn capture_write_session_replay_screen_log(
  logger_id: LoggerId<'_>,
  fields: *const Object,
  duration_s: f64,
) {
  with_handle_unexpected(
    || -> anyhow::Result<()> {
      let fields = unsafe { ffi::convert_annotated_fields(fields, LogFieldKind::Ootb) }?;

      logger_id.log_session_replay_screen(fields, time::Duration::seconds_f64(duration_s));
      Ok(())
    },
    "swift write session replay screen log",
  );
}

#[no_mangle]
extern "C" fn capture_write_session_replay_screenshot_log(
  logger_id: LoggerId<'_>,
  fields: *const Object,
  duration_s: f64,
) {
  with_handle_unexpected(
    || -> anyhow::Result<()> {
      let fields = unsafe { ffi::convert_annotated_fields(fields, LogFieldKind::Ootb) }?;

      logger_id.log_session_replay_screenshot(fields, time::Duration::seconds_f64(duration_s));
      Ok(())
    },
    "swift write session replay screenshot log",
  );
}

#[no_mangle]
extern "C" fn capture_write_resource_utilization_log(
  logger_id: LoggerId<'_>,
  fields: *const Object,
  duration_s: f64,
) {
  with_handle_unexpected(
    || -> anyhow::Result<()> {
      let fields = unsafe { ffi::convert_annotated_fields(fields, LogFieldKind::Ootb) }?;

      logger_id.log_resource_utilization(fields, time::Duration::seconds_f64(duration_s));
      Ok(())
    },
    "swift write resource utilization log",
  );
}

#[no_mangle]
extern "C" fn capture_write_sdk_start_log(
  logger_id: LoggerId<'_>,
  fields: *const Object,
  duration_s: f64,
) {
  with_handle_unexpected(
    || -> anyhow::Result<()> {
      let fields = unsafe { ffi::convert_annotated_fields(fields, LogFieldKind::Ootb) }?;

      logger_id.log_sdk_start(fields, time::Duration::seconds_f64(duration_s));
      Ok(())
    },
    "swift write sdk started log",
  );
}

#[no_mangle]
extern "C" fn capture_should_write_app_update_log(
  logger_id: LoggerId<'_>,
  app_version: *const Object,
  build_number: *const Object,
) -> bool {
  with_handle_unexpected_or(
    || {
      let app_version = unsafe { nsstring_into_string(app_version) }?;
      let build_number = unsafe { nsstring_into_string(build_number) }?;

      Ok(logger_id.should_log_app_update(
        app_version,
        bd_logger::AppVersionExtra::BuildNumber(build_number),
      ))
    },
    false,
    "swift should log app update",
  )
}

#[no_mangle]
extern "C" fn capture_write_app_update_log(
  logger_id: LoggerId<'_>,
  app_version: *const Object,
  build_number: *const Object,
  app_install_size_bytes: u64,
  duration_s: f64,
) {
  with_handle_unexpected(
    || -> anyhow::Result<()> {
      let app_version = unsafe { nsstring_into_string(app_version) }?;
      let build_number = unsafe { nsstring_into_string(build_number) }?;

      logger_id.log_app_update(
        app_version,
        bd_logger::AppVersionExtra::BuildNumber(build_number),
        app_install_size_bytes.into(),
        [].into(),
        Duration::seconds_f64(duration_s),
      );
      Ok(())
    },
    "swift wite app update log",
  );
}

#[no_mangle]
extern "C" fn capture_write_app_launch_tti_log(logger_id: LoggerId<'_>, duration_s: f64) {
  with_handle_unexpected(
    || -> anyhow::Result<()> {
      logger_id.log_app_launch_tti(Duration::seconds_f64(duration_s));
      Ok(())
    },
    "swift write app launch TTI log",
  );
}

#[no_mangle]
extern "C" fn capture_write_screen_view_log(logger_id: LoggerId<'_>, screen_name: *const Object) {
  with_handle_unexpected(
    || -> anyhow::Result<()> {
      let screen_name = unsafe { nsstring_into_string(screen_name) }?;
      logger_id.log_screen_view(screen_name);
      Ok(())
    },
    "swift write screen view log",
  );
}

#[no_mangle]
extern "C" fn capture_start_new_session(logger_id: LoggerId<'_>) {
  logger_id.start_new_session();
}

#[no_mangle]
extern "C" fn capture_get_session_id(logger_id: LoggerId<'_>) -> *const Object {
  make_nsstring(&logger_id.session_id())
    .unwrap_or_else(|_| make_empty_nsstring())
    .autorelease()
}

#[no_mangle]
extern "C" fn capture_get_device_id(logger_id: LoggerId<'_>) -> *const Object {
  make_nsstring(&logger_id.device_id())
    .unwrap_or_else(|_| make_empty_nsstring())
    .autorelease()
}

#[no_mangle]
extern "C" fn capture_get_sdk_version() -> *const Object {
  make_nsstring(&metadata::SDK_VERSION)
    .unwrap_or_else(|_| make_empty_nsstring())
    .autorelease()
}

#[no_mangle]
extern "C" fn capture_add_log_field(
  logger_id: LoggerId<'_>,
  key: *const c_char,
  value: *const c_char,
) {
  with_handle_unexpected(
    move || -> anyhow::Result<()> {
      let key = unsafe { CStr::from_ptr(key) }.to_str()?.to_string();
      let value = unsafe { CStr::from_ptr(value) }.to_str()?.to_string();

      logger_id.add_log_field(key, value.into());

      Ok(())
    },
    "swift add field",
  );
}

#[no_mangle]
extern "C" fn capture_remove_log_field(logger_id: LoggerId<'_>, key: *const c_char) {
  with_handle_unexpected(
    move || -> anyhow::Result<()> {
      let key = unsafe { CStr::from_ptr(key) }.to_str()?.to_string();
      logger_id.remove_log_field(&key);

      Ok(())
    },
    "swift remove field",
  );
}

#[no_mangle]
extern "C" fn capture_flush(logger_id: LoggerId<'_>, blocking: bool) {
  with_handle_unexpected(
    move || -> anyhow::Result<()> {
      let blocking = if blocking {
        Block::Yes(std::time::Duration::from_secs(1))
      } else {
        Block::No
      };
      logger_id.flush_state(blocking);

      Ok(())
    },
    "swift flush state",
  );
}

#[no_mangle]
extern "C" fn capture_set_feature_flag(
  logger_id: LoggerId<'_>,
  flag: *const c_char,
  variant: *const c_char,
) {
  with_handle_unexpected(
    move || -> anyhow::Result<()> {
      let flag = unsafe { CStr::from_ptr(flag) }.to_str()?;
      let variant = if variant.is_null() {
        None
      } else {
        unsafe { CStr::from_ptr(variant) }
          .to_str()
          .ok()
          .map(std::string::ToString::to_string)
      };
      logger_id.set_feature_flag(flag.to_string(), variant);

      Ok(())
    },
    "swift set feature flag",
  );
}

#[no_mangle]
extern "C" fn capture_set_feature_flags(logger_id: LoggerId<'_>, flags: *const Object) {
  with_handle_unexpected(
    move || -> anyhow::Result<()> {
      // Convert the Swift array of (flag: String, variant: String?) to Vec<(String,
      // Option<String>)>
      let flags_vec = unsafe { ffi::convert_feature_flags_array(flags) }?;
      logger_id.set_feature_flags(flags_vec);

      Ok(())
    },
    "swift set feature flags",
  );
}

#[no_mangle]
extern "C" fn capture_remove_feature_flag(logger_id: LoggerId<'_>, flag: *const c_char) {
  with_handle_unexpected(
    move || -> anyhow::Result<()> {
      let flag = unsafe { CStr::from_ptr(flag) }.to_str()?;
      logger_id.remove_feature_flag(flag.to_string());

      Ok(())
    },
    "swift remove feature flag",
  );
}

#[no_mangle]
extern "C" fn capture_clear_feature_flags(logger_id: LoggerId<'_>) {
  with_handle_unexpected(
    move || -> anyhow::Result<()> {
      logger_id.clear_feature_flags();

      Ok(())
    },
    "swift clear feature flags",
  );
}

#[no_mangle]
extern "C" fn capture_set_sleep_mode(logger_id: LoggerId<'_>, enabled: bool) {
  with_handle_unexpected(
    move || -> anyhow::Result<()> {
      logger_id.transition_sleep_mode(enabled);

      Ok(())
    },
    "swift transition sleep mode",
  );
}

mod flags {
  use bd_runtime::{bool_feature_flag, int_feature_flag};

  bool_feature_flag!(
    ReportSendDataTimeoutError,
    "ios.report_send_data_timeout",
    false
  );

  int_feature_flag!(SendDataTimeout, "ios.report_send_data_timeout_s", 60 * 3);
}

fn unix_milliseconds_to_date(millis_since_utc_epoch: i64) -> anyhow::Result<OffsetDateTime> {
  let seconds = millis_since_utc_epoch / 1000;
  let nano = (millis_since_utc_epoch % 1000) * 10_i64.pow(6);

  Ok(time::OffsetDateTime::from_unix_timestamp(seconds)? + Duration::nanoseconds(nano))
}
