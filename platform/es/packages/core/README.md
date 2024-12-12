# @bitdrift/core

## Overview

This is the package containing the core bitdrift engine, written in rust and shared across all
platforms.

## Usage

This package is not intended to be used directly, but rather as a dependency of the various
platform-specific packages, for example `@bitdrift/electron`.

## Multi Architecture Support

This package is built for the following architectures:

- aarch64-apple-darwin
- x86_64-apple-darwin
- x86_64-pc-windows-msvc
- i686-pc-windows-msvc
- x86_64-unknown-linux-gnu

We publish all architectures to the npm registry independently (e.g. `@bitdrift/core-darwin-x64`)
and the main `@bitdrift/core` package will automatically install the correct one for your platform.
It does it throught a `optionalDependencies` field in the `package.json`.

