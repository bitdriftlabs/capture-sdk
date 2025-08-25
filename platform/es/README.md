# capture-es

**capture-es:** bitdrift SDK integration

## Setup

To install dependencies, run

```sh
$ npm ci
```

## Electron

The electron library consists of two parts

- `@bitdrift/core` - the native bindings that allow calling into the Rust shared code.
- `@bitdrift/electron` - the top level library providing a JS API for `@bitdrift/core`.

The indirection allows us to have users install `@bitdrift/electron` and download the
architecture appropriate version of `@bitdrift/core`.

### Useful commands

Builds the electron native library in debug mode:

`nx build @bitdrift/core`

Build the electron native library in release mode, suitable for distribution:

`nx build @bitdrift/core -c release`

Runs the electron native library test suite:

`nx test @bitdrift/core`

Runs the electron JS library test suite:

`nx test @bitdrift/electron`

Most of the development of the electron logger tends to be done via the example app which can
be used to interact with the library.

## React Native

The React Native library needs to run in the context of an app, so use the `expo` example app
to build and develop the plugin. By opening the `expo` app in XCode or Android Studio, the
plugin files can be edited directly for a smoother development experience.

## Example Apps

To run either of the example apps, refer to the READMEs in `examples/`.
