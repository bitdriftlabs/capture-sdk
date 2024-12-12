# capture-es

**capture-es:** bitdrift SDK integration

## Setup

To install dependencies, run

```sh
$ npm ci
```

## Available Scripts

Builds the electron library in debug mode:

### `nx build electron`

Buld the electron library in release mode, suitable for distribution:

### `nx build electron -c release`

Runs the test suite:

### `nx test electron`

## Exploring capture-es

After building capture-es, you can explore its exports at the Node REPL:

```sh
$ nx build electron
$ node
> require('./dist/native.node').init("<api key>")
```

## Distribution

To distribute the electron library, run:

```sh
nx build electron -c release
npm pack ./dist/electron
```

This produces a .tgz in the project root that can be distributed and installed with npm.

