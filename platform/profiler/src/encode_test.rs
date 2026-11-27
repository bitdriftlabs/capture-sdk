// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::{profile, snapshot};
use crate::profile::ProfileSample;
use crate::tests::sample_snapshot;
use crate::{CancellationToken, Image, Observation, Profile, ProfileConfig, ThreadStatus};
use bd_unwind::aarch64::{Frame, FrameKind, StopReason};
use bd_unwind::image::ImageInfo;
use bd_unwind::memory::AddressRange;

fn image(uuid: u8, start: u64) -> Image {
  Image {
    info: ImageInfo {
      uuid: Some([uuid; 16]),
      text: AddressRange::new(start, 0x1000).unwrap(),
    },
    name: "DemoApp".into(),
  }
}

fn record(profile: &mut Profile, native_id: u64, timestamp_ns: u64, frames: &[Frame]) {
  let observation = Observation {
    native_id,
    name: "same name",
    timestamp_ns,
    frames,
    status: ThreadStatus::Captured(StopReason::EndOfStack),
    stack_truncated: false,
  };
  profile.record(ProfileSample::new(&observation), frames);
}

#[test]
fn references_only_used_images_and_preserves_unknown_frames_and_original_addresses() {
  let mut captured = sample_snapshot();
  captured.images = vec![image(1, 0x1000), image(2, 0x9000)];
  captured.threads[0].frames = vec![
    Frame {
      address: 0x1100,
      kind: FrameKind::ProgramCounter,
    },
    Frame {
      address: 0x2000,
      kind: FrameKind::ReturnAddress,
    },
    Frame {
      address: u64::MAX,
      kind: FrameKind::ReturnAddress,
    },
  ];
  let output = snapshot(&captured, &CancellationToken::default()).unwrap();
  assert_eq!(output.binary_images.len(), 1);
  let image = &output.binary_images[0];
  assert_eq!(image.id, "01010101-0101-0101-0101-010101010101");
  assert_eq!(image.path, "DemoApp");
  assert_eq!(image.load_address, 0x1000);
  assert_eq!(image.length, 0x1000);
  let frames = &output.thread_details.as_ref().unwrap().threads[0].frames;
  assert_eq!(frames.len(), 3);
  assert_eq!(frames[1].frame_address, Some(0x2000));
  assert_eq!(frames[1].image_id.as_ref(), Some(&image.id));
  assert_eq!(frames[2].frame_address, Some(u64::MAX));
  assert!(frames[2].image_id.is_none());
  assert!(
    frames
      .iter()
      .all(|frame| frame.symbol_name.is_none() && frame.source_file.is_none())
  );
}

#[test]
fn unloaded_images_with_reused_addresses_are_not_misattributed() {
  let mut captured = sample_snapshot();
  captured.images = vec![image(1, 0x1000), image(2, 0x1000)];
  captured.threads[0].frames = vec![Frame {
    address: 0x1100,
    kind: FrameKind::ProgramCounter,
  }];
  let output = snapshot(&captured, &CancellationToken::default()).unwrap();
  assert_eq!(output.binary_images.len(), 0);
  let frame = &output.thread_details.as_ref().unwrap().threads[0].frames[0];
  assert_eq!(frame.frame_address, Some(0x1100));
  assert!(frame.image_id.is_none());
}

#[test]
fn overlapping_history_ranges_preserve_unique_matches_and_reject_ambiguous_ones() {
  let mut old = image(1, 0x1000);
  old.info.text = AddressRange::new(0x1000, 0x4000).unwrap();
  let newer = image(2, 0x3000);
  for images in [vec![old.clone(), newer.clone()], vec![newer, old]] {
    let mut captured = sample_snapshot();
    captured.images = images;
    captured.threads[0].frames = vec![
      Frame {
        address: 0x3500,
        kind: FrameKind::ProgramCounter,
      },
      Frame {
        address: 0x4500,
        kind: FrameKind::ProgramCounter,
      },
      Frame {
        address: 0x4000,
        kind: FrameKind::ProgramCounter,
      },
      Frame {
        address: 0x4000,
        kind: FrameKind::ReturnAddress,
      },
    ];
    let output = snapshot(&captured, &CancellationToken::default()).unwrap();
    assert_eq!(output.binary_images.len(), 1);
    assert_eq!(output.binary_images[0].load_address, 0x1000);
    let frames = &output.thread_details.as_ref().unwrap().threads[0].frames;
    assert!(frames[0].image_id.is_none());
    assert_eq!(
      frames[1].image_id.as_ref(),
      Some(&output.binary_images[0].id)
    );
    assert_eq!(frames[2].image_id, frames[1].image_id);
    assert!(frames[3].image_id.is_none());
    assert_eq!(frames[3].frame_address, Some(0x4000));
  }
}

#[test]
fn profile_interns_stacks_across_threads_and_preserves_gaps_and_large_timestamps() {
  let mut captured = Profile::new(ProfileConfig::new(20, 1000).unwrap(), 16384).unwrap();
  captured.images = vec![image(1, 0x1000)];
  let frames = [
    Frame {
      address: 0x1100,
      kind: FrameKind::ProgramCounter,
    },
    Frame {
      address: 0x1200,
      kind: FrameKind::ReturnAddress,
    },
  ];
  let time = (1 << 53) + 1;
  record(&mut captured, 7, time, &frames);
  record(&mut captured, 9, time + 1, &frames);
  record(&mut captured, 7, time + 51_000_003, &[]);
  record(&mut captured, 7, time + 105_000_007, &frames);
  let output = profile(&captured, &CancellationToken::default()).unwrap();
  assert_eq!(output.frames.len(), 2);
  assert_eq!(output.stacks.len(), 2);
  assert_eq!(output.stacks[0].frame_indices.len(), 0);
  assert_eq!(output.stacks[1].frame_indices, [0, 1]);
  assert_eq!(output.threads.len(), 2);
  assert_eq!(
    output.threads[0].thread.as_ref().unwrap().name,
    output.threads[1].thread.as_ref().unwrap().name
  );
  assert!(
    output
      .threads
      .iter()
      .all(|thread| thread.thread.as_ref().unwrap().frames.is_empty())
  );
  let first = output.threads[0].samples.as_ref().unwrap();
  assert_eq!(
    first.timestamp_ns,
    [time, time + 51_000_003, time + 105_000_007]
  );
  assert_eq!(first.stack_indices, [1, 0, 1]);
  assert_eq!(
    output.threads[1].samples.as_ref().unwrap().stack_indices,
    [1]
  );
}

#[test]
fn valid_only_profile_still_reserves_the_empty_stack() {
  let mut captured = Profile::new(ProfileConfig::new(20, 100).unwrap(), 4096).unwrap();
  record(
    &mut captured,
    1,
    0,
    &[Frame {
      address: 0x1100,
      kind: FrameKind::ProgramCounter,
    }],
  );
  let output = profile(&captured, &CancellationToken::default()).unwrap();
  assert_eq!(output.stacks[0].frame_indices.len(), 0);
  assert_eq!(
    output.threads[0].samples.as_ref().unwrap().stack_indices,
    [1]
  );
}
