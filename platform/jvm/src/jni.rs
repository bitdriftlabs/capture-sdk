// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::events::ListenerTargetHandler as EventsListenerTargetHandler;
use crate::executor::{self};
use crate::key_value_storage::PreferencesHandle;
use crate::resource_utilization::TargetHandler as ResourceUtilizationTargetHandler;
use crate::session::SessionStrategyConfigurationHandle;
use crate::session_replay::{self, TargetHandler as SessionReplayTargetHandler};
use crate::{
  define_object_wrapper,
  events,
  ffi,
  key_value_storage,
  new_global,
  resource_utilization,
  session,
};
use anyhow::anyhow;
use bd_api::{Platform, PlatformNetworkStream, StreamEvent};
use bd_client_common::error::{
  with_handle_unexpected_or,
  MetadataErrorReporter,
  UnexpectedErrorHandler,
};
use bd_logger::{LogAttributesOverrides, LogFieldKind, LogFields, LogType};
use jni::descriptors::Desc;
use jni::objects::{
  GlobalRef,
  JClass,
  JMethodID,
  JObject,
  JPrimitiveArray,
  JString,
  JValueGen,
  JValueOwned,
};
use jni::signature::{Primitive, ReturnType};
use jni::sys::{jboolean, jbyteArray, jdouble, jint, jlong, jobject, jvalue, JNI_TRUE};
use jni::{JNIEnv, JavaVM};
use platform_shared::metadata::Mobile;
use platform_shared::{LoggerHolder, LoggerId};
use std::borrow::{Borrow, Cow};
use std::collections::HashMap;
use std::ffi::c_void;
use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::{Arc, OnceLock};
use time::{Duration, OffsetDateTime};

