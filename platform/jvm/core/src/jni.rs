// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./jni_test.rs"]
mod tests;

use crate::events::ListenerTargetHandler as EventsListenerTargetHandler;
use crate::key_value_storage::PreferencesHandle;
use crate::resource_utilization::TargetHandler as ResourceUtilizationTargetHandler;
use crate::session::SessionCallback;
use crate::session_replay::{self, TargetHandler as SessionReplayTargetHandler};
use crate::{
  define_object_wrapper,
  events,
  ffi,
  key_value_storage,
  report_processing,
  resource_utilization,
  session,
};
use anyhow::{anyhow, bail};
use bd_api::{PlatformNetworkStream, StreamEvent};
use bd_client_common::error::InvariantError;
use bd_crash_handler::CrashReportHook;
use bd_error_reporter::reporter::{
  MetadataErrorReporter,
  UnexpectedErrorHandler,
  handle_unexpected,
  handle_unexpected_error_with_details,
  with_handle_unexpected,
  with_handle_unexpected_or,
};
use bd_logger::{Block, CaptureSession, LogAttributesOverrides, LogFieldKind, LogFields};
use bd_proto::flatbuffers::report::bitdrift_public::fbs::issue_reporting::v_1::MemoryPressureLevel;
use bd_proto::protos::logging::payload::LogType;
use bd_session::Strategy;
use bd_session::configuration::{Callbacks, NoopCallbacks};
use futures_util::FutureExt;
use jni::objects::{
  Global,
  JClass,
  JMethodID,
  JObject,
  JObjectArray,
  JPrimitiveArray,
  JString,
  JValueOwned,
};
use jni::signature::{Primitive, ReturnType};
use jni::strings::JNIString;
use jni::sys::{
  JNI_ERR,
  JNI_TRUE,
  jboolean,
  jbyteArray,
  jdouble,
  jint,
  jlong,
  jobject,
  jstring,
  jvalue,
};
use jni::{AttachConfig, Env, EnvUnowned, JavaVM};
use platform_shared::metadata::{AndroidStaticFields, Mobile};
use platform_shared::{LoggerHolder, LoggerId, date_to_unix_milliseconds};
use protobuf::Enum as _;
use std::borrow::{Borrow, Cow};
use std::collections::HashMap;
use std::ffi::c_void;
use std::ops::DerefMut;
use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::{Arc, OnceLock};
use time::{Duration, OffsetDateTime};

// If we are running on Android, we need to initialize the logging system to send logs to
// `android_log` instead of `stderr. Use a compile time flag to determine if we are running on
// Android to avoid setting this up in JVM tests where we want to log to stderr.
#[cfg(target_os = "android")]
fn initialize_logging() {
  use android_logger::{Config, FilterBuilder};
  use log::LevelFilter;
  use std::sync::Once;

  static LOGGING_INIT: Once = Once::new();

  LOGGING_INIT.call_once(|| {
    // TODO(snowp): Ideally we use a tracing subscriber which embeds the span information like we
    // do everywhere else, as that would let us use trace spans to provide context for the logs.
    // Look into forking `tracing-android`.
    let rust_log = std::env::var("RUST_LOG").unwrap_or_else(|_| "info".to_string());

    // This can be called only once.
    android_logger::init_once(
      Config::default()
        .with_max_level(LevelFilter::Trace)
        .with_filter(FilterBuilder::new().parse(&rust_log).build()),
    );
  });
}

#[cfg(not(target_os = "android"))]
const fn initialize_logging() {}

//
// JValueWrapper
//

// Wrapper around jni related types that simplifies their conversion into a `jvalue`.
#[derive(Debug)]
pub enum JValueWrapper<'a> {
  Boolean(jboolean),
  I32(i32),
  I64(i64),
  Object(JObject<'a>),
  JObject(jobject),
}

impl From<JValueWrapper<'_>> for jvalue {
  fn from(wrapper: JValueWrapper<'_>) -> Self {
    match wrapper {
      JValueWrapper::Boolean(boolean) => jvalue { z: boolean },
      JValueWrapper::I32(int) => jvalue { i: int },
      JValueWrapper::I64(long) => jvalue { j: long },
      JValueWrapper::Object(object) => jvalue { l: object.as_raw() },
      JValueWrapper::JObject(jobject) => jvalue { l: jobject },
    }
  }
}

//
// CachedMethod
//

/// Wrapper around an method id that is initialized in `JNI_OnLoad` and can be used at a
/// later time to call a JVM method without first resolving the relevant class and method ids.
#[derive(Debug)]
pub(crate) struct CachedMethod {
  method_id: JMethodID,
}

impl CachedMethod {
  fn new(env: &mut Env<'_>, class: &JClass<'_>, name: &str, sig: &str) -> anyhow::Result<Self> {
    Ok(Self {
      method_id: env.get_method_id(
        class,
        JNIString::new(name),
        jni::signature::RuntimeMethodSignature::from_str(sig)?.method_signature(),
      )?,
    })
  }

  /// Invokes the method using the cached handle.
  pub(crate) fn call_method<'a>(
    &self,
    env: &mut Env<'a>,
    object: &JObject<'_>,
    return_type: ReturnType,
    args: &[jvalue],
  ) -> anyhow::Result<JValueOwned<'a>> {
    unsafe { Ok(env.call_method_unchecked(object, self.method_id, return_type, args)?) }
  }
}

//
// CachedClass
//

/// A cached global reference to a Class. Used to avoid continuously re-resolving the same class
/// multiple times and instead perform the lookup once during `JNI_OnLoad`.
#[derive(Debug)]
pub(crate) struct CachedClass {
  pub(crate) class: Global<JClass<'static>>,
}

impl CachedClass {
  /// Looks up the class by name from the provided environment.
  fn new(env: &mut Env<'_>, class_name: &str) -> jni::errors::Result<Self> {
    let class = env.find_class(JNIString::new(class_name))?;

    Ok(Self {
      class: env.new_global_ref(class)?,
    })
  }
}

