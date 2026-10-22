// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// Names the candidates recorded under the locks. Runs with no zone locked, but the memory it reads
// may have been freed since it was recorded, so every read goes through a safe read.

use super::tables::{Candidate, ClassTable, ReadableRanges, Resolution};
use super::{ADDRESS_MASK, Census, ISA_MASK, size_bucket};
use crate::images::ImageTable;
use crate::safe_read;
use crate::sys::mach::now_ns;
use crate::sys::objc::validated_class_name;

const CXX_NAME_CAPACITY: usize = 256;
/// Hops from a class to the root metaclass, which points at itself.
const ISA_CHAIN_HOPS: usize = 3;
const SUPERCLASS_CHAIN_HOPS: usize = 16;
/// First words below this are Swift metadata kinds rather than pointers.
const METADATA_KIND_MAX: usize = 0x800;

//
// Resolver
//

struct Resolver<'t> {
  classes: &'t ClassTable,
  ranges: &'t ReadableRanges,
  images: Option<&'t ImageTable>,
}

impl Resolver<'_> {
  /// Reads a word, rejecting addresses outside the surveyed readable ranges before paying a
  /// syscall.
  fn read_word(&self, address: usize) -> Option<usize> {
    if address == 0 {
      return None;
    }
    self.ranges.readable_end(address)?;
    safe_read::read_word(address)
  }

  /// A class's isa chain reaches the root metaclass, which points at itself.
  fn isa_chain_is_consistent(&self, candidate: usize) -> bool {
    let mut current = candidate;
    for hop in 0 .. ISA_CHAIN_HOPS {
      let Some(isa) = self.read_word(current) else {
        return false;
      };
      let next = isa & ISA_MASK;
      if next == 0 {
        return false;
      }
      if next == current {
        return hop > 0;
      }
      current = next;
    }
    false
  }

  /// Every real class, including Swift generic instantiations the runtime does not list, inherits
  /// from a registered class. The superclass is the second word of `objc_class`.
  fn superclass_chain_reaches_known(&self, candidate: usize) -> bool {
    let mut current = candidate;
    for _ in 0 .. SUPERCLASS_CHAIN_HOPS {
      let Some(superclass) = self.read_word(current + size_of::<usize>()) else {
        return false;
      };
      let superclass = superclass & ISA_MASK;
      if superclass == 0 || superclass == current {
        return false;
      }
      if self.classes.find(superclass).is_some() {
        return true;
      }
      current = superclass;
    }
    false
  }

  fn class_name(&self, candidate: usize) -> Option<String> {
    if !self.isa_chain_is_consistent(candidate) || !self.superclass_chain_reaches_known(candidate) {
      return None;
    }
    // SAFETY: validated as a class by both chains above.
    unsafe { validated_class_name(candidate) }
  }

  /// Itanium ABI: a polymorphic object's first word points into its vtable, whose previous slot
  /// is the `type_info` pointer; the `type_info`'s second word is the mangled name.
  fn cxx_name(&self, vtable: usize) -> Option<String> {
    let type_info = self.read_word(vtable.checked_sub(size_of::<usize>())?)? & ADDRESS_MASK;
    let name = self.read_word(type_info + size_of::<usize>())? & ADDRESS_MASK;
    self.ranges.readable_end(name)?;
    let name = safe_read::read_string(name, CXX_NAME_CAPACITY)?;
    // A mangled name starts with a length, a nested-name marker or a pointer modifier.
    let first = *name.first()?;
    if name.len() < 2 || !(first.is_ascii_alphanumeric() || first == b'_' || first == b'*') {
      return None;
    }
    String::from_utf8(name).ok()
  }
}

impl Census<'_> {
  /// Names the candidates recorded under the locks. Must run with no zone locked.
  pub(super) fn resolve(&mut self) {
    let started = now_ns();
    let resolver = Resolver {
      classes: &self.classes,
      ranges: &self.ranges,
      images: self.images,
    };
    for candidate in self
      .candidates
      .slots
      .iter_mut()
      .filter(|slot| slot.metadata != 0)
    {
      resolve_candidate(&resolver, candidate, &mut self.names, &mut self.summary);
      if candidate.resolution == Resolution::Unresolved {
        // No per-candidate histogram is kept; the average size places its blocks.
        self.summary.unclassified_histogram[size_bucket(candidate.bytes / candidate.blocks)] +=
          candidate.blocks;
      }
    }
    self.summary.resolve_ns = now_ns() - started;
  }
}

fn resolve_candidate(
  resolver: &Resolver<'_>,
  candidate: &mut Candidate,
  names: &mut Vec<String>,
  summary: &mut super::Summary,
) {
  let Some(first_word) = resolver.read_word(candidate.metadata) else {
    summary.unreadable_candidates += 1;
    return;
  };
  // Struct, enum and tuple metadata: not a heap object type.
  if first_word < METADATA_KIND_MAX {
    return;
  }

  let named = resolver
    .class_name(candidate.metadata)
    .map(|name| (Resolution::Class, name))
    .or_else(|| {
      resolver
        .cxx_name(candidate.metadata)
        .map(|name| (Resolution::Cxx, name))
    });
  if let Some((resolution, name)) = named {
    candidate.resolution = resolution;
    candidate.name = names.len();
    names.push(name);
    summary.named_blocks += candidate.blocks;
    summary.named_bytes += candidate.bytes;
    summary.reclassify(candidate.blocks, candidate.bytes);
    return;
  }

  if let Some(image) = resolver
    .images
    .and_then(|images| images.owner(candidate.metadata))
  {
    candidate.resolution = Resolution::Image;
    candidate.name = image;
    summary.image_blocks += candidate.blocks;
    summary.image_bytes += candidate.bytes;
    summary.reclassify(candidate.blocks, candidate.bytes);
  }
}