// If we are running on Android, we need to initialize the logging system to send logs to
// `android_log` instead of `stderr. Use a compile time flag to determine if we are running on
// Android to avoid setting this up in JVM tests where we want to log to stderr.
#[cfg(target_os = "android")]
fn initialize_logging() {
  use android_logger::Config;
  use log::LevelFilter;
  use std::sync::Once;

  static LOGGING_INIT: Once = Once::new();


  LOGGING_INIT.call_once(|| {
    // TODO(snowp): Ideally we use a tracing subscriber which embeds the span information like we
    // do everywhere else, as that would let us use trace spans to provide context for the logs.
    // Look into forking `tracing-android`.
    let level = std::env::var("RUST_LOG")
      .unwrap_or_else(|_| "info".to_string())
      .parse::<LevelFilter>()
      .unwrap_or(LevelFilter::Info);

    // This can be called only once.
    android_logger::init_once(Config::default().with_max_level(level));
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
  fn new<'local, 'other_local, T: Desc<'local, JClass<'other_local>>>(
    env: &mut JNIEnv<'local>,
    class: T,
    name: &str,
    sig: &str,
  ) -> anyhow::Result<Self> {
    Ok(Self {
      method_id: env.get_method_id(class, name, sig)?,
    })
  }

  /// Invokes the method using the cached handle.
  pub(crate) fn call_method<'a>(
    &self,
    env: &mut JNIEnv<'a>,
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
#[derive(Debug, Clone)]
pub(crate) struct CachedClass {
  pub(crate) class: GlobalRef,
}

impl CachedClass {
  /// Looks up the class by name from the provided environment.
  fn new(env: &mut JNIEnv<'_>, class_name: &str) -> jni::errors::Result<Self> {
    let class = env.find_class(class_name)?;

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

pub(crate) fn initialize_method_handle<
  'local,
  'other_local,
  T: Desc<'local, JClass<'other_local>> + std::fmt::Debug + Copy,
>(
  env: &mut JNIEnv<'local>,
  class: T,
  method_name: &str,
  signature: &str,
  handle: &OnceLock<CachedMethod>,
) {
  let method_id = CachedMethod::new(env, class, method_name, signature);

  let Ok(cached_id) = method_id else {
    log::error!("failed to resolve {class:?}::{method_name} with signature {signature}");
    check_exception(env);
    panic!("failed to resolve method");
  };

  // Safety: As long as this is called from within JNI_OnLoad this is called immediately following
  // dlopen for this JNI library. This means that it is guaranteed to complete before any other
  // JNI function from this library is invoked, making it safe to unwrap here.
  handle.set(cached_id).expect("double init");
}

pub(crate) fn initialize_class(
  env: &mut JNIEnv<'_>,
  class: &str,
  // Optional reference to `OnceLock` for cases when we don't want to store the cached value using
  // `OnceLock` to limit the number of locks we perform.
  handle: Option<&OnceLock<CachedClass>>,
) -> CachedClass {
  let Ok(cached_class) = CachedClass::new(env, class) else {
    log::error!("failed to find {class} class");
    check_exception(env);
    panic!("failed to find class");
  };

  // Safety: As long as this is called from within JNI_OnLoad this is called immediately following
  // dlopen for this JNI library. This means that it is guaranteed to complete before any other
  // JNI function from this library is invoked, making it safe to unwrap here.
  if let Some(handle) = handle {
    handle.set(cached_class.clone()).expect("cached class");
  }

  cached_class
}

fn check_exception(env: &mut JNIEnv<'_>) {
  match executor::check_exception(env) {
    Ok(Some(exception)) => log::error!("failed with exception {exception}"),
    Ok(None) => log::error!("no active exception"),
    Err(e) => {
      log::error!("unable to resolve exception: {e}");
    },
  }
}

#[no_mangle]
pub extern "system" fn JNI_OnLoad(vm: JavaVM, _: *mut c_void) -> jint {
  let mut env = vm.get_env().expect("Cannot get reference to the JNIEnv");

  let metadata_provider = initialize_class(&mut env, "io/bitdrift/capture/IMetadataProvider", None);

  initialize_method_handle(
    &mut env,
    &metadata_provider.class,
    "timestamp",
    "()J",
    &METADATA_PROVIDER_TIMESTAMP,
  );
  initialize_method_handle(
    &mut env,
    &metadata_provider.class,
    "ootbFields",
    "()Ljava/util/List;",
    &METADATA_PROVIDER_OOTB_FIELDS,
  );
  initialize_method_handle(
    &mut env,
    &metadata_provider.class,
    "customFields",
    "()Ljava/util/List;",
    &METADATA_PROVIDER_CUSTOM_FIELDS,
  );

  initialize_method_handle(
    &mut env,
    "io/bitdrift/capture/network/ICaptureNetwork",
    "startStream",
    "(JLjava/util/Map;)Lio/bitdrift/capture/network/ICaptureStream;",
    &NETWORK_START_STREAM,
  );

  let stream_class = initialize_class(&mut env, "io/bitdrift/capture/network/ICaptureStream", None);

  initialize_method_handle(
    &mut env,
    &stream_class.class,
    "sendData",
    "([B)V",
    &STREAM_SEND_DATA,
  );

  initialize_method_handle(
    &mut env,
    &stream_class.class,
    "shutdown",
    "()V",
    &STREAM_SHUTDOWN,
  );

  initialize_method_handle(
    &mut env,
    "io/bitdrift/capture/error/IErrorReporter",
    "reportError",
    "(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V",
    &ERROR_REPORTER_REPORT_ERROR,
  );

  initialize_method_handle(
    &mut env,
    "io/bitdrift/capture/StackTraceProvider",
    "invoke",
    "()Ljava/lang/String;",
    &STACK_TRACE_PROVIDER_INVOKE,
  );

  key_value_storage::initialize(&mut env);
  events::initialize(&mut env);
  ffi::initialize(&mut env);
  session::initialize(&mut env);
  resource_utilization::initialize(&mut env);
  session_replay::initialize(&mut env);

  env.get_version().unwrap().into()
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
        .unwrap()
        .call_method(
          e,
          network,
          ReturnType::Object,
          &[
            JValueWrapper::I64(stream_event as i64).into(),
            JValueWrapper::Object(headers).into(),
          ],
        )
        .and_then(|v| JValueGen::l(v).map_err(|e| anyhow!(e)))?;

      Ok(Box::new(new_global!(StreamHandle, e, handle)?) as Box<dyn PlatformNetworkStream>)
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
        .unwrap()
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
    bd_client_common::error::handle_unexpected(
      self.execute(|e, stream| {
        STREAM_SHUTDOWN
          .get()
          .unwrap()
          .call_method(e, stream, ReturnType::Primitive(Primitive::Void), &[])
          .map(|_| ())
      }),
      "stream shutdown",
    );
  }
}

#[allow(clippy::cast_sign_loss)]
#[no_mangle]
extern "system" fn Java_io_bitdrift_capture_network_Jni_onApiChunkReceived(
  env: JNIEnv<'_>,
  _class: JClass<'_>,
  stream_id: jlong,
  data: jbyteArray,
  size: jint,
) {
  let stream_state: &StreamState = unsafe { &*(stream_id as *const StreamState) };

  bd_client_common::error::with_handle_unexpected(
    || -> anyhow::Result<()> {
      let slice = env.convert_byte_array(unsafe { JPrimitiveArray::from_raw(data) })?;

      let _ignored = stream_state
        .event_tx
        .blocking_send(StreamEvent::Data((&slice[.. (size as usize)]).into()));

      Ok(())
    },
    "jni chunk received",
  );
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_network_Jni_onApiStreamClosed(
  env: JNIEnv<'_>,
  _class: JClass<'_>,
  stream_id: jlong,
  reason: JString<'_>,
) {
  let stream_state: &StreamState = unsafe { &*(stream_id as *const StreamState) };

  bd_client_common::error::with_handle_unexpected(
    || -> anyhow::Result<()> {
      let rust_str = unsafe { env.get_string_unchecked(&reason)? }.into();

      let _ignored = stream_state
        .event_tx
        .blocking_send(StreamEvent::StreamClosed(rust_str));

      Ok(())
    },
    "jni stream closed",
  );
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_network_Jni_releaseApiStream(
  _env: JNIEnv<'_>,
  _class: JClass<'_>,
  stream_id: jlong,
) {
  unsafe {
    let stream_state: &mut StreamState = &mut *(stream_id as *mut StreamState);
    drop(Box::from_raw(stream_state));
  };
}

define_object_wrapper!(ErrorReporterHandle);

impl bd_client_common::error::Reporter for ErrorReporterHandle {
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
        .unwrap()
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
        .unwrap()
        .call_method(e, provider, ReturnType::Primitive(Primitive::Long), &[])?
        .j()?;

      unix_milliseconds_to_date(millis_since_utc_epoch)
    })
  }

  fn fields(&self) -> anyhow::Result<(LogFields, LogFields)> {
    self.execute(|e, provider| {
      let ootb_fields = METADATA_PROVIDER_OOTB_FIELDS
        .get()
        .unwrap()
        .call_method(e, provider, ReturnType::Object, &[])?
        .l()?;
      let ootb_fields = ffi::jobject_list_to_fields(e, &ootb_fields)?;

      let custom_fields = METADATA_PROVIDER_CUSTOM_FIELDS
        .get()
        .unwrap()
        .call_method(e, provider, ReturnType::Object, &[])?
        .l()?;
      let custom_fields = ffi::jobject_list_to_fields(e, &custom_fields)?;

      Ok((custom_fields, ootb_fields))
    })
  }
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_createLogger(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  directory: JString<'_>,
  api_key: JString<'_>,
  session_strategy: JObject<'_>,
  metadata_provider: JObject<'_>,
  resource_utilization_target: JObject<'_>,
  session_replay_target: JObject<'_>,
  events_listener_target: JObject<'_>,
  application_id: JString<'_>,
  application_version: JString<'_>,
  model: JString<'_>,
  network: JObject<'_>,
  preferences: JObject<'_>,
  error_reporter: JObject<'_>,
) -> jlong {
  initialize_logging();

  bd_client_common::error::with_handle_unexpected_or(
    || {
      let sdk_directory = PathBuf::from(
        unsafe { env.get_string_unchecked(&directory) }?
          .to_string_lossy()
          .to_string(),
      );
      let network_manager = Box::new(Network {
        handle: new_global!(NetworkHandle, &mut env, network)?,
        active_streams: Arc::new(AtomicU32::new(0)),
      });

      let preferences = new_global!(PreferencesHandle, &mut env, preferences)?;
      let store = Arc::new(bd_key_value::Store::new(Box::new(preferences)));

      let session_strategy = Arc::new(new_global!(
        SessionStrategyConfigurationHandle,
        &mut env,
        session_strategy
      )?);
      let session_strategy = session_strategy.create(session_strategy.clone(), store.clone())?;

      let device = Arc::new(bd_device::Device::new(store.clone()));
      let static_metadata = Arc::new(Mobile {
        app_id: Some(unsafe { env.get_string_unchecked(&application_id) }?.into()),
        app_version: Some(unsafe { env.get_string_unchecked(&application_version) }?.into()),
        platform: Platform::Android,
        // TODO(mattklein123): Pass this from the platform layer when we want to support other OS.
        // Further, "os" as sent as a log tag is hard coded as "Android" so we have a casing
        // mismatch. We need to untangle all of this but we can do that when we send all fixed
        // fields as metadata and only use the fixed fields on logs for matching.
        os: "android".to_string(),
        device: device.clone(),
        model: unsafe { env.get_string_unchecked(&model) }?.into(),
      });

      let error_reporter = Arc::new(new_global!(ErrorReporterHandle, &mut env, error_reporter)?);
      let error_reporter = MetadataErrorReporter::new(
        error_reporter,
        Arc::new(platform_shared::error::SessionProvider::new(
          session_strategy.clone(),
        )),
        static_metadata.clone(),
      );

      let resource_utilization_target = Box::new(new_global!(
        ResourceUtilizationTargetHandler,
        &mut env,
        resource_utilization_target
      )?);

      let session_replay_target = Box::new(new_global!(
        SessionReplayTargetHandler,
        &mut env,
        session_replay_target
      )?);

      let events_listener_target = Box::new(new_global!(
        EventsListenerTargetHandler,
        &mut env,
        events_listener_target
      )?);

      // Errors emitted up until this point are not reported to bitdrift remote.
      // TODO(Augustyniak): Make it more obvious that as much work as possible should be done after
      // the error reporter is set up.
      UnexpectedErrorHandler::set_reporter(Arc::new(error_reporter));

      let logger = bd_logger::LoggerBuilder::new(bd_logger::InitParams {
        sdk_directory,
        api_key: unsafe { env.get_string_unchecked(&api_key) }?.into(),
        session_strategy,
        metadata_provider: Arc::new(new_global!(MetadataProvider, &mut env, metadata_provider)?),
        resource_utilization_target,
        session_replay_target,
        events_listener_target,
        device,
        store,
        network: network_manager,
        static_metadata,
      })
      .with_client_stats(true)
      .with_internal_logger(true)
      .build()
      .map(|(logger, _, future, _)| LoggerHolder::new(logger, future))?;

      Ok(logger.into_raw().into())
    },
    -1,
    "jni create logger",
  )
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_startLogger(
  _env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
) {
  let logger = unsafe { LoggerId::from_raw(logger_id) };
  logger.start();
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_destroyLogger(
  _env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
) {
  unsafe { LoggerHolder::destroy(logger_id) }
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_startNewSession(
  _env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: LoggerId<'_>,
) {
  logger_id.start_new_session();
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_getSessionId<'a>(
  env: JNIEnv<'a>,
  _class: JClass<'_>,
  logger_id: LoggerId<'_>,
) -> JString<'a> {
  bd_client_common::error::with_handle_unexpected_or(
    || Ok(env.new_string(logger_id.session_id())?),
    JObject::null().into(),
    "jni get_session_id",
  )
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_getDeviceId<'a>(
  env: JNIEnv<'a>,
  _class: JClass<'_>,
  logger_id: LoggerId<'_>,
) -> JString<'a> {
  bd_client_common::error::with_handle_unexpected_or(
    || Ok(env.new_string(logger_id.device_id())?),
    JObject::null().into(),
    "jni get_device_id",
  )
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_addLogField(
  env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  key: JString<'_>,
  value: JString<'_>,
) {
  bd_client_common::error::with_handle_unexpected(
    || -> anyhow::Result<()> {
      let key = unsafe { env.get_string_unchecked(&key) }?
        .to_string_lossy()
        .to_string();
      let value = unsafe { env.get_string_unchecked(&value) }?
        .to_string_lossy()
        .to_string();

      let logger = unsafe { LoggerId::from_raw(logger_id) };
      logger.add_log_field(key, value.into());

      Ok(())
    },
    "jni add log field",
  );
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_removeLogField(
  env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  key: JString<'_>,
) {
  bd_client_common::error::with_handle_unexpected(
    || -> anyhow::Result<()> {
      let key = unsafe { env.get_string_unchecked(&key) }?
        .to_string_lossy()
        .to_string();

      let logger = unsafe { LoggerId::from_raw(logger_id) };
      logger.remove_log_field(&key);

      Ok(())
    },
    "jni add log field",
  );
}

#[no_mangle]
// Java types are always signed, but log level/type are both unsigned.
#[allow(clippy::cast_sign_loss)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeLog(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  log_type: jint,
  log_level: jint,
  log: JString<'_>,
  fields: JObject<'_>,
  matching_fields: JObject<'_>,
  override_expected_previous_process_session_id: JString<'_>,
  override_occurred_at_unix_milliseconds: jlong,
  blocking: jboolean,
) {
  // This should only fail if the JVM is in a bad state.
  bd_client_common::error::with_handle_unexpected(
    || -> anyhow::Result<()> {
      let fields = ffi::jobject_map_to_fields(&mut env, &fields, LogFieldKind::Ootb)?;
      let matching_fields =
        ffi::jobject_map_to_fields(&mut env, &matching_fields, LogFieldKind::Ootb)?;

      let attributes_overrides = if override_expected_previous_process_session_id.is_null()
        && override_occurred_at_unix_milliseconds <= 0
      {
        None
      } else if override_expected_previous_process_session_id.is_null()
        && override_occurred_at_unix_milliseconds > 0
      {
        Some(LogAttributesOverrides::OccurredAt(
          unix_milliseconds_to_date(override_occurred_at_unix_milliseconds)?,
        ))
      } else {
        let expected_previous_process_session_id =
          unsafe { env.get_string_unchecked(&override_expected_previous_process_session_id) }?
            .to_string_lossy()
            .to_string();

        Some(LogAttributesOverrides::PreviousRunSessionID(
          expected_previous_process_session_id,
          unix_milliseconds_to_date(override_occurred_at_unix_milliseconds)?,
        ))
      };

      let logger = unsafe { LoggerId::from_raw(logger_id) };
      logger.log(
        log_level as u32,
        LogType(log_type as u32),
        unsafe { env.get_string_unchecked(&log) }?
          .to_string_lossy()
          .to_string()
          .into(),
        fields,
        matching_fields,
        attributes_overrides,
        blocking == JNI_TRUE,
      );

      Ok(())
    },
    "jni write log",
  );
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeSessionReplayScreenLog(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  fields: JObject<'_>,
  duration_s: jdouble,
) {
  bd_client_common::error::with_handle_unexpected(
    || -> anyhow::Result<()> {
      let fields = ffi::jobject_map_to_fields(&mut env, &fields, LogFieldKind::Ootb)?;

      let logger = unsafe { LoggerId::from_raw(logger_id) };
      logger.log_session_replay_screen(fields, Duration::seconds_f64(duration_s));

      Ok(())
    },
    "jni write replay screen log",
  );
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeSessionReplayScreenshotLog(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  fields: JObject<'_>,
  duration_s: jdouble,
) {
  bd_client_common::error::with_handle_unexpected(
    || -> anyhow::Result<()> {
      let fields = ffi::jobject_map_to_fields(&mut env, &fields, LogFieldKind::Ootb)?;

      let logger = unsafe { LoggerId::from_raw(logger_id) };
      logger.log_session_replay_screenshot(fields, Duration::seconds_f64(duration_s));

      Ok(())
    },
    "jni write replay screenshot log",
  );
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeResourceUtilizationLog(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  fields: JObject<'_>,
  duration_s: jdouble,
) {
  bd_client_common::error::with_handle_unexpected(
    || -> anyhow::Result<()> {
      let fields = ffi::jobject_map_to_fields(&mut env, &fields, LogFieldKind::Ootb)?;

      let logger = unsafe { LoggerId::from_raw(logger_id) };
      logger.log_resource_utilization(fields, Duration::seconds_f64(duration_s));

      Ok(())
    },
    "jni write resource utilization log",
  );
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeSDKStartLog(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  fields: JObject<'_>,
  duration_s: jdouble,
) {
  bd_client_common::error::with_handle_unexpected(
    || -> anyhow::Result<()> {
      let fields = ffi::jobject_map_to_fields(&mut env, &fields, LogFieldKind::Ootb)?;

      let logger = unsafe { LoggerId::from_raw(logger_id) };
      logger.log_sdk_start(fields, Duration::seconds_f64(duration_s));

      Ok(())
    },
    "jni write resource utilization log",
  );
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_shouldWriteAppUpdateLog(
  env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  app_version: JString<'_>,
  app_version_code: jlong,
) -> bool {
  with_handle_unexpected_or(
    || {
      let app_version = unsafe { env.get_string_unchecked(&app_version)? }
        .to_string_lossy()
        .to_string();

      let logger = unsafe { LoggerId::from_raw(logger_id) };
      Ok(logger.should_log_app_update(
        app_version,
        bd_logger::AppVersionExtra::AppVersionCode(app_version_code),
      ))
    },
    false,
    "swift should log app update",
  )
}

// Java types are always signed, but app_install_size_bytes is unsigned.
#[no_mangle]
#[allow(clippy::cast_sign_loss)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeAppUpdateLog(
  env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  app_version: JString<'_>,
  app_version_code: jlong,
  app_install_size_bytes: jlong,
  duration_s: f64,
) {
  bd_client_common::error::with_handle_unexpected(
    || -> anyhow::Result<()> {
      let app_version = unsafe { env.get_string_unchecked(&app_version)? }
        .to_string_lossy()
        .to_string();

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
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeAppLaunchTTILog(
  _env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  duration_s: f64,
) {
  bd_client_common::error::with_handle_unexpected(
    || -> anyhow::Result<()> {
      let logger = unsafe { LoggerId::from_raw(logger_id) };
      logger.log_app_launch_tti(Duration::seconds_f64(duration_s));

      Ok(())
    },
    "jni write app launch TTI log",
  );
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_writeScreenViewLog(
  env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  screen_name: JString<'_>,
) {
  bd_client_common::error::with_handle_unexpected(
    || -> anyhow::Result<()> {
      let screen_name = unsafe { env.get_string_unchecked(&screen_name)? }
        .to_string_lossy()
        .to_string();
      let logger = unsafe { LoggerId::from_raw(logger_id) };
      logger.log_screen_view(screen_name);

      Ok(())
    },
    "jni write screen view log",
  );
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_flush(
  _env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  blocking: jboolean,
) {
  bd_client_common::error::with_handle_unexpected(
    || -> anyhow::Result<()> {
      let logger = unsafe { LoggerId::from_raw(logger_id) };
      logger.flush_state(blocking == JNI_TRUE);

      Ok(())
    },
    "jni flush",
  );
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_debugDebug(
  env: JNIEnv<'_>,
  _class: JClass<'_>,
  message: JString<'_>,
) {
  if let Ok(message) = unsafe { env.get_string_unchecked(&message) } {
    log::debug!("jni log: {}", message.to_string_lossy());
  }
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_debugError(
  env: JNIEnv<'_>,
  _class: JClass<'_>,
  message: JString<'_>,
) {
  if let Ok(message) = unsafe { env.get_string_unchecked(&message) } {
    log::error!("jni log: {}", message.to_string_lossy());
  }
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_reportError(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  message: JString<'_>,
  stack_trace_provider: JObject<'_>,
) {
  if let Ok(message) = unsafe { env.get_string_unchecked(&message) } {
    bd_client_common::error::handle_unexpected_error_with_details(
      anyhow!(message.to_string_lossy().to_string()),
      "jni reported",
      || {
        exception_stacktrace(&mut env, &stack_trace_provider).unwrap_or_else(|_| {
          let msg = crate::executor::check_exception(&mut env).unwrap();
          log::warn!("failed to extract stacktrace: {msg:?}");
          None
        })
      },
    );
  }
}

fn exception_stacktrace(
  env: &mut JNIEnv<'_>,
  stack_trace_provider: &JObject<'_>,
) -> anyhow::Result<Option<String>> {
  let stacktrace = &STACK_TRACE_PROVIDER_INVOKE
    .get()
    .unwrap()
    .call_method(env, stack_trace_provider, ReturnType::Object, &[])?
    .l()?
    .into();

  let stacktrace_str = unsafe { env.get_string_unchecked(stacktrace) }?;
  Ok(Some(stacktrace_str.to_string_lossy().to_string()))
}

#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_Jni_isRuntimeEnabled(
  env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  feature: JString<'_>,
  default_value: jboolean,
) -> jboolean {
  bd_client_common::error::with_handle_unexpected_or(
    || {
      // We default the feature to default_value to so that we don't require sending anything over
      // the wire in order to enable a feature (the default), leaving this as a kill switch in
      // case we need to override what the user configured.
      let logger = unsafe { LoggerId::from_raw(logger_id) };

      Ok(logger.runtime_snapshot().get_bool(
        unsafe { env.get_string_unchecked(&feature) }?.to_str()?,
        default_value == JNI_TRUE,
      ))
    },
    default_value == JNI_TRUE,
    "jni isFeatureEnabled",
  )
  .into()
}

#[no_mangle]
// Java/Kotlin types are always signed, but get_integer is unsigned.
#[allow(clippy::cast_sign_loss)]
pub extern "system" fn Java_io_bitdrift_capture_Jni_runtimeValue(
  env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger_id: jlong,
  variable_name: JString<'_>,
  default_value: jint,
) -> jint {
  bd_client_common::error::with_handle_unexpected_or(
    || {
      let logger = unsafe { LoggerId::from_raw(logger_id) };
      let binding = unsafe { env.get_string_unchecked(&variable_name) }?;
      let variable_name = binding.to_str()?;
      let integer_value = logger
        .runtime_snapshot()
        .get_integer(variable_name, default_value as u32);

      Ok(jint::try_from(integer_value).map_or(default_value, |value| value))
    },
    default_value,
    "jni runtimeValue",
  )
}

fn unix_milliseconds_to_date(millis_since_utc_epoch: i64) -> anyhow::Result<OffsetDateTime> {
  let seconds = millis_since_utc_epoch / 1000;
  let nano = (millis_since_utc_epoch % 1000) * 10_i64.pow(6);

  Ok(time::OffsetDateTime::from_unix_timestamp(seconds)? + Duration::nanoseconds(nano))
}
