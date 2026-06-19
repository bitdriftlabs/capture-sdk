// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(target_vendor = "apple")]
mod nsexception;

// Apple-only for now; widen this cfg when non-Apple monitors are introduced.
#[cfg(target_vendor = "apple")]
trait Monitor {
  fn install(&self) -> bool;
  fn uninstall(&self);
}

#[cfg(target_vendor = "apple")]
pub(crate) fn install() -> bool {
  nsexception::NSExceptionMonitor.install()
}

#[cfg(not(target_vendor = "apple"))]
pub(crate) fn install() -> bool {
  false
}

#[cfg(target_vendor = "apple")]
pub(crate) fn uninstall() {
  nsexception::NSExceptionMonitor.uninstall();
}

#[cfg(not(target_vendor = "apple"))]
pub(crate) fn uninstall() {}
