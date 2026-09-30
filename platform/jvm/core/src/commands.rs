// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::define_object_wrapper;
use crate::jni::{CachedMethod, initialize_method_handle};
use bd_client_common::error::InvariantError;
use bd_logger::{
  CommandAttachment,
  CommandInvocation,
  CommandResult,
  LogFields,
  RegisteredCommandHandler,
};
use bd_proto::protos::logging::payload::Data;
use bd_proto::protos::logging::payload::data::Data_type;
use jni::JNIEnv;
use jni::objects::{JObject, JObjectArray, JValue};
use jni::signature::{Primitive, ReturnType};
use jni::sys::jvalue;
use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{LazyLock, Mutex, MutexGuard, OnceLock, PoisonError};
use tokio::sync::oneshot;

static COMMAND_DISPATCHER_DISPATCH: OnceLock<CachedMethod> = OnceLock::new();
static NEXT_INVOCATION_ID: AtomicU64 = AtomicU64::new(1);
static PENDING_INVOCATIONS: LazyLock<Mutex<HashMap<u64, oneshot::Sender<CommandResult>>>> =
  LazyLock::new(Mutex::default);

fn pending_invocations() -> MutexGuard<'static, HashMap<u64, oneshot::Sender<CommandResult>>> {
  PENDING_INVOCATIONS
    .lock()
    .unwrap_or_else(PoisonError::into_inner)
}

pub(crate) fn initialize(env: &mut JNIEnv<'_>) -> anyhow::Result<()> {
  initialize_method_handle(
    env,
    "io/bitdrift/capture/commands/ICommandDispatcher",
    "dispatch",
    "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/\
     String;)V",
    &COMMAND_DISPATCHER_DISPATCH,
  )
}

pub(crate) fn complete_invocation(invocation_id: u64, result: CommandResult) {
  let Some(sender) = pending_invocations().remove(&invocation_id) else {
    log::debug!("ignoring completion for unknown command invocation {invocation_id}");
    return;
  };
  if sender.send(result).is_err() {
    log::debug!("command invocation {invocation_id} completed after its caller stopped waiting");
  }
}

pub(crate) fn completed_result(
  fields: LogFields,
  attachment: Option<Vec<u8>>,
  content_type: Option<String>,
) -> CommandResult {
  CommandResult::Completed {
    fields,
    attachment: attachment.map(|bytes| CommandAttachment {
      source: bd_artifact_upload::UploadSource::Bytes(bytes),
      content_type,
      state: LogFields::default(),
    }),
  }
}

fn failed_result(error: impl Into<String>) -> CommandResult {
  CommandResult::Failed {
    error: error.into(),
    fields: LogFields::default(),
  }
}

fn argument_to_string(argument: Data) -> String {
  match argument.data_type {
    Some(Data_type::StringData(value)) => value,
    Some(Data_type::IntData(value)) => value.to_string(),
    Some(Data_type::SintData(value)) => value.to_string(),
    Some(Data_type::DoubleData(value)) => value.to_string(),
    Some(Data_type::BoolData(value)) => value.to_string(),
    Some(Data_type::BinaryData(value)) => String::from_utf8_lossy(&value.payload).into_owned(),
    Some(Data_type::MapData(_) | Data_type::ArrayData(_)) | None => String::new(),
  }
}

fn new_string_array<'a>(
  env: &mut JNIEnv<'a>,
  values: &[String],
) -> anyhow::Result<JObjectArray<'a>> {
  let array = env.new_object_array(
    values.len().try_into()?,
    "java/lang/String",
    JObject::null(),
  )?;
  for (index, value) in values.iter().enumerate() {
    let value = env.new_string(value)?;
    env.set_object_array_element(&array, index.try_into()?, value)?;
  }
  Ok(array)
}

//
// CommandDispatcherHandle
//

define_object_wrapper!(CommandDispatcherHandle);

unsafe impl Send for CommandDispatcherHandle {}
unsafe impl Sync for CommandDispatcherHandle {}

impl CommandDispatcherHandle {
  fn dispatch(&self, invocation_id: u64, invocation: CommandInvocation) -> anyhow::Result<()> {
    let (argument_names, argument_values): (Vec<_>, Vec<_>) = invocation
      .arguments
      .into_iter()
      .map(|(name, value)| (name, argument_to_string(value)))
      .unzip();

    self.execute(|env, dispatcher| {
      let key = env.new_string(&invocation.registered_command_id)?;
      let command_id = match invocation.command_id {
        Some(command_id) => JObject::from(env.new_string(command_id.to_string())?),
        None => JObject::null(),
      };
      let session_id = env.new_string(&invocation.session_id)?;
      let java_argument_names = new_string_array(env, &argument_names)?;
      let java_argument_values = new_string_array(env, &argument_values)?;

      COMMAND_DISPATCHER_DISPATCH
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(
          env,
          dispatcher,
          ReturnType::Primitive(Primitive::Void),
          &[
            jvalue {
              j: invocation_id.cast_signed(),
            },
            JValue::Object(&key).as_jni(),
            JValue::Object(&command_id).as_jni(),
            JValue::Object(&session_id).as_jni(),
            JValue::Object(&java_argument_names).as_jni(),
            JValue::Object(&java_argument_values).as_jni(),
          ],
        )
        .map(|_| ())
    })
  }
}

//
// JniCommandHandler
//

pub(crate) struct JniCommandHandler {
  dispatcher: CommandDispatcherHandle,
}

impl JniCommandHandler {
  pub(crate) fn new(env: &JNIEnv<'_>, dispatcher: JObject<'_>) -> jni::errors::Result<Self> {
    Ok(Self {
      dispatcher: CommandDispatcherHandle::new_global(env, dispatcher)?,
    })
  }
}

#[async_trait::async_trait]
impl RegisteredCommandHandler for JniCommandHandler {
  async fn execute(&self, invocation: CommandInvocation) -> CommandResult {
    let invocation_id = NEXT_INVOCATION_ID.fetch_add(1, Ordering::Relaxed);
    let (sender, receiver) = oneshot::channel();
    pending_invocations().insert(invocation_id, sender);

    if let Err(error) = self.dispatcher.dispatch(invocation_id, invocation) {
      pending_invocations().remove(&invocation_id);
      return failed_result(format!("failed to dispatch command: {error}"));
    }

    receiver
      .await
      .unwrap_or_else(|_| failed_result("command handler stopped without a result"))
  }
}
