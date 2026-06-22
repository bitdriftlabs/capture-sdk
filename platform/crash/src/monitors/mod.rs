// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(target_vendor = "apple")]
mod nsexception;

// Abstracts process-global crash monitor lifecycle for the current platform. `install` registers
// the platform-specific hook, and `uninstall` restores the prior process state when possible.
// Apple-only for now; widen this cfg when non-Apple monitors are introduced.
#[cfg(target_vendor = "apple")]
trait Monitor {
  fn install(&self) -> bool;
  fn uninstall(&self);
}

// Install every crash monitor supported on the current platform. 
#[cfg(target_vendor = "apple")]
pub(crate) fn install() -> bool {
  nsexception::NSExceptionMonitor.install()
}

// Non-Apple builds currently have no crash monitor implementation in this crate, so installation
// is a no-op and reports failure to install any monitor. This will change in the future.
#[cfg(not(target_vendor = "apple"))]
pub(crate) fn install() -> bool {
  false
}

// Uninstall every crash monitor that may have been registered by `install()`.
#[cfg(target_vendor = "apple")]
pub(crate) fn uninstall() {
  nsexception::NSExceptionMonitor.uninstall();
}

// Non-Apple builds currently have no crash monitor implementation to tear down.
// This will change in the future.
#[cfg(not(target_vendor = "apple"))]
pub(crate) fn uninstall() {}
