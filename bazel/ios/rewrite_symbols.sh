#!/bin/bash

set -euxo pipefail

AR=$(which llvm-ar)
RANLIB=$(which llvm-ranlib)
LIPO=$(which llvm-lipo)

# Ensure that ar/ranlib creates deterministic archives.
export ZERO_AR_DATE=1

remove_rmeta () {
  local -r bin="$1"

  # Remove lib.rmeta files one at a time to avoid command line length limits
  # Use a counter to prevent infinite loops
  local max_iterations=10000
  local iteration=0
  
  while [ $iteration -lt $max_iterations ] && $AR t "$bin" 2>/dev/null | grep -q "lib\.r"; do
    local rmeta_file=$($AR t "$bin" 2>/dev/null | grep "lib\.r" | head -1)
    if [ -n "$rmeta_file" ]; then
      $AR dD "$bin" "$rmeta_file" 2>/dev/null || break
    else
      break
    fi
    iteration=$((iteration + 1))
  done

  # Do not exit on error.
  set +e

  if $AR t "$bin" 2>/dev/null | grep -q "lib\.r"; then
    # Method succeeded which means that it found
    # one of the unexpected object references which means that
    # we were not able to remove all of the revevant `lib.rmeta`
    # file references and should exit with error.
    echo "failed to remove 'lib.rmeta'-like reference(s) from '$bin'" >&2
    exit 1;
  fi

  # Revert back to previous behavior: exit on error.
  set -e

  $RANLIB -D "$bin"
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
  # NOTE: Apple broke their bitcode_strip tool and it's trying to open a `strip` file
  touch strip
  xcrun bitcode_strip -r "$binary" -o "$binary" > /dev/null 2>&1

  if file -b -- "$binary" | grep -q 'x86_64'; then
    x86_slice=$(mktemp -d)/$framework_name
    arm_slice=$(mktemp -d)/$framework_name
    $LIPO -thin x86_64 "$binary" -output "$x86_slice"
    $LIPO -thin arm64 "$binary" -output "$arm_slice"

    remove_rmeta "$x86_slice"
    remove_rmeta "$arm_slice"

    $LIPO -create "$x86_slice" "$arm_slice" -output "$binary"
  else
    remove_rmeta "$binary"
  fi
done
