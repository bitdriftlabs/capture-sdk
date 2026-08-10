#!/bin/bash

set -euo pipefail

archive_tool=$(xcrun --find llvm-ar)
if [[ -n "${IOS_CAPTURE_XCFRAMEWORK_ROOT:-}" ]]; then
  framework_root="$IOS_CAPTURE_XCFRAMEWORK_ROOT"
else
  framework_root="$TEST_SRCDIR/$TEST_WORKSPACE/Capture.xcframework"
fi
archive="$framework_root/ios-arm64/Capture.framework/Capture"

if [[ ! -f "$archive" ]]; then
  echo "no arm64 Capture framework archive found at $archive" >&2
  exit 1
fi

duplicates=$("$archive_tool" t "$archive" |
  awk '/^(alloc|cfg_if|compiler_builtins|core|hashbrown|libc|rustc_demangle|rustc_std_workspace_(alloc|core)|std|std_detect|unwind)-/' |
  LC_ALL=C sort |
  uniq -d)

if [[ -n "$duplicates" ]]; then
  echo "duplicate custom Rust standard-library members in $archive:" >&2
  echo "$duplicates" >&2
  exit 1
fi
