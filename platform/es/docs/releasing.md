# Releasing React Native

The [SDK release workflow](https://github.com/bitdriftlabs/capture-sdk/actions/workflows/update_sdk_version.yaml)
starts the React Native version bump for the `all` and `react-native` release targets.
It checks the native release tag and opens a pull request updating the React Native SDK
dependency pins. After that pull request merges, the next React Native package version
is published to [npm](https://www.npmjs.com/package/@bitdrift/react-native).

For a React Native-only patch against the currently pinned native SDK, run
[React Native Release](https://github.com/bitdriftlabs/capture-sdk/actions/workflows/react-native-release.yml)
from `main` with the desired version. The workflow publishes the package and opens a PR
to record the version in the workspace. Check that the new version appears on npm,
then update the [React Native release notes](https://docs.bitdrift.io/sdk/releases-react-native).

The workflow releases `@bitdrift/react-native` and `@bitdrift/react-native-webview` at the
same version. Configure npm trusted publishing for both packages with the
`bitdriftlabs/capture-sdk` repository and the `react-native-release.yml` workflow.

## Version tracking

From `platform/es`, read the pinned native dependency version with:

```sh
node -p "require('./packages/react-native/package.json').captureSdkVersion"
```