// Below is the list of classes and methods that are used by the JNI layer and are initialized and
// cached at library load time.
// Caching of all classes and methods - including the ones that aren't on the hot path and don't
// need performance boost - is done to verify that library's ProGuard definition is correct at
// library load time.

// Cached method IDs

static METADATA_PROVIDER_TIMESTAMP: OnceLock<CachedMethod> = OnceLock::new();
static METADATA_PROVIDER_OOTB_FIELDS: OnceLock<CachedMethod> = OnceLock::new();
static METADATA_PROVIDER_CUSTOM_FIELDS: OnceLock<CachedMethod> = OnceLock::new();

static NETWORK_START_STREAM: OnceLock<CachedMethod> = OnceLock::new();

static STREAM_SEND_DATA: OnceLock<CachedMethod> = OnceLock::new();
static STREAM_SHUTDOWN: OnceLock<CachedMethod> = OnceLock::new();

static ERROR_REPORTER_REPORT_ERROR: OnceLock<CachedMethod> = OnceLock::new();

static STACK_TRACE_PROVIDER_INVOKE: OnceLock<CachedMethod> = OnceLock::new();

static REPORT_PROCESSING_SESSION_CURRENT: OnceLock<CachedClass> = OnceLock::new();
static REPORT_PROCESSING_SESSION_PREVIOUS_RUN: OnceLock<CachedClass> = OnceLock::new();

static ISSUE_REPORT_DISPATCHER_DISPATCH: OnceLock<CachedMethod> = OnceLock::new();
static ISSUE_REPORT_CLASS: OnceLock<CachedClass> = OnceLock::new();
static ISSUE_REPORT_CONSTRUCTOR: OnceLock<CachedMethod> = OnceLock::new();

static SDK_STATUS_CLASS: OnceLock<CachedClass> = OnceLock::new();
static SDK_STATUS_CONSTRUCTOR: OnceLock<CachedMethod> = OnceLock::new();

pub(crate) fn initialize_method_handle(
  env: &mut Env<'_>,
  class: &JClass<'_>,
  method_name: &str,
  signature: &str,
  handle: &OnceLock<CachedMethod>,
) -> anyhow::Result<()> {
  let method_id = CachedMethod::new(env, class, method_name, signature);

  let Ok(cached_id) = method_id else {
    check_exception(env);
    bail!("failed to resolve method");
  };

  // JNI_OnLoad runs before any other JNI function from this library, so this OnceLock can only be
  // initialized once.
  handle
    .set(cached_id)
    .map_err(|_| InvariantError::Invariant)?;
  Ok(())
}

pub(crate) fn initialize_class(
  env: &mut Env<'_>,
  class: &str,
  // Optional reference to `OnceLock` for cases when we don't want to store the cached value using
  // `OnceLock` to limit the number of locks we perform.
  handle: Option<&OnceLock<CachedClass>>,
) -> anyhow::Result<CachedClass> {
  let Ok(cached_class) = CachedClass::new(env, class) else {
    log::error!("failed to find {class} class");
    check_exception(env);
    bail!("failed to find class");
  };

  // JNI_OnLoad runs before any other JNI function from this library, so this OnceLock can only be
  // initialized once.
  if let Some(handle) = handle {
    // Global references are owned and cannot be cloned. Class lookup happens only during library
    // initialization, so make a second global reference for the caller and the cache.
    handle
      .set(CachedClass::new(env, class)?)
      .map_err(|_| InvariantError::Invariant)?;
  }

  Ok(cached_class)
}

fn check_exception(env: &mut Env<'_>) {
  match crate::executor::check_exception(env) {
    Ok(Some(exception)) => log::error!("failed with exception {exception}"),
    Ok(None) => log::error!("no active exception"),
    Err(e) => {
      log::error!("unable to resolve exception: {e}");
    },
  }
}

fn throw_java_exception(env: &mut Env<'_>, class: &str, message: &str) {
  if let Err(e) = env.throw_new(JNIString::new(class), JNIString::new(message)) {
    log::error!("failed to throw Java exception {class}: {e}; original message: {message}");
    check_exception(env);
  }
}

