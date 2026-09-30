// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::ffi::{make_nsstring, nsstring_into_string};
use anyhow::{Result, anyhow};
use bd_artifact_upload::UploadSource;
use bd_logger::{CommandAttachment, CommandInvocation, CommandResult, RegisteredCommandHandler};
use bd_proto::protos::logging::payload::Data;
use bd_proto::protos::logging::payload::data::Data_type;
use objc::rc::{StrongPtr, autoreleasepool};
use objc::runtime::Object;
use parking_lot::Mutex;
use std::collections::HashMap;
use std::hash::BuildHasher;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, LazyLock};
use tokio::sync::oneshot;

#[cfg(test)]
#[path = "./live_commands_test.rs"]
mod live_commands_test;

const ARGUMENT_NAME_KEY: &str = "name";
const ARGUMENT_TYPE_KEY: &str = "type";
const ARGUMENT_VALUE_KEY: &str = "value";
const ATTACHMENT_FILENAME_FIELD: &str = "filename";

const ARGUMENT_TYPE_STRING: usize = 0;
const ARGUMENT_TYPE_BINARY: usize = 1;
const ARGUMENT_TYPE_UINT64: usize = 2;
const ARGUMENT_TYPE_DOUBLE: usize = 3;
const ARGUMENT_TYPE_INT64: usize = 4;
const ARGUMENT_TYPE_BOOL: usize = 5;

static NEXT_REQUEST_ID: AtomicU64 = AtomicU64::new(1);
static COMPLETIONS: LazyLock<Mutex<HashMap<u64, oneshot::Sender<CommandResult>>>> =
  LazyLock::new(Mutex::default);

#[allow(clippy::non_send_fields_in_send_ty)]
pub struct Target {
  swift_object: StrongPtr,
}

unsafe impl Send for Target {}
unsafe impl Sync for Target {}

impl Target {
  #[allow(clippy::not_unsafe_ptr_arg_deref)]
  pub fn new(swift_object: *mut Object) -> Self {
    Self {
      swift_object: unsafe { StrongPtr::retain(swift_object) },
    }
  }
}

#[async_trait::async_trait]
impl RegisteredCommandHandler for Target {
  async fn execute(&self, invocation: CommandInvocation) -> CommandResult {
    let receiver = {
      let arguments = match make_arguments(&invocation.arguments) {
        Ok(arguments) => arguments,
        Err(error) => return failed_result(error.to_string(), &HashMap::new()),
      };

      let request_id = NEXT_REQUEST_ID.fetch_add(1, Ordering::Relaxed);
      let (sender, receiver) = oneshot::channel();
      COMPLETIONS.lock().insert(request_id, sender);

      autoreleasepool(|| {
        let Ok(command_key) = make_nsstring(&invocation.registered_command_id) else {
          let sender = COMPLETIONS.lock().remove(&request_id);
          drop(sender);
          return;
        };
        unsafe {
          let (): () = msg_send![*self.swift_object,
            executeCommand: *command_key
            arguments: *arguments
            requestID: request_id
          ];
        }
      });
      receiver
    };

    receiver.await.unwrap_or_else(|_| {
      failed_result(
        "The command handler was released before completing".to_string(),
        &HashMap::new(),
      )
    })
  }
}

pub fn register(logger: &bd_logger::LoggerHandle, key: String, target: *mut Object) {
  logger.register_command_handler(key, Arc::new(Target::new(target)));
}

pub fn unregister(logger: &bd_logger::LoggerHandle, key: &str) {
  let _ = logger.unregister_command_handler(key);
}

pub fn complete<S: BuildHasher>(
  request_id: u64,
  succeeded: bool,
  context: &HashMap<String, String, S>,
  attachment: Option<Vec<u8>>,
  attachment_mime_type: Option<String>,
  attachment_filename: Option<String>,
  error: Option<String>,
) {
  let sender = COMPLETIONS.lock().remove(&request_id);
  let Some(sender) = sender else {
    log::debug!("ignoring completion for unknown live command {request_id}");
    return;
  };

  let result = if succeeded {
    let attachment = attachment
      .zip(attachment_mime_type)
      .map(|(bytes, type_id)| {
        let mut state = context_to_fields(context);
        if let Some(filename) = attachment_filename {
          state.insert(ATTACHMENT_FILENAME_FIELD.into(), filename.into());
        }
        CommandAttachment {
          source: UploadSource::Bytes(bytes),
          type_id,
          state,
        }
      });
    CommandResult::Completed {
      fields: context_to_fields(context),
      attachment,
    }
  } else {
    failed_result(
      error.unwrap_or_else(|| "Command failed".to_string()),
      context,
    )
  };

  let _ = sender.send(result);
}

