// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::ffi::{
  append_array_value,
  make_nsdata,
  make_nsstring,
  nsstring_into_string,
  set_dictionary_value,
};
use anyhow::{Result, anyhow};
use bd_artifact_upload::UploadSource;
use bd_logger::{
  CommandAttachment,
  CommandError,
  CommandInvocation,
  CommandResult,
  CommandResultGuard,
  RegisteredCommandHandler,
};
use bd_proto::protos::logging::payload::Data;
use bd_proto::protos::logging::payload::data::Data_type;
use objc::rc::{StrongPtr, autoreleasepool};
use objc::runtime::Object;
use parking_lot::Mutex;
use std::collections::HashMap;
use std::hash::BuildHasher;
use std::sync::LazyLock;
use std::sync::atomic::{AtomicU64, Ordering};
use tokio::sync::oneshot;

#[cfg(test)]
#[path = "./commands_test.rs"]
pub(crate) mod commands_test;

const ARGUMENT_NAME_KEY: &str = "name";
const ARGUMENT_TYPE_KEY: &str = "type";
const ARGUMENT_VALUE_KEY: &str = "value";

const ARGUMENT_TYPE_STRING: usize = 0;
const ARGUMENT_TYPE_BINARY: usize = 1;
const ARGUMENT_TYPE_UINT64: usize = 2;
const ARGUMENT_TYPE_DOUBLE: usize = 3;
const ARGUMENT_TYPE_INT64: usize = 4;
const ARGUMENT_TYPE_BOOL: usize = 5;

static NEXT_REQUEST_ID: AtomicU64 = AtomicU64::new(1);
static COMPLETIONS: LazyLock<Mutex<HashMap<u64, oneshot::Sender<CommandResultGuard>>>> =
  LazyLock::new(Mutex::default);

#[allow(clippy::non_send_fields_in_send_ty)]
pub struct Target {
  swift_object: StrongPtr,
}

unsafe impl Send for Target {}
unsafe impl Sync for Target {}

//
// PendingRequest
//

struct PendingRequest<'a> {
  target: &'a Target,
  request_id: u64,
  armed: bool,
}

impl Drop for PendingRequest<'_> {
  fn drop(&mut self) {
    if !self.armed {
      return;
    }
    // Release the map lock before calling Swift, which may finish the task synchronously.
    let sender = COMPLETIONS.lock().remove(&self.request_id);
    drop(sender);
    log::debug!("cancelling Apple command request {}", self.request_id);
    autoreleasepool(|| unsafe {
      let (): () = msg_send![*self.target.swift_object, cancelCommand: self.request_id];
    });
  }
}

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
    let (mut pending_request, receiver) = {
      let arguments = match make_arguments(&invocation.arguments) {
        Ok(arguments) => arguments,
        Err(error) => {
          return failed_result(
            CommandError::InvalidArguments(error.to_string()),
            &HashMap::new(),
          );
        },
      };

      // Rust workers have no ambient pool. The retained key stays alive for dispatch after
      // draining.
      let command_key = match autoreleasepool(|| make_nsstring(&invocation.registered_command_id)) {
        Ok(command_key) => command_key,
        Err(error) => {
          return failed_result(
            CommandError::InvalidArguments(error.to_string()),
            &HashMap::new(),
          );
        },
      };
      let request_id = NEXT_REQUEST_ID.fetch_add(1, Ordering::Relaxed);
      let (sender, receiver) = oneshot::channel();
      COMPLETIONS.lock().insert(request_id, sender);
      let pending_request = PendingRequest {
        target: self,
        request_id,
        armed: true,
      };

      autoreleasepool(|| unsafe {
        let (): () = msg_send![*self.swift_object,
          executeCommand: *command_key
          arguments: *arguments
          requestID: request_id
        ];
      });
      (pending_request, receiver)
    };

    receiver.await.map_or_else(
      |_| {
        failed_result(
          CommandError::Other("The command handler was released before completing".into()),
          &HashMap::new(),
        )
      },
      |result| {
        pending_request.armed = false;
        result.into_inner()
      },
    )
  }
}

pub fn take_completion(request_id: u64) -> Option<oneshot::Sender<CommandResultGuard>> {
  COMPLETIONS.lock().remove(&request_id)
}

pub fn complete_taken(
  request_id: u64,
  sender: oneshot::Sender<CommandResultGuard>,
  result: CommandResult,
) {
  if sender.send(CommandResultGuard::new(result)).is_err() {
    log::debug!("command {request_id} completed after its caller stopped waiting");
  }
}

pub fn result_from_platform_completion<S: BuildHasher>(
  succeeded: bool,
  context: &HashMap<String, String, S>,
  attachment: Option<UploadSource>,
  attachment_mime_type: Option<String>,
  error_code: Option<&str>,
  error_message: Option<String>,
) -> CommandResult {
  if succeeded {
    let attachment = attachment
      .zip(attachment_mime_type)
      .map(|(source, content_type)| CommandAttachment {
        source,
        content_type: Some(content_type),
      });
    CommandResult::Completed {
      fields: context_to_fields(context),
      attachment,
    }
  } else {
    failed_result(
      platform_shared::command_error::from_command_error_wire(error_code, error_message),
      context,
    )
  }
}

pub fn failed_result<S: BuildHasher>(
  error: CommandError,
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
      let argument = make_command_argument(name, argument_type, &value)?;
      append_array_value(&arguments_array, &argument);
    }
    Ok(arguments_array)
  })
}

/// Creates the Foundation representation consumed by `CommandsTarget`.
fn make_command_argument(name: &str, argument_type: usize, value: &StrongPtr) -> Result<StrongPtr> {
  let argument = unsafe { StrongPtr::new(msg_send![class!(NSMutableDictionary), new]) };
  let argument_name = make_nsstring(name)?;
  let argument_type = number_with_unsigned_integer(argument_type);
  set_dictionary_value(&argument, ARGUMENT_NAME_KEY, &argument_name)?;
  set_dictionary_value(&argument, ARGUMENT_TYPE_KEY, &argument_type)?;
  set_dictionary_value(&argument, ARGUMENT_VALUE_KEY, value)?;
  Ok(argument)
}

fn make_argument_value(data_type: &Data_type) -> Result<(usize, StrongPtr)> {
  match data_type {
    Data_type::StringData(value) => Ok((ARGUMENT_TYPE_STRING, make_nsstring(value)?)),
    Data_type::BinaryData(value) => Ok((ARGUMENT_TYPE_BINARY, make_nsdata(&value.payload))),
    Data_type::IntData(value) => Ok((ARGUMENT_TYPE_UINT64, number_with_unsigned_long_long(*value))),
    Data_type::DoubleData(value) => Ok((ARGUMENT_TYPE_DOUBLE, number_with_double(*value))),
    Data_type::SintData(value) => Ok((ARGUMENT_TYPE_INT64, number_with_long_long(*value))),
    Data_type::BoolData(value) => Ok((ARGUMENT_TYPE_BOOL, number_with_bool(*value))),
    Data_type::MapData(_) | Data_type::ArrayData(_) => {
      Err(anyhow!("nested command argument values are unsupported"))
    },
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
