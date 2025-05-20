#!/bin/bash

set -euxo pipefail

remove_rmeta () {
  local -r bin="$1"

  ar d "$bin" $(ar t "$bin"| grep "lib\.r")

  # Do not exit on error.
  set +e

  # Look for a 'lib.rmeta' with 'e' character replaceed with either one of the
  # following: new line, tab or a space character.
  ar d "$bin" "$(echo "lib.rm\x0ata")" "$(echo "lib.rm\x09ta")" "$(echo "lib.rm\x20ta")"

  if ar t "$bin"| grep "lib\.r"; then
    # Method succeeded which means that it found
    # one of the unexpected object references which means that
    # we were not able to remove all of the revevant `lib.rmeta`
    # file references and should exit with error.
    echo "failed to remove 'lib.rmeta'-like reference(s) from '$bin'" >&2
    exit 1;
  fi

  # Revert back to previous behavior: exit on error.
  set -e

  return 0
}

framework_to_rewrite="$2"
framework_base=$(basename $framework_to_rewrite)
framework_name=${framework_base%.*}

if [ ! -d "$framework_to_rewrite" ];
then
  >&2 echo "Directory $framework_to_rewrite does not exist"
  exit 1;
fi

for binary in $(find $framework_to_rewrite -type f -name $framework_name);
do
  if lipo -info $binary | grep -q x86_64; then
    x86_slice=$(mktemp -d)/$framework_name
    arm_slice=$(mktemp -d)/$framework_name
    lipo -thin x86_64 "$binary" -output "$x86_slice"
    lipo -thin arm64 "$binary" -output "$arm_slice"

    remove_rmeta "$x86_slice"
    remove_rmeta "$arm_slice"

    lipo -create "$x86_slice" "$arm_slice" -output "$binary"
  else
    remove_rmeta "$binary"
  fi

  # NOTE: Apple broke their bitcode_strip tool and it's trying to open a `strip` file
  xcrun bitcode_strip -r "$binary" -o "$binary"
done
