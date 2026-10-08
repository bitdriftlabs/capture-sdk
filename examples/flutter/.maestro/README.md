# Maestro flows

`APP_ID` is `io.bitdrift.flutterCaptureExample` on iOS and `io.bitdrift.flutter_example` on Android.

```bash
maestro test -e APP_ID=<app id> -e API_KEY=<key> -e API_URL=<api url> configure_sdk.yaml
maestro test -e APP_ID=<app id> session_replay.yaml
maestro test -e APP_ID=<app id> dart_error_report.yaml
maestro test -e APP_ID=<app id> native_crash.yaml
```

`configure_sdk.yaml` clears the app state, so run it first. On Android, a local API server needs
`adb reverse tcp:<port> tcp:<port>`.
