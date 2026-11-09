// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use crate::{Snapshot, ThreadStatus};
use bd_unwind::aarch64::{FrameKind, StopReason};
use bd_unwind::image::ImageError;
use std::io::{self, Write};

// Legacy Firefox Profiler tables are imported as stack samples by Perfetto:
// https://perfetto.dev/docs/getting-started/other-formats
pub(super) fn write(snapshot: &Snapshot, output: &mut impl Write) -> io::Result<()> {
  output.write_all(
    br#"{"meta":{"version":24,"interval":1,"startTime":0,
      "product":"bitdrift stack snapshot","stackwalk":1},"libs":[],"threads":["#,
  )?;
  for (thread_index, thread) in snapshot.threads.iter().enumerate() {
    if thread_index != 0 {
      output.write_all(b",")?;
    }
    write!(
      output,
      r#"{{"pid":{},"tid":{},"name":"#,
      snapshot.pid,
      thread_index + 1
    )?;
    if thread.name.is_empty() {
      string(output, &format!("Thread {}", thread.native_id))?;
    } else {
      string(output, &thread.name)?;
    }
    write!(
      output,
      r#", "bitdrift":{{"nativeThreadId":"{}","status":"{}",
      "stackTruncated":{},"copiedStackBytes":{},"suspensionNs":{},"frames":["#,
      thread.native_id,
      status(thread.status),
      thread.stack_truncated,
      thread.copied_stack_bytes,
      thread.suspension_ns
    )?;
    for (index, frame) in thread.frames.iter().enumerate() {
      if index != 0 {
        output.write_all(b",")?;
      }
      let kind = match frame.kind {
        FrameKind::ProgramCounter => "pc",
        FrameKind::ReturnAddress => "return",
      };
      write!(
        output,
        r#"{{"address":"0x{:x}","kind":"{kind}"}}"#,
        frame.address
      )?;
    }
    output.write_all(br#"]},"stringTable":["#)?;
    // Firefox stacks run root-to-leaf, opposite to the unwinder's PC-first output.
    for (index, frame) in thread.frames.iter().rev().enumerate() {
      if index != 0 {
        output.write_all(b",")?;
      }
      write!(output, r#""0x{:x}""#, frame.address)?;
    }
    output.write_all(br#"],"frameTable":{"schema":{"location":0},"data":["#)?;
    for index in 0 .. thread.frames.len() {
      if index != 0 {
        output.write_all(b",")?;
      }
      write!(output, "[{index}]")?;
    }
    output.write_all(br#"]},"stackTable":{"schema":{"prefix":0,"frame":1},"data":["#)?;
    for index in 0 .. thread.frames.len() {
      if index == 0 {
        output.write_all(b"[null,0]")?;
      } else {
        write!(output, ",[{},{}]", index - 1, index)?;
      }
    }
    output.write_all(br#"]},"samples":{"schema":{"stack":0,"time":1},"data":["#)?;
    if let Some(last) = thread.frames.len().checked_sub(1) {
      // Render nanoseconds as milliseconds without rounding through a floating-point cast.
      write!(
        output,
        "[{last},{}.{:06}]",
        thread.timestamp_ns / 1_000_000,
        thread.timestamp_ns % 1_000_000
      )?;
    }
    output.write_all(b"]}}")?;
  }
  let summary = snapshot.summary();
  write!(
    output,
    r#"],"bitdrift":{{"version":1,"capture":"snapshot","capturedThreads":{},
    "failedThreads":{},"truncatedThreads":{},"skippedThreads":{},"images":["#,
    summary.captured_threads,
    summary.failed_threads,
    summary.truncated_threads,
    summary.skipped_threads
  )?;
  for (index, image) in snapshot.images.iter().enumerate() {
    if index != 0 {
      output.write_all(b",")?;
    }
    write!(
      output,
      r#"{{"start":"0x{:x}","size":{},"uuid":"#,
      image.text.start(),
      image.text.size()
    )?;
    if let Some(uuid) = image.uuid {
      output.write_all(b"\"")?;
      for byte in uuid {
        write!(output, "{byte:02x}")?;
      }
      output.write_all(b"\"")?;
    } else {
      output.write_all(b"null")?;
    }
    output.write_all(b"}")?;
  }
  let rejected: usize = snapshot.image_errors.values().sum();
  write!(
    output,
    r#"],"imageSurvey":{{"enumerated":{},"accepted":{},"rejected":{},"errors":{{"#,
    snapshot.images.len() + rejected,
    snapshot.images.len(),
    rejected
  )?;
  for (index, (error, count)) in snapshot.image_errors.iter().enumerate() {
    if index != 0 {
      output.write_all(b",")?;
    }
    write!(output, r#""{}":{count}"#, image_error(*error))?;
  }
  output.write_all(b"}}}}")
}

const fn image_error(error: ImageError) -> &'static str {
  match error {
    ImageError::UnreadableMemory => "unreadable_memory",
    ImageError::InvalidHeader => "invalid_header",
    ImageError::UnsupportedArchitecture => "unsupported_architecture",
    ImageError::InvalidLoadCommand => "invalid_load_command",
    ImageError::NonzeroTextFileOffset {
      in_shared_cache: true,
    } => "nonzero_text_file_offset_in_shared_cache",
    ImageError::NonzeroTextFileOffset {
      in_shared_cache: false,
    } => "nonzero_text_file_offset",
    ImageError::InvalidSection => "invalid_section",
    ImageError::MissingTextSegment => "missing_text_segment",
    ImageError::AddressOverflow => "address_overflow",
  }
}

fn string(output: &mut impl Write, value: &str) -> io::Result<()> {
  output.write_all(b"\"")?;
  for character in value.chars() {
    match character {
      '"' => output.write_all(br#"\""#)?,
      '\\' => output.write_all(br"\\")?,
      '\n' => output.write_all(br"\n")?,
      '\r' => output.write_all(br"\r")?,
      '\t' => output.write_all(br"\t")?,
      c if c <= '\u{1f}' => write!(output, "\\u{:04x}", u32::from(c))?,
      c => output.write_all(c.encode_utf8(&mut [0; 4]).as_bytes())?,
    }
  }
  output.write_all(b"\"")
}

const fn status(status: ThreadStatus) -> &'static str {
  match status {
    ThreadStatus::CaptureThread => "capture_thread",
    ThreadStatus::SuspendFailed => "suspend_failed",
    ThreadStatus::RegistersUnavailable => "registers_unavailable",
    ThreadStatus::StackUnavailable => "stack_unavailable",
    ThreadStatus::Captured(reason) => match reason {
      StopReason::EndOfStack => "end_of_stack",
      StopReason::OutputFull => "output_full",
      StopReason::FramePointerUnavailable => "frame_pointer_unavailable",
      StopReason::InvalidFramePointer => "invalid_frame_pointer",
      StopReason::OutOfStackBounds => "out_of_stack_bounds",
      StopReason::UnreadableMemory => "unreadable_memory",
      StopReason::NonIncreasingFramePointer => "non_increasing_frame_pointer",
      StopReason::AsyncBoundary => "async_boundary",
      StopReason::InvalidUnwindInfo => "invalid_unwind_info",
      StopReason::UnsupportedUnwindEncoding => "unsupported_unwind_encoding",
      StopReason::DwarfRequired => "dwarf_required",
      StopReason::ReturnAddressUnavailable => "return_address_unavailable",
    },
  }
}
