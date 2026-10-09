// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./commands_test.rs"]
mod commands_test;

use crate::define_object_wrapper;
use crate::jni::{CachedMethod, initialize_method_handle};
use bd_client_common::error::InvariantError;
use bd_logger::{
  CommandAttachment,
  CommandError,
  CommandInvocation,
  CommandResult,
  LogFields,
  RegisteredCommandHandler,
};
use bd_proto::protos::logging::payload::Data;
use bd_proto::protos::logging::payload::data::Data_type;
use jni::JNIEnv;
use jni::objects::{JIntArray, JObject, JObjectArray, JValue};
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
    "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[I[Ljava/lang/\
     Object;)V",
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

/// Attachment state key for the filename; shared with the iOS bridge.
const ATTACHMENT_FILENAME_FIELD: &str = "filename";

/// A successful result's attachment as reported by Kotlin.
pub(crate) struct PlatformAttachment {
  pub bytes: Vec<u8>,
  pub filename: String,
  pub content_type: String,
}

pub(crate) fn completed_result(
  fields: LogFields,
  attachment: Option<PlatformAttachment>,
) -> CommandResult {
  CommandResult::Completed {
    fields,
    attachment: attachment.map(|attachment| CommandAttachment {
      source: bd_artifact_upload::UploadSource::Bytes(attachment.bytes),
      content_type: Some(attachment.content_type),
      state: [(ATTACHMENT_FILENAME_FIELD.into(), attachment.filename.into())].into(),
    }),
  }
}

fn failed_result(error: CommandError) -> CommandResult {
  CommandResult::Failed {
    error,
    fields: LogFields::default(),
  }
}

/// Maps a Kotlin `CommandErrorCode.wire` string to the typed `CommandError`. The strings are shared
/// with the iOS bridge.
pub(crate) fn command_error(code: &str, message: Option<String>) -> CommandError {
  match code {
    "command_unknown" => CommandError::CommandUnknown,
    "command_already_executing" => CommandError::AlreadyExecuting,
    "max_command_concurrency" => CommandError::MaxCommandConcurrency,
    "invalid_arguments" => {
      CommandError::InvalidArguments(message.unwrap_or_else(|| "invalid command arguments".into()))
    },
    "handler_failed" => {
      CommandError::HandlerFailed(message.unwrap_or_else(|| "command handler failed".into()))
    },
    "timeout" => CommandError::Timeout,
    other => {
      CommandError::Other(message.unwrap_or_else(|| format!("unknown command error code: {other}")))
    },
  }
}

/// A command argument as handed to the Kotlin dispatcher, with the type the backend sent. The type
/// codes are a JNI contract with Kotlin `CommandArgument.TYPE_*`; the values also match the iOS
/// bridge.
#[derive(Debug, Clone, PartialEq)]
pub(crate) enum PlatformArgument {
  Text(String),
  Binary(Vec<u8>),
  UnsignedInteger(u64),
  Decimal(f64),
  SignedInteger(i64),
  Bool(bool),
}

impl PlatformArgument {
  pub(crate) const fn type_code(&self) -> i32 {
    match self {
      Self::Text(_) => 0,
      Self::Binary(_) => 1,
      Self::UnsignedInteger(_) => 2,
      Self::Decimal(_) => 3,
      Self::SignedInteger(_) => 4,
      Self::Bool(_) => 5,
    }
  }

  fn to_jobject<'a>(&self, env: &mut JNIEnv<'a>) -> anyhow::Result<JObject<'a>> {
    Ok(match self {
      Self::Text(value) => env.new_string(value)?.into(),
      Self::Binary(value) => env.byte_array_from_slice(value)?.into(),
      Self::UnsignedInteger(value) => env.new_object(
        "java/lang/Long",
        "(J)V",
        &[JValue::Long(value.cast_signed())],
      )?,
      Self::SignedInteger(value) => {
        env.new_object("java/lang/Long", "(J)V", &[JValue::Long(*value)])?
      },
      Self::Decimal(value) => {
        env.new_object("java/lang/Double", "(D)V", &[JValue::Double(*value)])?
      },
      Self::Bool(value) => env.new_object(
        "java/lang/Boolean",
        "(Z)V",
        &[JValue::Bool((*value).into())],
      )?,
    })
  }
}

