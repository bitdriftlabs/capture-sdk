// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::Profile;
use crate::json::{status, string};
use bd_unwind::aarch64::FrameKind;
use std::collections::BTreeMap;
use std::io::{self, Write};

// Each observation has an independent root-to-leaf chain. Samples reference its leaf and actual
// time.
pub(super) fn write(profile: &Profile, output: &mut impl Write) -> io::Result<()> {
  let interval = profile.config.interval.as_nanos();
  write!(
    output,
    r#"{{"meta":{{"version":24,"interval":{}.{:06},"startTime":0,
    "product":"bitdrift stack profile","stackwalk":1}},"libs":[],"threads":["#,
    interval / 1_000_000,
    interval % 1_000_000
  )?;
  let mut threads = BTreeMap::new();
  for sample in &profile.samples {
    threads.insert(sample.native_id, sample.name());
  }
  for (thread_index, (native_id, name)) in threads.iter().enumerate() {
    if thread_index != 0 {
      output.write_all(b",")?;
    }
    write!(
      output,
      r#"{{"pid":{},"tid":{},"name":"#,
      profile.pid,
      thread_index + 1
    )?;
    if name.is_empty() {
      string(output, &format!("Thread {native_id}"))?;
    } else {
      string(output, name)?;
    }
    write!(
      output,
      r#", "bitdrift":{{"nativeThreadId":"{native_id}","observations":["#
    )?;
    let mut first = true;
    let mut observation_frame_offset = 0;
    for sample in &profile.samples {
      let frames = profile
        .frames
        .range(observation_frame_offset .. observation_frame_offset + sample.frame_count);
      observation_frame_offset += sample.frame_count;
      if sample.native_id != *native_id {
        continue;
      }
      if !first {
        output.write_all(b",")?;
      }
      first = false;
      write!(
        output,
        r#"{{"timestampNs":{},"status":"{}","stackTruncated":{},
        "copiedStackBytes":{},"suspensionNs":{},"frames":["#,
        sample.timestamp_ns,
        status(sample.status),
        sample.stack_truncated,
        sample.copied_stack_bytes,
        sample.suspension_ns
      )?;
      for (index, frame) in frames.enumerate() {
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
      output.write_all(b"]}")?;
    }
    output.write_all(br#"]},"stringTable":["#)?;
    let mut frame_offset = 0;
    let mut frame_count = 0;
    for sample in &profile.samples {
      let frames = profile
        .frames
        .range(frame_offset .. frame_offset + sample.frame_count);
      frame_offset += sample.frame_count;
      if sample.native_id != *native_id {
        continue;
      }
      for frame in frames.rev() {
        if frame_count != 0 {
          output.write_all(b",")?;
        }
        write!(output, r#""0x{:x}""#, frame.address)?;
        frame_count += 1;
      }
    }
    output.write_all(br#"],"frameTable":{"schema":{"location":0},"data":["#)?;
    for index in 0 .. frame_count {
      if index != 0 {
        output.write_all(b",")?;
      }
      write!(output, "[{index}]")?;
    }
    output.write_all(br#"]},"stackTable":{"schema":{"prefix":0,"frame":1},"data":["#)?;
    let mut index = 0;
    for sample in profile
      .samples
      .iter()
      .filter(|sample| sample.native_id == *native_id)
    {
      for frame_index in 0 .. sample.frame_count {
        if index != 0 {
          output.write_all(b",")?;
        }
        if frame_index == 0 {
          write!(output, "[null,{index}]")?;
        } else {
          write!(output, "[{},{}]", index - 1, index)?;
        }
        index += 1;
      }
    }
    output.write_all(br#"]},"samples":{"schema":{"stack":0,"time":1},"data":["#)?;
    let mut first = true;
    let mut index = 0;
    for sample in profile
      .samples
      .iter()
      .filter(|sample| sample.native_id == *native_id)
    {
      index += sample.frame_count;
      if sample.frame_count == 0 {
        continue;
      }
      if !first {
        output.write_all(b",")?;
      }
      first = false;
      write!(
        output,
        "[{},{}.{:06}]",
        index - 1,
        sample.timestamp_ns / 1_000_000,
        sample.timestamp_ns % 1_000_000
      )?;
    }
    output.write_all(b"]}}")?;
  }
  let summary = profile.summary();
  write!(
    output,
    r#"],"bitdrift":{{"version":1,"capture":"profile","unwinder":"arm64_frame_pointer",
    "durationMs":{},"captureRounds":{},"capturedSamples":{},
    "failedSamples":{},"truncatedSamples":{},
    "skippedSamples":{},"retainedSamples":{},"droppedSamples":{},
    "missedTicks":{},"droppedImages":{},
    "failedImageReads":{},"noUserContextSamples":{},"timing":{{"#,
    profile.config.duration.as_millis(),
    summary.capture_rounds,
    summary.captured_samples,
    summary.failed_samples,
    summary.truncated_samples,
    summary.skipped_samples,
    summary.retained_samples,
    summary.dropped_samples,
    summary.missed_ticks,
    summary.dropped_images,
    summary.failed_image_reads,
    summary.no_user_context_samples
  )?;
  // Totals and maxima are per-round wall-clock measurements, in nanoseconds. The capture and
  // unwind stages do not overlap; roundWork also includes bookkeeping. Loader callbacks update
  // image history independently of the sampling worker.
  for (index, (name, timing)) in [
    ("capture", &profile.timing.capture),
    ("unwind", &profile.timing.unwind),
    ("roundWork", &profile.timing.round_work),
  ]
  .iter()
  .enumerate()
  {
    if index != 0 {
      output.write_all(b",")?;
    }
    write!(
      output,
      r#""{name}":{{"totalNs":{},"maxNs":{}}}"#,
      timing.total_ns, timing.max_ns
    )?;
  }
  write!(
    output,
    r#"}},"imagePreparationNs":{},"imageFinalizationNs":{},"images":["#,
    profile.timing.image_preparation_ns, profile.timing.image_finalization_ns
  )?;
  for (index, image) in profile.images.iter().enumerate() {
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
  output.write_all(b"]}}")
}