fn jni_load_inner(vm: &JavaVM) -> anyhow::Result<jint> {
  vm.attach_current_thread(|env| {
    let metadata_provider = initialize_class(env, "io/bitdrift/capture/IMetadataProvider", None)?;

    initialize_method_handle(
      env,
      metadata_provider.class.as_ref(),
      "timestamp",
      "()J",
      &METADATA_PROVIDER_TIMESTAMP,
    )?;
    initialize_method_handle(
      env,
      metadata_provider.class.as_ref(),
      "ootbFields",
      "()[Lio/bitdrift/capture/providers/Field;",
      &METADATA_PROVIDER_OOTB_FIELDS,
    )?;
    initialize_method_handle(
      env,
      metadata_provider.class.as_ref(),
      "customFields",
      "()[Lio/bitdrift/capture/providers/Field;",
      &METADATA_PROVIDER_CUSTOM_FIELDS,
    )?;

    let network_class = initialize_class(env, "io/bitdrift/capture/network/ICaptureNetwork", None)?;
    initialize_method_handle(
      env,
      network_class.class.as_ref(),
      "startStream",
      "(JLjava/util/Map;)Lio/bitdrift/capture/network/ICaptureStream;",
      &NETWORK_START_STREAM,
    )?;

    let stream_class = initialize_class(env, "io/bitdrift/capture/network/ICaptureStream", None)?;

    initialize_method_handle(
      env,
      stream_class.class.as_ref(),
      "sendData",
      "([B)V",
      &STREAM_SEND_DATA,
    )?;

    initialize_method_handle(
      env,
      stream_class.class.as_ref(),
      "shutdown",
      "()V",
      &STREAM_SHUTDOWN,
    )?;

    let error_reporter_class =
      initialize_class(env, "io/bitdrift/capture/error/IErrorReporter", None)?;
    initialize_method_handle(
      env,
      error_reporter_class.class.as_ref(),
      "reportError",
      "(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V",
      &ERROR_REPORTER_REPORT_ERROR,
    )?;

    let stack_trace_provider_class =
      initialize_class(env, "io/bitdrift/capture/StackTraceProvider", None)?;
    initialize_method_handle(
      env,
      stack_trace_provider_class.class.as_ref(),
      "invoke",
      "()Ljava/lang/String;",
      &STACK_TRACE_PROVIDER_INVOKE,
    )?;

    initialize_class(
      env,
      "io/bitdrift/capture/reports/processor/ReportProcessingSession$Current",
      Some(&REPORT_PROCESSING_SESSION_CURRENT),
    )?;
    initialize_class(
      env,
      "io/bitdrift/capture/reports/processor/ReportProcessingSession$PreviousRun",
      Some(&REPORT_PROCESSING_SESSION_PREVIOUS_RUN),
    )?;

    let issue_callback_configuration_class = initialize_class(
      env,
      "io/bitdrift/capture/reports/IssueCallbackConfiguration",
      None,
    )?;
    initialize_method_handle(
      env,
      issue_callback_configuration_class.class.as_ref(),
      "dispatch",
      "(Lio/bitdrift/capture/reports/Report;)V",
      &ISSUE_REPORT_DISPATCHER_DISPATCH,
    )?;

    let issue_report_class = initialize_class(
      env,
      "io/bitdrift/capture/reports/Report",
      Some(&ISSUE_REPORT_CLASS),
    )?;

    initialize_method_handle(
      env,
      issue_report_class.class.as_ref(),
      "<init>",
      "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V",
      &ISSUE_REPORT_CONSTRUCTOR,
    )?;

    let sdk_status_class = initialize_class(
      env,
      "io/bitdrift/capture/SdkStatus",
      Some(&SDK_STATUS_CLASS),
    )?;

    initialize_method_handle(
      env,
      sdk_status_class.class.as_ref(),
      "<init>",
      "(IJJ)V",
      &SDK_STATUS_CONSTRUCTOR,
    )?;

    key_value_storage::initialize(env)?;
    events::initialize(env)?;
    ffi::initialize(env)?;
    session::initialize(env)?;
    report_processing::initialize(env)?;
    resource_utilization::initialize(env)?;
    session_replay::initialize(env)?;

    Ok(env.version()?.into())
  })
}

#[unsafe(no_mangle)]
/// # Safety
///
/// The JVM calls this entrypoint with a valid `JavaVM` pointer during native-library loading.
pub unsafe extern "system" fn JNI_OnLoad(vm: *mut jni::sys::JavaVM, _: *mut c_void) -> jint {
  initialize_logging();
  // JNI owns the VM for the lifetime of the process and passes a valid pointer here.
  let vm = unsafe { JavaVM::from_raw(vm) };
  jni_load_inner(&vm)
    .inspect_err(|e| log::error!("JNI_OnLoad failed: {e}"))
    .unwrap_or(JNI_ERR)
}

//
// StreamState
//
struct StreamState {
  event_tx: bd_api::StreamEventSender,

  // Used to sanity check that we are correctly deallocating stream handles.
  active_streams: Arc<AtomicU32>,
}

impl Drop for StreamState {
  fn drop(&mut self) {
    self.active_streams.fetch_sub(1, Ordering::Relaxed);
  }
}

define_object_wrapper!(NetworkHandle);

struct Network {
  handle: NetworkHandle,

  // Used to track how many active streams there are, allowing us to sanity check cleanup in test.
  active_streams: Arc<AtomicU32>,
}

#[async_trait::async_trait]
impl bd_api::PlatformNetworkManager<bd_runtime::runtime::ConfigLoader> for Network {
  async fn start_stream(
    &self,
    event_tx: bd_api::StreamEventSender,
    _runtime: &bd_runtime::runtime::ConfigLoader,
    headers: &HashMap<&str, &str>,
  ) -> anyhow::Result<Box<dyn PlatformNetworkStream>> {
    self.active_streams.fetch_add(1, Ordering::Relaxed);

    let stream_event = Box::into_raw(Box::new(StreamState {
      event_tx,
      active_streams: self.active_streams.clone(),
    }));

    let res = self.handle.execute(|e, network| {
      let headers = ffi::map_to_jmap(e, headers)?;

      let handle = NETWORK_START_STREAM
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(
          e,
          network,
          ReturnType::Object,
          &[
            JValueWrapper::I64(stream_event as i64).into(),
            JValueWrapper::Object(headers).into(),
          ],
        )
        .and_then(|v| JValueOwned::l(v).map_err(|e| anyhow!(e)))?;

      Ok(Box::new(StreamHandle::new_global(e, handle)?) as Box<dyn PlatformNetworkStream>)
    });

    // At this point we should have allocated a new one but also deallocated the previous one. This
    // failing would indicate a leak.
    debug_assert_eq!(self.active_streams.load(Ordering::Relaxed), 1);

    res
  }
}

define_object_wrapper!(StreamHandle);

#[async_trait::async_trait]
impl bd_api::PlatformNetworkStream for StreamHandle {
  async fn send_data(&mut self, data: &[u8]) -> anyhow::Result<()> {
    self.execute(|e, stream| {
      let jarray = e.byte_array_from_slice(data)?;

      STREAM_SEND_DATA
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(
          e,
          stream,
          ReturnType::Primitive(Primitive::Void),
          &[JValueWrapper::JObject(jarray.as_raw()).into()],
        )
        .map(|_| ())
    })
  }
}

impl Drop for StreamHandle {
  fn drop(&mut self) {
    handle_unexpected(
      self.execute(|e, stream| {
        STREAM_SHUTDOWN
          .get()
          .ok_or(InvariantError::Invariant)?
          .call_method(e, stream, ReturnType::Primitive(Primitive::Void), &[])
          .map(|_| ())
      }),
      "stream shutdown",
    );
  }
}

