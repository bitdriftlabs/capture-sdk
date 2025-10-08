#!/bin/bash

set -euxo pipefail

AR=$(xcrun --find ar)
LIPO=$(xcrun --find lipo)
RANLIB=$(xcrun --find ranlib)

# Ensure that ar/ranlib creates deterministic archives.
export ZERO_AR_DATE=1

remove_rmeta () {
  local -r bin="$1"

  if $AR t "$bin"| grep "lib\.r"; then
    $AR d "$bin" $($AR t "$bin"| grep "lib\.r")
  fi

  # Do not exit on error.
  set +e

  if $AR t "$bin"| grep "lib\.r"; then
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

framework_to_rewrite="$1"
framework_base=$(basename $framework_to_rewrite)
framework_name=${framework_base%.*}

if [ ! -d "$framework_to_rewrite" ];
then
  >&2 echo "Directory $framework_to_rewrite does not exist"
  exit 1;
fi

for binary in $(find $framework_to_rewrite -type f -name $framework_name);
do
  if $LIPO -info $binary | grep -q x86_64; then
    x86_slice=$(mktemp -d)/$framework_name
    arm_slice=$(mktemp -d)/$framework_name
    $LIPO -thin x86_64 "$binary" -output "$x86_slice"
    $LIPO -thin arm64 "$binary" -output "$arm_slice"

    remove_rmeta "$x86_slice"
    remove_rmeta "$arm_slice"

    $LIPO -create "$x86_slice" "$arm_slice" -output "$binary"
    $RANLIB "$binary"
  else
    remove_rmeta "$binary"
  fi

  # NOTE: Apple broke their bitcode_strip tool and it's trying to open a `strip` file
  touch strip
  xcrun bitcode_strip -r "$binary" -o "$binary"
done