fn failed_result<S: BuildHasher>(
  error: String,
  context: &HashMap<String, String, S>,
) -> CommandResult {
  CommandResult::Failed {
    error,
    fields: context_to_fields(context),
  }
}

fn context_to_fields<S: BuildHasher>(context: &HashMap<String, String, S>) -> bd_logger::LogFields {
  context
    .iter()
    .map(|(key, value)| (key.clone().into(), value.clone().into()))
    .collect()
}

fn make_arguments(arguments: &HashMap<String, Data>) -> Result<StrongPtr> {
  autoreleasepool(|| {
    let arguments_array = unsafe { StrongPtr::new(msg_send![class!(NSMutableArray), new]) };
    for (name, data) in arguments {
      let Some(data_type) = data.data_type.as_ref() else {
        return Err(anyhow!("command argument {name} has no value"));
      };
      let (argument_type, value) = make_argument_value(data_type)?;
      let argument = unsafe { StrongPtr::new(msg_send![class!(NSMutableDictionary), new]) };
      let name_key = make_nsstring(ARGUMENT_NAME_KEY)?;
      let type_key = make_nsstring(ARGUMENT_TYPE_KEY)?;
      let value_key = make_nsstring(ARGUMENT_VALUE_KEY)?;
      let name = make_nsstring(name)?;
      let argument_type = number_with_unsigned_integer(argument_type);
      unsafe {
        let (): () = msg_send![*argument, setObject:*name forKey:*name_key];
        let (): () = msg_send![*argument, setObject:*argument_type forKey:*type_key];
        let (): () = msg_send![*argument, setObject:*value forKey:*value_key];
        let (): () = msg_send![*arguments_array, addObject:*argument];
      }
    }
    Ok(arguments_array)
  })
}

fn make_argument_value(data_type: &Data_type) -> Result<(usize, StrongPtr)> {
  match data_type {
    Data_type::StringData(value) => Ok((ARGUMENT_TYPE_STRING, make_nsstring(value)?)),
    Data_type::BinaryData(value) => Ok((ARGUMENT_TYPE_BINARY, data_from_bytes(&value.payload))),
    Data_type::IntData(value) => Ok((ARGUMENT_TYPE_UINT64, number_with_unsigned_long_long(*value))),
    Data_type::DoubleData(value) => Ok((ARGUMENT_TYPE_DOUBLE, number_with_double(*value))),
    Data_type::SintData(value) => Ok((ARGUMENT_TYPE_INT64, number_with_long_long(*value))),
    Data_type::BoolData(value) => Ok((ARGUMENT_TYPE_BOOL, number_with_bool(*value))),
    Data_type::MapData(_) | Data_type::ArrayData(_) => {
      Err(anyhow!("nested command argument values are unsupported"))
    },
  }
}

fn data_from_bytes(bytes: &[u8]) -> StrongPtr {
  unsafe {
    StrongPtr::retain(msg_send![class!(NSData), dataWithBytes: bytes.as_ptr() length: bytes.len()])
  }
}

fn number_with_unsigned_integer(value: usize) -> StrongPtr {
  unsafe { StrongPtr::retain(msg_send![class!(NSNumber), numberWithUnsignedInteger: value]) }
}

fn number_with_unsigned_long_long(value: u64) -> StrongPtr {
  unsafe { StrongPtr::retain(msg_send![class!(NSNumber), numberWithUnsignedLongLong: value]) }
}

fn number_with_long_long(value: i64) -> StrongPtr {
  unsafe { StrongPtr::retain(msg_send![class!(NSNumber), numberWithLongLong: value]) }
}

fn number_with_double(value: f64) -> StrongPtr {
  unsafe { StrongPtr::retain(msg_send![class!(NSNumber), numberWithDouble: value]) }
}

fn number_with_bool(value: bool) -> StrongPtr {
  unsafe { StrongPtr::retain(msg_send![class!(NSNumber), numberWithBool: value]) }
}

/// Converts an Objective-C dictionary whose keys and values are `NSString` instances.
///
/// # Safety
/// `dictionary` must point to a live `NSDictionary<NSString *, NSString *>` for this call.
pub unsafe fn string_dictionary_from_objc(
  dictionary: *const Object,
) -> Result<HashMap<String, String>> {
  let count: usize = unsafe { msg_send![dictionary, count] };
  let keys: *const Object = unsafe { msg_send![dictionary, allKeys] };
  let mut result = HashMap::with_capacity(count);
  for index in 0 .. count {
    let key: *const Object = unsafe { msg_send![keys, objectAtIndex: index] };
    let value: *const Object = unsafe { msg_send![dictionary, objectForKey: key] };
    result.insert(unsafe { nsstring_into_string(key) }?, unsafe {
      nsstring_into_string(value)
    }?);
  }
  Ok(result)
}
