# Releasing

This document explains the process of releasing new versions within capture-es repo

## Release for React Native

1. Get the latest capture-sdk release version, for example [0.17.11](https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.17.11)
2. Run `scripts/update_rn_capture_version.sh` with the desired version
3. Open and merge PR on [capture-es] (https://github.com/bitdriftlabs/capture-es) repo
4. Run Github action [React Native Release] (https://github.com/bitdriftlabs/capture-es/actions/workflows/react-native-release.yml)
3. Hit `Run Workflow` button on the right.
4. Keep `main` branch selection, enter version that follows formatting rules from [Version Formatting](#version-formatting).
5. The CI job should open a PR named 'Update SDK version to 0.12.1-rc.4' ([example](https://github.com/bitdriftlabs/capture-sdk/pull/1637)).
6. Approve and merge the PR.
7. Update [release docs](https://docs.bitdrift.io/sdk/releases-react-native) to include latest version. 

![](images/react_native_github_action.png)

An example release can be found [here](https://github.com/bitdriftlabs/capture-sdk/releases/tag/v0.12.1-rc.5).

