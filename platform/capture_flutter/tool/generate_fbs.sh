#!/usr/bin/env bash
# Regenerates the Dart issue report flatbuffer bindings from shared-core's schemas.
#
# Usage: tool/generate_fbs.sh <path to shared-core checkout>

set -euo pipefail

shared_core="${1:?usage: $0 <path to shared-core checkout>}"
plugin_dir="$(cd "$(dirname "$0")/.." && pwd)"
out_dir="$plugin_dir/lib/src/fbs"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

flatc --dart -I "$shared_core/api/src" -o "$tmp_dir" \
  "$shared_core/api/src/bitdrift_public/fbs/issue-reporting/v1/report.fbs" \
  "$shared_core/api/src/bitdrift_public/fbs/common/v1/common.fbs"

mkdir -p "$out_dir"
cp "$tmp_dir/common_bitdrift_public.fbs.common.v1_generated.dart" "$out_dir/common_generated.dart"
sed 's#./common_bitdrift_public.fbs.common.v1_generated.dart#common_generated.dart#' \
  "$tmp_dir/report_bitdrift_public.fbs.issue_reporting.v1_generated.dart" > "$out_dir/report_generated.dart"

# Align the 16-byte timestamp struct, which the Dart generator does not do.
perl -0pi -e 's/(\n(\s*)fbBuilder\.pad\(4\);\n\s*fbBuilder\.putUint32\(_?nanos\);)/\n$2fbBuilder.pad((8 - fbBuilder.offset % 8) % 8);$1/g' \
  "$out_dir/report_generated.dart"