#[allow(clippy::cast_sign_loss)]
#[unsafe(no_mangle)]
extern "system" fn Java_io_bitdrift_capture_network_Jni_onApiChunkReceived(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  stream_id: jlong,
  data: jbyteArray,
  size: jint,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      let stream_state: &StreamState = unsafe { &*(stream_id as *const StreamState) };

      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let slice = env.convert_byte_array(unsafe { JPrimitiveArray::from_raw(env, data) })?;

          let _ignored = stream_state
            .event_tx
            .blocking_send(StreamEvent::Data((&slice[.. (size as usize)]).into()));

          Ok(())
        },
        "jni chunk received",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_network_Jni_onApiStreamClosed(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  stream_id: jlong,
  reason: JString<'_>,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      let stream_state: &StreamState = unsafe { &*(stream_id as *const StreamState) };

      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let rust_str = reason.try_to_string(env)?;

          let _ignored = stream_state
            .event_tx
            .blocking_send(StreamEvent::StreamClosed(rust_str));

          Ok(())
        },
        "jni stream closed",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_network_Jni_releaseApiStream(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  stream_id: jlong,
) {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<()> {
      unsafe {
        let stream_state: &mut StreamState = &mut *(stream_id as *mut StreamState);
        drop(Box::from_raw(stream_state));
      };

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

define_object_wrapper!(ErrorReporterHandle);

impl bd_error_reporter::reporter::Reporter for ErrorReporterHandle {
  fn report(
    &self,
    message: &str,
    details: &Option<String>,
    fields: &HashMap<Cow<'_, str>, Cow<'_, str>>,
  ) {
    // No error handling to avoid a recursion.
    let _ignored = self.execute(|e, error_reporter| {
      let java_str = e.new_string(message)?;
      let details_str = e.new_string(details.clone().unwrap_or_default())?;
      let fields = ffi::map_to_jmap::<std::hash::RandomState>(
        e,
        &fields
          .iter()
          .map(|(k, v)| (k.borrow(), v.borrow()))
          .collect(),
      )?;

      ERROR_REPORTER_REPORT_ERROR
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(
          e,
          error_reporter,
          ReturnType::Primitive(Primitive::Void),
          &[
            JValueWrapper::JObject(java_str.as_raw()).into(),
            JValueWrapper::JObject(details_str.as_raw()).into(),
            JValueWrapper::Object(fields).into(),
          ],
        )
        .map(|_| ())
    });
  }
}

define_object_wrapper!(MetadataProvider);

impl bd_logger::MetadataProvider for MetadataProvider {
  #[allow(clippy::cast_possible_truncation)]
  fn timestamp(&self) -> anyhow::Result<time::OffsetDateTime> {
    self.execute(|e, provider| {
      let millis_since_utc_epoch = METADATA_PROVIDER_TIMESTAMP
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(e, provider, ReturnType::Primitive(Primitive::Long), &[])?
        .j()?;

      unix_milliseconds_to_date(millis_since_utc_epoch)
    })
  }

  fn fields(&self) -> anyhow::Result<(LogFields, LogFields)> {
    self.execute(|e, provider| {
      let ootb_fields = METADATA_PROVIDER_OOTB_FIELDS
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(e, provider, ReturnType::Object, &[])?
        .l()?;
      let ootb_fields_array = e.cast_local::<JObjectArray<'_>>(ootb_fields)?;
      let ootb_fields = ffi::jarray_to_fields(e, &ootb_fields_array)?;

      let custom_fields = METADATA_PROVIDER_CUSTOM_FIELDS
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(e, provider, ReturnType::Object, &[])?
        .l()?;
      let custom_fields_array = e.cast_local::<JObjectArray<'_>>(custom_fields)?;
      let custom_fields = ffi::jarray_to_fields(e, &custom_fields_array)?;

      Ok((custom_fields, ootb_fields))
    })
  }
}

define_object_wrapper!(IssueCallbackConfigurationHandle);

impl CrashReportHook for IssueCallbackConfigurationHandle {
  fn on_crash_report(&self, info: &bd_crash_handler::CrashReportInfo) {
    with_handle_unexpected(
      || -> anyhow::Result<()> {
        self.execute(|env, dispatcher| {
          let report_type = env.new_string(&info.report_type)?;
          let reason = env.new_string(info.crash_reason.as_deref().unwrap_or(""))?;
          let details = env.new_string(info.crash_details.as_deref().unwrap_or(""))?;
          let session_id = env.new_string(&info.session_id)?;

          let fields_map = platform_shared::log_fields_to_string_map(&info.fields);
          let fields = ffi::map_to_jmap(env, &fields_map)?;

          let report_class = ISSUE_REPORT_CLASS.get().ok_or(InvariantError::Invariant)?;
          let report_class: &JClass<'static> = report_class.class.as_ref();
          let report_obj = unsafe {
            env.new_object_unchecked(
              report_class,
              ISSUE_REPORT_CONSTRUCTOR
                .get()
                .ok_or(InvariantError::Invariant)?
                .method_id,
              &[
                jvalue {
                  l: report_type.as_raw(),
                },
                jvalue { l: reason.as_raw() },
                jvalue {
                  l: details.as_raw(),
                },
                jvalue {
                  l: session_id.as_raw(),
                },
                jvalue { l: fields.as_raw() },
              ],
            )?
          };

          ISSUE_REPORT_DISPATCHER_DISPATCH
            .get()
            .ok_or(InvariantError::Invariant)?
            .call_method(
              env,
              dispatcher,
              ReturnType::Primitive(Primitive::Void),
              &[jvalue {
                l: report_obj.as_raw(),
              }],
            )?;

          Ok(())
        })?;

        Ok(())
      },
      "jni issue report callback",
    );
  }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_createLogger(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  directory: JString<'_>,
  api_key: JString<'_>,
  initial_session_id: JString<'_>,
  inactivity_timeout_milliseconds: jlong,
  session_callback: JObject<'_>,
  metadata_provider: JObject<'_>,
  resource_utilization_target: JObject<'_>,
  session_replay_target: JObject<'_>,
  events_listener_target: JObject<'_>,
  application_id: JString<'_>,
  application_version: JString<'_>,
  os_version: JString<'_>,
  manufacturer: JString<'_>,
  model: JString<'_>,
  app_version_code: jlong,
  os_api_level: jint,
  architecture: JString<'_>,
  network: JObject<'_>,
  preferences: JObject<'_>,
  error_reporter: JObject<'_>,
  start_in_sleep_mode: jboolean,
  issue_report_callback: JObject<'_>,
) -> jlong {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<_> {
      Ok(with_handle_unexpected_or(
        || {
          let sdk_directory = PathBuf::from(directory.try_to_string(env)?);
          let network_manager = Box::new(Network {
            handle: NetworkHandle::new_global(&env, network)?,
            active_streams: Arc::new(AtomicU32::new(0)),
          });

          let preferences = PreferencesHandle::new_global(&env, preferences)?;
          let store = Arc::new(bd_key_value::Store::new(Box::new(preferences)));

          let initial_session_id = if initial_session_id.is_null() {
            None
          } else {
            Some(initial_session_id.try_to_string(env)?.into())
          };
          let callbacks: Arc<dyn Callbacks> = if session_callback.is_null() {
            Arc::new(NoopCallbacks)
          } else {
            Arc::new(SessionCallback::new_global(&env, session_callback)?)
          };
          let session = Strategy::configuration(
            &sdk_directory,
            initial_session_id,
            (inactivity_timeout_milliseconds >= 0)
              .then(|| time::Duration::milliseconds(inactivity_timeout_milliseconds)),
            callbacks,
            Arc::new(bd_time::SystemTimeProvider {}),
          );
          let active_session = session.strategy();

          let device = Arc::new(bd_device::Device::new(store.clone()));
          let static_metadata = Arc::new(Mobile::android(
            Some(application_id.try_to_string(env)?.into()),
            Some(application_version.try_to_string(env)?.into()),
            Some(os_version.try_to_string(env)?.into()),
            device.clone(),
            model.try_to_string(env)?.into(),
            AndroidStaticFields {
              manufacturer: manufacturer.try_to_string(env)?.into(),
              app_version_code,
              os_api_level,
              architecture: architecture.try_to_string(env)?,
            },
          ));
          let initial_ootb_fields = static_metadata.static_log_fields();

          let error_reporter = Arc::new(ErrorReporterHandle::new_global(&env, error_reporter)?);
          let error_reporter = MetadataErrorReporter::new(
            error_reporter,
            Arc::new(platform_shared::error::SessionProvider::new(active_session)),
            static_metadata.clone(),
          );

          let resource_utilization_target = Box::new(ResourceUtilizationTargetHandler::new_global(
            &env,
            resource_utilization_target,
          )?);

          let session_replay_target = Box::new(SessionReplayTargetHandler::new_global(
            &env,
            session_replay_target,
          )?);

          let events_listener_target = Box::new(EventsListenerTargetHandler::new_global(
            &env,
            events_listener_target,
          )?);

          // Errors emitted up until this point are not reported to bitdrift remote.
          // TODO(Augustyniak): Make it more obvious that as much work as possible should be done
          // after the error reporter is set up.
          UnexpectedErrorHandler::set_reporter(Arc::new(error_reporter));

          let crash_report_hook: Option<Arc<dyn CrashReportHook>> =
            if issue_report_callback.is_null() {
              None
            } else {
              Some(Arc::new(IssueCallbackConfigurationHandle::new_global(
                &env,
                issue_report_callback,
              )?))
            };

          let java_vm = env.get_java_vm()?;
          let logger = bd_logger::LoggerBuilder::new(bd_logger::InitParams {
            sdk_directory,
            api_key: api_key.try_to_string(env)?,
            session,
            metadata_provider: Arc::new(MetadataProvider::new_global(&env, metadata_provider)?),
            initial_ootb_fields,
            initial_custom_fields: [].into(),
            resource_utilization_target,
            session_replay_target,
            events_listener_target,
            device,
            store,
            network: network_manager,
            static_metadata: static_metadata.clone(),
            start_in_sleep_mode: start_in_sleep_mode == JNI_TRUE,
          })
          .with_internal_logger(true)
          .with_crash_report_hook(crash_report_hook)
          .build()
          .map(|(logger, _, future, _)| {
            LoggerHolder::new_with_static_metadata(
              logger,
              async move {
                handle_unexpected(
                  java_vm.attach_current_thread_with_config(
                    || AttachConfig::new().thread_name(jni::jni_str!("bd-tokio")),
                    None,
                    |_| Ok::<(), jni::errors::Error>(()),
                  ),
                  "jni attach logger thread",
                );

                let result = future.await;

                // The logger runtime owns this dedicated OS thread. Keep the JVM attachment for
                // its lifetime so JNI callbacks do not repeatedly attach on every log, but detach
                // before the thread begins TLS teardown. This clears jni-rs' attach guard while
                // Rust's logging TLS is still available.
                handle_unexpected(java_vm.detach_current_thread(), "jni detach logger thread");

                result
              }
              .boxed(),
              Some(static_metadata),
            )
          })?;

          Ok(logger.into_raw().into())
        },
        -1,
        "jni create logger",
      ))
    })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_startLogger(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
) {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<()> {
      let logger = unsafe { LoggerId::from_raw(logger_id) };
      logger.start();

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_getSdkStatus(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
) -> jobject {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<_> {
      Ok(with_handle_unexpected_or(
        || {
          let logger = unsafe { LoggerId::from_raw(logger_id) };
          let status = logger.get_sdk_status();

          let initialization_state = status.initialization_state as i32;
          let last_handshake_time = date_to_unix_milliseconds(status.last_handshake_time);
          let last_config_delivery_time =
            date_to_unix_milliseconds(status.last_config_delivery_time);

          let sdk_status_class = SDK_STATUS_CLASS.get().ok_or(InvariantError::Invariant)?;
          let sdk_status_class: &JClass<'static> = sdk_status_class.class.as_ref();
          let obj = unsafe {
            env.new_object_unchecked(
              sdk_status_class,
              SDK_STATUS_CONSTRUCTOR
                .get()
                .ok_or(InvariantError::Invariant)?
                .method_id,
              &[
                jvalue {
                  i: initialization_state,
                },
                jvalue {
                  j: last_handshake_time,
                },
                jvalue {
                  j: last_config_delivery_time,
                },
              ],
            )?
          };

          Ok(obj.as_raw())
        },
        JObject::null().as_raw(),
        "jni get_sdk_status",
      ))
    })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_destroyLogger(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
) {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<()> {
      let _: () = unsafe { LoggerHolder::destroy(logger_id) };
      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_startNewSession(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: LoggerId<'_>,
  session_id: JString<'_>,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || {
          let session_id = (!session_id.is_null())
            .then(|| session_id.try_to_string(env))
            .transpose()?
            .map(Into::into);
          logger_id.start_new_session(session_id)
        },
        "jni start new session",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_getSessionId<'a>(
  mut env: EnvUnowned<'a>,
  _class: JClass<'_>,
  logger_id: LoggerId<'_>,
) -> JString<'a> {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<_> {
      Ok(with_handle_unexpected_or(
        || Ok(env.new_string(logger_id.session_id()?)?),
        JString::null(),
        "jni get_session_id",
      ))
    })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_getDeviceId<'a>(
  mut env: EnvUnowned<'a>,
  _class: JClass<'_>,
  logger_id: LoggerId<'_>,
) -> JString<'a> {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<_> {
      Ok(with_handle_unexpected_or(
        || Ok(env.new_string(logger_id.device_id())?),
        JString::null(),
        "jni get_device_id",
      ))
    })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_isTracingActive(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: LoggerId<'_>,
) -> jboolean {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<_> { Ok(logger_id.is_tracing_active()) })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_addLogField(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  key: JString<'_>,
  value: JString<'_>,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let key = key.try_to_string(env)?;
          let value = value.try_to_string(env)?;

          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.add_log_field(key, value.into());

          Ok(())
        },
        "jni add log field",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_removeLogField(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  key: JString<'_>,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let key = key.try_to_string(env)?;

          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.remove_log_field(&key);

          Ok(())
        },
        "jni add log field",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_setFeatureFlagExposure(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  key: JString<'_>,
  variant: JString<'_>,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let key = key.try_to_string(env)?;
          let variant = if variant.is_null() {
            None
          } else {
            Some(variant.try_to_string(env)?)
          };

          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.set_feature_flag_exposure(key, variant);

          Ok(())
        },
        "jni set feature flag exposure",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_setEntityId(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  entity_id: JString<'_>,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let entity_id = entity_id.try_to_string(env)?;

          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.register_opaque_entity_id(Some(&entity_id));

          Ok(())
        },
        "jni set entity id",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_clearEntityId(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
) {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.register_opaque_entity_id(None);

          Ok(())
        },
        "jni clear entity id",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
// Java types are always signed, but log level/type are both unsigned.
#[allow(clippy::cast_sign_loss)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeLog(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  log_type: jint,
  log_level: jint,
  log: JString<'_>,
  field_keys: JObjectArray<'_>,
  field_values: JObjectArray<'_>,
  matching_field_keys: JObjectArray<'_>,
  matching_field_values: JObjectArray<'_>,
  use_previous_process_session_id: jboolean,
  override_occurred_at_unix_milliseconds: jlong,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      // This should only fail if the JVM is in a bad state.
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let fields = ffi::string_arrays_to_annotated_fields(
            env,
            &field_keys,
            &field_values,
            LogFieldKind::Ootb,
          )?;
          let matching_fields = ffi::string_arrays_to_annotated_fields(
            env,
            &matching_field_keys,
            &matching_field_values,
            LogFieldKind::Ootb,
          )?;

          let attributes_overrides = if use_previous_process_session_id != JNI_TRUE
            && override_occurred_at_unix_milliseconds <= 0
          {
            None
          } else if use_previous_process_session_id != JNI_TRUE
            && override_occurred_at_unix_milliseconds > 0
          {
            Some(LogAttributesOverrides::OccurredAt(
              unix_milliseconds_to_date(override_occurred_at_unix_milliseconds)?,
            ))
          } else {
            Some(LogAttributesOverrides::PreviousRunSessionID(
              unix_milliseconds_to_date(override_occurred_at_unix_milliseconds)?,
            ))
          };

          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.log(
            log_level as u32,
            LogType::from_i32(log_type).unwrap_or(LogType::NORMAL),
            log.try_to_string(env)?.into(),
            fields,
            matching_fields,
            attributes_overrides,
            &CaptureSession::default(),
          );

          Ok(())
        },
        "jni write log",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_shutdown(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
) {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          // NOTE: This performs a blocking shutdown of the logger for use in test and eventual
          // public API. This needs additional testing before exposing in the public API.
          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.shutdown(true);
          Ok(())
        },
        "shutdown",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeSessionReplayScreenLog(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  fields: JObjectArray<'_>,
  duration_s: jdouble,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let fields = ffi::jarray_to_annotated_fields(env, &fields, LogFieldKind::Ootb)?;

          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.log_session_replay_screen(fields, Duration::seconds_f64(duration_s));

          Ok(())
        },
        "jni write replay screen log",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeSessionReplayScreenshotLog(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  fields: JObjectArray<'_>,
  duration_s: jdouble,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let fields = ffi::jarray_to_annotated_fields(env, &fields, LogFieldKind::Ootb)?;

          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.log_session_replay_screenshot(fields, Duration::seconds_f64(duration_s));

          Ok(())
        },
        "jni write replay screenshot log",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeResourceUtilizationLog(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  fields: JObjectArray<'_>,
  duration_s: jdouble,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let fields = ffi::jarray_to_annotated_fields(env, &fields, LogFieldKind::Ootb)?;

          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.log_resource_utilization(fields, Duration::seconds_f64(duration_s));

          Ok(())
        },
        "jni write resource utilization log",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeSDKStartLog(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  fields: JObjectArray<'_>,
  duration_s: jdouble,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let fields = ffi::jarray_to_annotated_fields(env, &fields, LogFieldKind::Ootb)?;

          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.log_sdk_start(fields, Duration::seconds_f64(duration_s));

          Ok(())
        },
        "jni write resource utilization log",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_shouldWriteAppUpdateLog(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  app_version: JString<'_>,
  app_version_code: jlong,
) -> bool {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<_> {
      Ok(with_handle_unexpected_or(
        || {
          let app_version = app_version.try_to_string(env)?;

          let logger = unsafe { LoggerId::from_raw(logger_id) };
          Ok(logger.should_log_app_update(
            app_version,
            bd_logger::AppVersionExtra::AppVersionCode(app_version_code),
          ))
        },
        false,
        "swift should log app update",
      ))
    })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

