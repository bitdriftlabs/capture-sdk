// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#[cfg(test)]
#[path = "./encode_test.rs"]
mod tests;

use crate::{CancellationToken, Image, Profile, Snapshot, ThreadStatus};
use bd_proto::protos::profiling::crash::report::thread_details::Thread;
use bd_proto::protos::profiling::crash::report::{BinaryImage, ThreadDetails};
use bd_proto::protos::profiling::crash::{Frame as ProtoFrame, FrameType};
use bd_proto::protos::profiling::stack_profile::{Samples, Stack, StackProfile, ThreadSamples};
use bd_proto::protos::profiling::thread_snapshot::ThreadSnapshot;
use bd_unwind::aarch64::{Frame, FrameKind};
use std::collections::{BTreeMap, HashMap};
use std::fmt::Write;
use std::io;

pub(super) fn check(cancellation: &CancellationToken) -> io::Result<()> {
  if cancellation.is_cancelled() {
    return Err(io::ErrorKind::ConnectionAborted.into());
  }
  Ok(())
}

fn index(value: usize) -> io::Result<u32> {
  u32::try_from(value).map_err(|_| io::ErrorKind::FileTooLarge.into())
}

fn thread(native_id: u64, name: &str, main_thread_id: Option<u64>) -> Thread {
  Thread {
    name: if main_thread_id == Some(native_id) {
      "Main Thread".into()
    } else if name.is_empty() {
      format!("Thread {native_id}")
    } else {
      name.to_owned()
    },
    ..Default::default()
  }
}

pub(super) fn snapshot(
  snapshot: &Snapshot,
  cancellation: &CancellationToken,
) -> io::Result<ThreadSnapshot> {
  check(cancellation)?;
  let mut images = Images::new(&snapshot.images);
  let mut threads = Vec::new();
  for captured in &snapshot.threads {
    check(cancellation)?;
    if captured.status == ThreadStatus::CaptureThread {
      continue;
    }
    let mut thread = thread(captured.native_id, &captured.name, snapshot.main_thread_id);
    for frame in &captured.frames {
      check(cancellation)?;
      thread.frames.push(images.frame(frame));
    }
    threads.push(thread);
  }
  Ok(ThreadSnapshot {
    thread_details: Some(ThreadDetails {
      total_threads: index(snapshot.threads.len())?,
      threads,
      ..Default::default()
    })
    .into(),
    binary_images: images.output,
    ..Default::default()
  })
}

pub(super) fn profile(
  profile: &Profile,
  cancellation: &CancellationToken,
) -> io::Result<StackProfile> {
  check(cancellation)?;
  let mut output = StackProfile {
    interval_ns: u64::try_from(profile.config.interval.as_nanos())
      .map_err(|_| io::ErrorKind::InvalidInput)?,
    // Every profile reserves index zero, even when every observation has a usable stack.
    stacks: vec![Stack::default()],
    ..Default::default()
  };
  let mut images = Images::new(&profile.images);
  let mut frame_indices = HashMap::new();
  let mut stack_indices = HashMap::new();
  let mut threads = BTreeMap::new();
  let mut frame_offset = 0;
  for sample in &profile.samples {
    check(cancellation)?;
    let mut stack = Vec::with_capacity(sample.frame_count);
    for frame in profile
      .frames
      .range(frame_offset .. frame_offset + sample.frame_count)
    {
      check(cancellation)?;
      let frame = images.frame(frame);
      let key = (frame.frame_address, frame.image_id.clone());
      let frame_index = if let Some(&existing) = frame_indices.get(&key) {
        existing
      } else {
        let next = index(output.frames.len())?;
        frame_indices.insert(key, next);
        output.frames.push(frame);
        next
      };
      stack.push(frame_index);
    }
    frame_offset += sample.frame_count;
    let stack_index = if stack.is_empty() {
      0
    } else if let Some(&existing) = stack_indices.get(&stack) {
      existing
    } else {
      let next = index(output.stacks.len())?;
      output.stacks.push(Stack {
        frame_indices: stack.clone(),
        ..Default::default()
      });
      stack_indices.insert(stack, next);
      next
    };
    let entry = threads
      .entry(sample.native_id)
      .or_insert_with(|| ThreadSamples {
        thread: Some(thread(
          sample.native_id,
          sample.name(),
          profile.main_thread_id,
        ))
        .into(),
        samples: Some(Samples::default()).into(),
        ..Default::default()
      });
    // Both columns are appended together and preserve the original shared-origin timestamps.
    let samples = entry.samples.mut_or_insert_default();
    samples.timestamp_ns.push(sample.timestamp_ns);
    samples.stack_indices.push(stack_index);
  }
  output.threads = threads.into_values().collect();
  output.binary_images = images.output;
  Ok(output)
}

//
// Images
//

struct Images<'a> {
  by_address: BTreeMap<u64, Vec<&'a Image>>,
  numbers: HashMap<(u64, [u8; 16]), usize>,
  output: Vec<BinaryImage>,
}

impl<'a> Images<'a> {
  fn new(images: &'a [Image]) -> Self {
    let mut by_address: BTreeMap<u64, Vec<&Image>> = BTreeMap::new();
    for image in images {
      by_address
        .entry(image.info.text.start())
        .or_default()
        .push(image);
    }
    Self {
      by_address,
      numbers: HashMap::new(),
      output: Vec::new(),
    }
  }

  fn frame(&mut self, frame: &Frame) -> ProtoFrame {
    // Use the call site only for image lookup. Keep the original address on the wire; the
    // consumer derives PC versus return address from each stack's original frame position.
    let address = match frame.kind {
      FrameKind::ProgramCounter => frame.address,
      FrameKind::ReturnAddress => frame.address.saturating_sub(1),
    };
    // An older, larger image can still contain this address even if a later image starts closer
    // to it. Inspect every candidate range retained in the history.
    let mut matches = self
      .by_address
      .range(..= address)
      .flat_map(|(_, images)| images.iter().copied())
      .filter(|image| image.info.text.contains(address, 1));
    let image = matches.next();
    // History can contain different images at the same address after unloading. Without an
    // image epoch per sample, leave ambiguous addresses unattributed rather than guessing.
    let image = image.filter(|first| matches.all(|other| first.info == other.info));
    let image_id = if let Some(image) = image.filter(|image| !image.name.is_empty()) {
      if let Some(uuid) = image.info.uuid {
        let key = (image.info.text.start(), uuid);
        let number = *self.numbers.entry(key).or_insert_with(|| {
          let mut id = String::with_capacity(36);
          for (i, byte) in uuid.iter().enumerate() {
            if [4, 6, 8, 10].contains(&i) {
              id.push('-');
            }
            let _ = write!(id, "{byte:02x}");
          }
          self.output.push(BinaryImage {
            id,
            path: image.name.to_string(),
            load_address: image.info.text.start(),
            length: image.info.text.size(),
            ..Default::default()
          });
          self.output.len() - 1
        });
        Some(self.output[number].id.clone())
      } else {
        None
      }
    } else {
      None
    };
    ProtoFrame {
      frame_type: FrameType::DWARF.into(),
      frame_address: Some(frame.address),
      image_id,
      ..Default::default()
    }
  }
}