/// Converts one backend argument. Nested and empty values are rejected so that the whole invocation
/// fails with `InvalidArguments` before any handler runs.
pub(crate) fn platform_argument(name: &str, argument: Data) -> Result<PlatformArgument, String> {
  match argument.data_type {
    Some(Data_type::StringData(value)) => Ok(PlatformArgument::Text(value)),
    Some(Data_type::BinaryData(value)) => Ok(PlatformArgument::Binary(value.payload)),
    Some(Data_type::IntData(value)) => Ok(PlatformArgument::UnsignedInteger(value)),
    Some(Data_type::DoubleData(value)) => Ok(PlatformArgument::Decimal(value)),
    Some(Data_type::SintData(value)) => Ok(PlatformArgument::SignedInteger(value)),
    Some(Data_type::BoolData(value)) => Ok(PlatformArgument::Bool(value)),
    Some(Data_type::MapData(_) | Data_type::ArrayData(_)) => {
      Err(format!("argument {name} has an unsupported nested value"))
    },
    None => Err(format!("argument {name} has no value")),
  }
}

fn platform_arguments(
  arguments: HashMap<String, Data>,
) -> Result<Vec<(String, PlatformArgument)>, String> {
  arguments
    .into_iter()
    .map(|(name, data)| platform_argument(&name, data).map(|argument| (name, argument)))
    .collect()
}

fn new_argument_arrays<'a>(
  env: &mut JNIEnv<'a>,
  arguments: &[(String, PlatformArgument)],
) -> anyhow::Result<(JObjectArray<'a>, JIntArray<'a>, JObjectArray<'a>)> {
  let count: i32 = arguments.len().try_into()?;
  let names = env.new_object_array(count, "java/lang/String", JObject::null())?;
  let types = env.new_int_array(count)?;
  let values = env.new_object_array(count, "java/lang/Object", JObject::null())?;
  let type_codes: Vec<i32> = arguments
    .iter()
    .map(|(_, argument)| argument.type_code())
    .collect();
  env.set_int_array_region(&types, 0, &type_codes)?;
  for (index, (name, argument)) in arguments.iter().enumerate() {
    let index: i32 = index.try_into()?;
    let name = env.new_string(name)?;
    env.set_object_array_element(&names, index, name)?;
    let value = argument.to_jobject(env)?;
    env.set_object_array_element(&values, index, value)?;
  }
  Ok((names, types, values))
}

//
// CommandDispatcherHandle
//

define_object_wrapper!(CommandDispatcherHandle);

unsafe impl Send for CommandDispatcherHandle {}
unsafe impl Sync for CommandDispatcherHandle {}

impl CommandDispatcherHandle {
  fn dispatch(
    &self,
    invocation_id: u64,
    invocation: &CommandInvocation,
    arguments: &[(String, PlatformArgument)],
  ) -> anyhow::Result<()> {
    self.execute(|env, dispatcher| {
      let key = env.new_string(&invocation.registered_command_id)?;
      let command_id = match &invocation.command_id {
        Some(command_id) => JObject::from(env.new_string(command_id.to_string())?),
        None => JObject::null(),
      };
      let session_id = env.new_string(&invocation.session_id)?;
      let (java_argument_names, java_argument_types, java_argument_values) =
        new_argument_arrays(env, arguments)?;

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
            JValue::Object(&java_argument_types).as_jni(),
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
  async fn execute(&self, mut invocation: CommandInvocation) -> CommandResult {
    let arguments = match platform_arguments(std::mem::take(&mut invocation.arguments)) {
      Ok(arguments) => arguments,
      Err(error) => return failed_result(CommandError::InvalidArguments(error)),
    };

    let invocation_id = NEXT_INVOCATION_ID.fetch_add(1, Ordering::Relaxed);
    let (sender, receiver) = oneshot::channel();
    pending_invocations().insert(invocation_id, sender);

    if let Err(error) = self
      .dispatcher
      .dispatch(invocation_id, &invocation, &arguments)
    {
      pending_invocations().remove(&invocation_id);
      return failed_result(CommandError::Other(format!(
        "failed to dispatch command: {error}"
      )));
    }

    receiver.await.unwrap_or_else(|_| {
      failed_result(CommandError::Other(
        "command handler stopped without a result".into(),
      ))
    })
  }
}
