# Releasing

This document explains the process of releasing new versions of Bitdrift SDK.

## Pre-requisites for Including Recent Changes in [shared-core](https://github.com/bitdriftlabs/shared-core)

If you need to include recents changes added on [shared-core](https://github.com/bitdriftlabs/shared-core), please follow this process:

1. Find the related dep sha for the change and update it on `Cargo.toml`. (Sample [PR](https://github.com/bitdriftlabs/capture-sdk/pull/223) for reference)
2. Run the repin command `CARGO_BAZEL_REPIN=true ./bazelw test --build_tests_only //platform/... *(This will add changes to the Cargo.Bazel.lock/Cargo.lock files)*
3. Open PR and merge it.

___NOTE: If you have build issues on the gradle test app after update, make sure to run locally `rustup update` before re-building___

## Release process

1. Run `Release and Update SDK Version` workflow action: https://github.com/bitdriftlabs/capture-sdk/actions/workflows/update_sdk_version.yaml
2. Keep `main` branch selection, enter version that follows formatting rules from [Version Formatting](#version-formatting).
3. The entire process should create both a release in github: https://github.com/bitdriftlabs/capture-sdk/releases as well as the s3 directories that power our docs ([android](https://docs.bitdrift.io/sdk/releases-android), [ios](https://docs.bitdrift.io/sdk/releases-ios))
4. The CI job should open a PR named 'Update SDK version to x.y.z' ([example](https://github.com/bitdriftlabs/capture-sdk/pull/1637)) you need to approve and auto-merge it.
5. At the end of the job another PR will be atuomatically opened in https://github.com/bitdriftlabs/capture-ios (see [below](#capture-ios))

### capture-ios
1. When approving a release in capture-ios, watch for a [PR being created in capture-ios](https://github.com/bitdriftlabs/capture-ios/pulls) with the new version number and approve it. In order for it to be merged you'll have to manually close it and re-open it.
2. Once the PR merges, follow [the `Release` workflow](https://github.com/bitdriftlabs/capture-ios/actions/workflows/release.yaml) and approve public releases as needed. Note that this step can take a long time to complete.

### bitdrift-docs
This process is not automated yet so you'll need to manually open a PR in https://github.com/bitdriftlabs/bitdrift-docs for the public release notes.

## Version Formatting

Public bitdrift SDK versions should follow the following format:

```
0.{x}.{y}(-rc.{z})?
```

Example 0.1.0.

Please note that while officially our releases follow `0.x.y` format GitHub Releases use their "v" prefixed version (i.e., `v0.x.y`). That's because some tooling
expects this to differentiate other git tags from versioned releases.

Examples of correctly formatted versions:

* 0.1.0
* 0.1.12
* 0.1.12-rc.1

Examples of incorrectly formatted versions:

* v0.1.0
* v0.1.1.0
* 0.1.12-rc.0
* 0.1.12test

### Release Candidates

For increased development velocity and higher quality of stable SDK releases, we allow for the release of RC ("release candidate") versions of the SDK.

The way it works is that for small iterative changes to the SDK (changes we don't want to communicate to our customers as they are too small or not ready) or changes that we want to test more thoroughly before releasing them to a wider audience, we release `0.x.y-rcx` versions of the SDK. Such releases should not be automatically picked up by dependency managers, but their existence will allow us to integrate, test, and monitor them internally.

Multiple release candidates can be released for a given SDK version, for example, `0.1.3-rc.1`, followed by `0.1.3-rc.2`, and so on. Once we reach a point where we want to share a given version with a wider audience, we cut a stable release, for instance, `0.1.3`.

### Test Releases

For increased developer velocity, the regex that CI uses to verify versions is more permissive compared to what we recommend the version string to look like for public releases. For example, a version such as `0.1.3-test.1` is allowed.
