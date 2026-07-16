// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::previous_nsexception;
use crate::previous::{
  NSExceptionCallStack,
  NSExceptionCrashInfo,
  NSExceptionStackFrame,
  PreviousCrashDetails,
  PreviousCrashState,
};
use crate::schema;

#[test]
fn previous_nsexception_returns_exception_details() {
  let mut return_addresses = [0; schema::MAX_NS_EXCEPTION_CALL_STACK_FRAMES];
  return_addresses[.. 2].copy_from_slice(&[0x1234, 0x5678]);
  let state = PreviousCrashState {
    did_crash: true,
    details: PreviousCrashDetails::NSException(Box::new(NSExceptionCrashInfo {
      name: [0; schema::NS_EXCEPTION_NAME_CAPACITY],
      reason: [0; schema::NS_EXCEPTION_REASON_CAPACITY],
      call_stack: NSExceptionCallStack {
        frame_count: 2,
        return_addresses,
        frames: {
          let mut frames = std::array::from_fn(|_| NSExceptionStackFrame::default());
          frames[0].return_address = 0x1234;
          frames[0].image_load_address = 0x1000;
          frames[0].binary_name[.. 6].copy_from_slice(b"MyApp\0");
          frames[0].image_id[.. schema::NS_EXCEPTION_IMAGE_ID_CAPACITY]
            .copy_from_slice(b"BD9C11B4-BF87-3F60-AEA0-0141BD7F8AC0\0");
          frames[1].return_address = 0x5678;
          frames
        },
      },
    })),
    ..PreviousCrashState::default()
  };

  let exception = previous_nsexception(&state).unwrap();
  assert_eq!(2, exception.call_stack.frame_count);
  assert_eq!(
    &[0x1234, 0x5678],
    &exception.call_stack.return_addresses[.. 2]
  );
  assert_eq!(0x1000, exception.call_stack.frames[0].image_load_address);
  assert_eq!(
    &exception.call_stack.frames[0].binary_name[.. 6],
    b"MyApp\0"
  );
}

#[test]
fn previous_nsexception_returns_none_for_non_exception_state() {
  assert!(previous_nsexception(&PreviousCrashState::default()).is_none());
}
