# React Native SDK workspace

## Setup

From the repository root, enter the Nx workspace and install dependencies with the Node version in `.nvmrc`.

```sh
cd platform/es
npm ci
```

## React Native

The React Native library needs to run in the context of an app, so use the `expo` example app
to build and develop the plugin. By opening the `expo` app in XCode or Android Studio, the
plugin files can be edited directly for a smoother development experience.

## Example Apps

To run an example app, refer to the READMEs in `examples/`.
