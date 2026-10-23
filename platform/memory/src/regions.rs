// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// Stage 2: walks this process's VM map and aggregates it by Mach tag and by owning image. Takes no
// locks. Pages are counted only for regions whose current or maximum protection allows writing: a
// region that could never be written cannot hold a dirty page.

#[cfg(test)]
#[path = "./regions_test.rs"]
mod tests;

use crate::images::ImageTable;
use crate::report::{ImageChargeRow, RegionsReport, TagRow};
use crate::sys::mach::{KERN_INVALID_ADDRESS, kernel_page_size};
use crate::sys::vm_region::{
  SM_COW,
  SM_SHARED,
  SM_SHARED_ALIASED,
  SM_TRUESHARED,
  VM_PROT_WRITE,
  region_info,
  region_short_info,
};
use crate::tags::tag_name;

const MAX_REGIONS: u32 = 200_000;
const MAX_SUBMAP_DEPTH: u32 = 8;
const TAG_COUNT: usize = 256;

//
// TagTotals
//

#[derive(Clone, Copy, Default)]
struct TagTotals {
  private_dirty: u64,
  shared_dirty: u64,
  compressed: u64,
  virtual_size: u64,
  regions: u32,
}

/// Pages other processes map too. A copy-on-write region with several references counts as shared:
/// the shared cache arrives that way, and charging it in full puts the total above the footprint.
const fn is_shared(share_mode: u8, ref_count: u32) -> bool {
  match share_mode {
    SM_SHARED | SM_TRUESHARED | SM_SHARED_ALIASED => true,
    SM_COW => ref_count > 1,
    _ => false,
  }
}

pub fn walk(images: Option<&ImageTable>) -> RegionsReport {
  let page_size = kernel_page_size();
  let mut by_tag = [TagTotals::default(); TAG_COUNT];
  let mut charged_by_image = vec![0u64; images.map_or(0, ImageTable::len)];
  let mut region_count = 0;
  let mut truncated = false;

  let mut address = 0;
  // In/out: the kernel reports the depth it found the region at. Resetting it per region makes
  // leaving a submap re-enter it forever.
  let mut depth = 0;

  // Check to prevent cases where regions are changing faster than it can be read.
  while region_count < MAX_REGIONS {
    let region = match region_short_info(address, depth) {
      Ok(region) if region.size > 0 => region,
      // KERN_INVALID_ADDRESS is the normal end of the map.
      Err(KERN_INVALID_ADDRESS) => break,
      _ => {
        truncated = true;
        break;
      },
    };
    address = region.address;
    depth = region.depth;
    let info = region.info;

    // A submap is a container: descend without advancing, or its contents count twice.
    if info.is_submap != 0 {
      if depth + 1 >= MAX_SUBMAP_DEPTH {
        address += region.size;
      } else {
        depth += 1;
      }
      continue;
    }

    // Current protection is not enough: dyld writes into __DATA_CONST and then makes it
    // read-only, and those pages stay dirty.
    let mut ref_count = info.ref_count;
    let mut dirty = 0;
    let mut compressed = 0;
    if (info.protection | info.max_protection) & VM_PROT_WRITE != 0 {
      match region_info(address, depth) {
        // The map can change between the two calls; losing a count beats charging it to the
        // wrong region.
        Ok(counted) if counted.address == address => {
          let counted = counted.info;
          dirty = u64::from(counted.pages_dirtied) * page_size;
          compressed = u64::from(counted.pages_swapped_out) * page_size;
          ref_count = counted.ref_count;
        },
        _ => truncated = true,
      }
    }

    let totals = &mut by_tag[info.user_tag as usize % TAG_COUNT];
    totals.regions += 1;
    totals.virtual_size += region.size as u64;
    totals.compressed += compressed;
    let shared = is_shared(info.share_mode, ref_count);
    if shared {
      totals.shared_dirty += dirty;
    } else {
      totals.private_dirty += dirty;
    }

    // Which segment the dirty pages landed in is unknown at region granularity, so the charge is
    // prorated by how much of the region each image covers.
    let charged = if shared { 0 } else { dirty } + compressed;
    if let Some(images) = images
      && charged > 0
    {
      let size = region.size as u128;
      images.for_each_overlap(address, address + region.size, |image, bytes| {
        if let Some(total) = charged_by_image.get_mut(image) {
          *total += u64::try_from(u128::from(charged) * u128::from(bytes) / size).unwrap_or(0);
        }
      });
    }

    region_count += 1;
    address += region.size;
  }

  if region_count >= MAX_REGIONS {
    truncated = true;
  }
  log::debug!("walked {region_count} regions, truncated: {truncated}");
  report(&by_tag, region_count, truncated, &charged_by_image)
}

fn report(
  by_tag: &[TagTotals],
  region_count: u32,
  truncated: bool,
  charged_by_image: &[u64],
) -> RegionsReport {
  let mut tag_rows: Vec<TagRow> = by_tag
    .iter()
    .enumerate()
    .filter(|(_, totals)| totals.regions > 0)
    .map(|(tag, totals)| {
      let tag = u32::try_from(tag).unwrap_or_default();
      TagRow {
        name: tag_name(tag).map_or_else(|| format!("VM_TAG_{tag}"), str::to_string),
        tag,
        regions: totals.regions,
        private_dirty: totals.private_dirty,
        shared_dirty: totals.shared_dirty,
        compressed: totals.compressed,
        virtual_size: totals.virtual_size,
        charged: totals.private_dirty + totals.compressed,
      }
    })
    .collect();
  tag_rows.sort_by(|left, right| {
    (right.charged, right.virtual_size).cmp(&(left.charged, left.virtual_size))
  });

  RegionsReport {
    regions: region_count,
    truncated,
    private_dirty: tag_rows.iter().map(|row| row.private_dirty).sum(),
    shared_dirty: tag_rows.iter().map(|row| row.shared_dirty).sum(),
    compressed: tag_rows.iter().map(|row| row.compressed).sum(),
    charged: tag_rows.iter().map(|row| row.charged).sum(),
    virtual_size: tag_rows.iter().map(|row| row.virtual_size).sum(),
    by_tag: tag_rows,
    by_image: image_rows(charged_by_image),
  }
}

fn image_rows(charged_by_image: &[u64]) -> Vec<ImageChargeRow> {
  let mut rows: Vec<ImageChargeRow> = charged_by_image
    .iter()
    .enumerate()
    .filter(|(_, charged)| **charged > 0)
    .map(|(image, charged)| ImageChargeRow {
      image,
      charged: *charged,
    })
    .collect();
  rows.sort_by(|left, right| {
    right
      .charged
      .cmp(&left.charged)
      .then(left.image.cmp(&right.image))
  });
  rows
}