// Java types are always signed, but app_install_size_bytes is unsigned.
#[unsafe(no_mangle)]
#[allow(clippy::cast_sign_loss)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeAppUpdateLog(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  app_version: JString<'_>,
  app_version_code: jlong,
  app_install_size_bytes: jlong,
  duration_s: f64,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let app_version = app_version.try_to_string(env)?;

          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.log_app_update(
            app_version,
            bd_logger::AppVersionExtra::AppVersionCode(app_version_code),
            Some(app_install_size_bytes as u64),
            [].into(),
            Duration::seconds_f64(duration_s),
          );

          Ok(())
        },
        "jni write app update log",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeAppLaunchTTILog(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  duration_s: f64,
) {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.log_app_launch_tti(Duration::seconds_f64(duration_s));

          Ok(())
        },
        "jni write app launch TTI log",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeScreenViewLog(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  screen_name: JString<'_>,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let screen_name = screen_name.try_to_string(env)?;
          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.log_screen_view(screen_name);

          Ok(())
        },
        "jni write screen view log",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_flush(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  blocking: jboolean,
) {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let logger = unsafe { LoggerId::from_raw(logger_id) };
          let block = if blocking == JNI_TRUE {
            Block::Yes {
              timeout: std::time::Duration::from_millis(500),
              poll_callback: None,
            }
          } else {
            Block::No
          };
          logger.flush_state(block);

          Ok(())
        },
        "jni flush",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_debugDebug(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  message: JString<'_>,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      let _: () = {
        if let Ok(message) = message.try_to_string(env) {
          log::debug!("jni log: {message}");
        }
      };
      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_debugError(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  message: JString<'_>,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      let _: () = {
        if let Ok(message) = message.try_to_string(env) {
          log::error!("jni log: {message}");
        }
      };
      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_reportError(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  message: JString<'_>,
  stack_trace_provider: JObject<'_>,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      let _: () = {
        if let Ok(message) = message.try_to_string(env) {
          handle_unexpected_error_with_details(anyhow!(message), "jni reported", || {
            exception_stacktrace(env, &stack_trace_provider).unwrap_or_else(|_| {
              if let Ok(msg) = crate::executor::check_exception(env) {
                log::warn!("failed to extract stacktrace: {msg:?}");
              }
              None
            })
          });
        }
      };
      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_setSleepModeEnabled(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  enabled: jboolean,
) {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.transition_sleep_mode(enabled == JNI_TRUE);

          Ok(())
        },
        "jni transition sleep mode",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeMemoryPressureLevel(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  level: jint,
) {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let level = MemoryPressureLevel(i8::try_from(level).unwrap_or(0));

          let logger = unsafe { LoggerId::from_raw(logger_id) };
          logger.notify_memory_pressure(level);

          Ok(())
        },
        "jni notify memory pressure level",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_previousMemoryPressureLevel(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
) -> jint {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<_> {
      Ok(with_handle_unexpected_or(
        || {
          let logger = unsafe { LoggerId::from_raw(logger_id) };
          Ok(logger.previous_memory_pressure_level().0.into())
        },
        0,
        "jni previous memory pressure level",
      ))
    })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_processIssueReports(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  mut logger_id: LoggerId<'_>,
  session: JObject<'_>,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let current_processing_session_type = &REPORT_PROCESSING_SESSION_CURRENT
            .get()
            .ok_or(InvariantError::Invariant)?
            .class;

          let previous_processing_session_type = &REPORT_PROCESSING_SESSION_PREVIOUS_RUN
            .get()
            .ok_or(InvariantError::Invariant)?
            .class;

          let report_processing_session =
            if env.is_instance_of(&session, current_processing_session_type)? {
              bd_logger::ReportProcessingSession::Current
            } else if env.is_instance_of(&session, previous_processing_session_type)? {
              bd_logger::ReportProcessingSession::PreviousRun
            } else {
              bail!("invalid ReportProcessingSession type: expected Current or PreviousRun");
            };

          logger_id
            .deref_mut()
            .process_crash_reports(report_processing_session);
          Ok(())
        },
        "jni process issue reports",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_processAndPersistANR(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: LoggerId<'_>,
  stream: JObject<'_>,
  timestamp: jlong,
  destination: JString<'_>,
  attributes: JObject<'_>,
  running_state: JString<'_>,
  app_exit_description: JString<'_>,
  memory_pressure_level: jint,
  is_file_size_optimization_enabled: jboolean,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      let _: () = {
        let destination = match destination.try_to_string(env) {
          Ok(destination) => destination,
          Err(e) => {
            let message = format!("jni persist ANR: failed to parse destination: {e}");
            throw_java_exception(env, "java/lang/IllegalArgumentException", &message);
            return Ok(());
          },
        };

        let running_state_str = if running_state.is_null() {
          None
        } else {
          running_state.try_to_string(env).ok()
        };

        let app_exit_description_str = if app_exit_description.is_null() {
          None
        } else {
          app_exit_description
            .try_to_string(env)
            .ok()
            .filter(|s| !s.is_empty())
        };

        let stream = (!stream.is_null()).then_some(&stream);
        let result = logger_id
          .static_metadata()
          .ok_or_else(|| anyhow!("missing static logger metadata"))
          .and_then(|metadata| {
            let mut context = report_processing::AndroidReportContext {
              env,
              metadata,
              attributes: &attributes,
            };
            let report = report_processing::AnrReport {
              source_stream: stream,
              timestamp_millis: timestamp,
              destination: &destination,
              running_state: running_state_str.as_deref(),
              app_exit_description: app_exit_description_str.as_deref(),
              memory_pressure_level,
              is_file_size_optimization_enabled: is_file_size_optimization_enabled == JNI_TRUE,
            };
            report_processing::persist_anr(&mut context, &report)
          });

        match result {
          Ok(()) => {},
          Err(e) => {
            let message = format!("jni persist ANR: {e:#}");
            throw_java_exception(env, "java/io/IOException", &message);
          },
        }
      };
      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_processAndPersistJavaScriptError(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: LoggerId<'_>,
  error_name: JString<'_>,
  error_message: JString<'_>,
  stack_trace: JString<'_>,
  is_fatal: jboolean,
  engine: JString<'_>,
  debugger_id: JString<'_>,
  timestamp: jlong,
  destination: JString<'_>,
  attributes: JObject<'_>,
  sdk_version: JString<'_>,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      with_handle_unexpected(
        || -> anyhow::Result<()> {
          let error_name = error_name
            .try_to_string(env)
            .map_err(|e| anyhow::anyhow!("failed to parse error_name: {e}"))?;
          let error_message = error_message
            .try_to_string(env)
            .map_err(|e| anyhow::anyhow!("failed to parse error_message: {e}"))?;
          let stack_trace = stack_trace
            .try_to_string(env)
            .map_err(|e| anyhow::anyhow!("failed to parse stack_trace: {e}"))?;
          let engine = engine
            .try_to_string(env)
            .map_err(|e| anyhow::anyhow!("failed to parse engine: {e}"))?;
          let debugger_id = debugger_id
            .try_to_string(env)
            .map_err(|e| anyhow::anyhow!("failed to parse debugger_id: {e}"))?;
          let destination = destination
            .try_to_string(env)
            .map_err(|e| anyhow::anyhow!("failed to parse destination: {e}"))?;
          let sdk_version = sdk_version
            .try_to_string(env)
            .map_err(|e| anyhow::anyhow!("failed to parse sdk_version: {e}"))?;

          let metadata = logger_id
            .static_metadata()
            .ok_or_else(|| anyhow!("missing static logger metadata"))?;
          let mut context = report_processing::AndroidReportContext {
            env,
            metadata,
            attributes: &attributes,
          };
          let report = report_processing::JavaScriptErrorReport {
            error_name: &error_name,
            error_message: &error_message,
            stack_trace: &stack_trace,
            is_fatal,
            engine: &engine,
            debugger_id: &debugger_id,
            timestamp_millis: timestamp,
            destination: &destination,
            sdk_version: &sdk_version,
          };
          report_processing::persist_javascript_error(&mut context, &report)?;
          Ok(())
        },
        "jni persist JavaScript error",
      );

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

fn exception_stacktrace(
  env: &mut Env<'_>,
  stack_trace_provider: &JObject<'_>,
) -> anyhow::Result<Option<String>> {
  let stacktrace = STACK_TRACE_PROVIDER_INVOKE
    .get()
    .ok_or(InvariantError::Invariant)?
    .call_method(env, stack_trace_provider, ReturnType::Object, &[])?
    .l()?;
  let stacktrace = env.cast_local::<JString<'_>>(stacktrace)?;

  let stacktrace_str = stacktrace.try_to_string(env)?;
  Ok(Some(stacktrace_str))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_Jni_isRuntimeEnabled(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  feature: JString<'_>,
  default_value: jboolean,
) -> jboolean {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<_> {
      Ok(with_handle_unexpected_or(
        || {
          // We default the feature to default_value to so that we don't require sending anything
          // over the wire in order to enable a feature (the default), leaving this as
          // a kill switch in case we need to override what the user configured.
          let logger = unsafe { LoggerId::from_raw(logger_id) };

          Ok(logger.runtime_snapshot().get_bool(
            feature.try_to_string(env)?.as_str(),
            default_value == JNI_TRUE,
          ))
        },
        default_value == JNI_TRUE,
        "jni isFeatureEnabled",
      ))
    })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

#[unsafe(no_mangle)]
// Java/Kotlin types are always signed, but get_integer is unsigned.
#[allow(clippy::cast_sign_loss)]
pub extern "system" fn Java_io_bitdrift_capture_Jni_runtimeValue(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  variable_name: JString<'_>,
  default_value: jint,
) -> jint {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<_> {
      Ok(with_handle_unexpected_or(
        || {
          let logger = unsafe { LoggerId::from_raw(logger_id) };
          let binding = variable_name.try_to_string(env)?;
          let variable_name = binding.as_str();
          let integer_value = logger
            .runtime_snapshot()
            .get_integer(variable_name, default_value as u32);

          Ok(jint::try_from(integer_value).unwrap_or(default_value))
        },
        default_value,
        "jni runtimeValue",
      ))
    })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_Jni_runtimeStringValue(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger_id: LoggerId<'_>,
  variable_name: JString<'_>,
  default_value: JString<'_>,
) -> jstring {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<_> {
      Ok(with_handle_unexpected_or(
        || {
          let (logger, variable_name) =
            runtime_logger_and_variable_name(env, logger_id, &variable_name)?;
          let default_value = default_value.try_to_string(env)?.as_str().to_string();
          let value = logger
            .runtime_snapshot()
            .get_string(&variable_name, default_value);
          Ok(env.new_string(value)?.into_raw())
        },
        JObject::null().into_raw(),
        "jni runtimeStringValue",
      ))
    })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

fn runtime_logger_and_variable_name<'a>(
  env: &Env<'a>,
  logger_id: LoggerId<'a>,
  variable_name: &JString<'a>,
) -> anyhow::Result<(LoggerId<'a>, String)> {
  let variable_name = variable_name.try_to_string(env)?.as_str().to_string();

  Ok((logger_id, variable_name))
}

fn unix_milliseconds_to_date(millis_since_utc_epoch: i64) -> anyhow::Result<OffsetDateTime> {
  let seconds = millis_since_utc_epoch / 1000;
  let nano = (millis_since_utc_epoch % 1000) * 10_i64.pow(6);

  Ok(time::OffsetDateTime::from_unix_timestamp(seconds)? + Duration::nanoseconds(nano))
}
