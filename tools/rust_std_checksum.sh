#!/bin/sh

if [ -z "$1" ]; then
    echo "Usage: $0 <rust version>"
    exit 1
fi

VERSION=$1
AWK_FORMAT='{print "        \""$2"\": \""$1"\","}'
SED_REPLACE="s/$VERSION/\\\" + RUST_VERSION + \\\"/"

for arch in aarch64-apple-ios-sim aarch64-apple-ios x86_64-apple-ios aarch64-linux-android armv7-linux-androideabi i686-linux-android; do
  curl -Ls https://github.com/bitdriftlabs/rust-std-mobile/releases/download/${VERSION}/rust-std-${VERSION}-${arch}.tar.gz.sha256 | awk "$AWK_FORMAT" | sed -e "$SED_REPLACE"
done

# Now fetch the shas from the official releases
for tool in rustc cargo llvm-tools rust-std clippy rustfmt; do
  curl -Ls https://static.rust-lang.org/dist/${tool}-${VERSION}-aarch64-apple-darwin.tar.gz.sha256 | awk "$AWK_FORMAT" | sed -e "$SED_REPLACE"
done
