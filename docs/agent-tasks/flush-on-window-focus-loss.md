# Task: flush the logger when the app window loses focus

A self-contained, replayable brief. Written so the same task can be handed to a different model tier
and the outputs compared. Everything an implementer needs is either here or reachable from here — do
not assume knowledge of the conversation this came from.

## Problem

The SDK's backgrounding flush hangs off `ProcessLifecycleOwner`'s `ON_STOP`. **`ON_STOP` never fires
for the app switcher**: while the overview is open the recents carousel renders a live tile of the
activity, so the OS still reports it visible and the process is genuinely not backgrounded. An app
swiped away from the overview therefore loses whatever had not yet reached disk.

Window focus *does* drop in that case, which makes it the only observable signal.

## Requirements

1. Call `logger.flush(blocking = false)` when the app's window loses focus
   (`onWindowFocusChanged(false)`). Over-calling is acceptable — the work is debounced downstream — so
   firing on more cases than strictly the app switcher is fine.
2. **Implement in the SDK (`platform/jvm/capture`), not in an example app.** It must work for any
   consuming app, including on older Android versions: verify the API level of any platform API
   against `minSdk` rather than assuming.
3. Follow SOLID. The main entry point implements `IEventListenerLogger` and follows the usage patterns
   already in `LoggerImpl`.
4. Add a `gradle-test-app` flow that navigates to a **new Activity**, since an Activity transition is
   one of the focus-loss cases.
5. Take **light** inspiration — only what is strictly necessary — from
   [square/papa `ViewTreeObservers.kt`](https://github.com/square/papa/blob/main/papa/src/main/java/papa/internal/ViewTreeObservers.kt).
6. Expand `T03-recents` into a matrix covering: open recents · recents then force-kill · background ·
   navigate to another Activity · rotate · permission change · IME.
7. Unit tests replicating that matrix are a **later** step. Do not write them now, but the design must
   support them.
8. Stage changes only. Do not commit until the requester has manually reviewed and tested.
9. Ask clarifying questions **before** implementing.
10. Use the `android-cli` skill and `android docs` for platform reference.

## Decisions already taken

Recorded so a replay does not re-litigate them — though a replay is free to disagree explicitly.

| Question | Decision |
|---|---|
| Client-side rate limit? | **No.** Rely on shared-core's existing debounce. Keep trigger and action separated so a policy could be added later. |
| Kill switch | **New `RuntimeFeature`, `defaultValue = true`** — default-on so it is testable on-device with no server config. |
| Matrix automation | Automate the adb-drivable cases (recents, recents+kill, background, navigate, rotate); document IME and permission-change as manual steps. |
| New Activity scope | Minimal and purpose-built: an `EditText` (IME focus loss) and a permission-request button (dialog focus loss). |

### Lint traps hit while doing this

- **Do not add a new permission for the dialog case.** `CAMERA` trips
  `PermissionImpliesUnsupportedChromeOsHardware`, which demands a `<uses-feature>` tag. Request
  `READ_PHONE_STATE`, which the app already declares, so the manifest permission set is unchanged.
  Revoke it first or an already-granted permission returns with no dialog and therefore no focus loss:
  `adb shell pm revoke io.bitdrift.gradletestapp android.permission.READ_PHONE_STATE`
- **The test app enforces `LogNotTimber`** — use `Timber`, not `android.util.Log`.
- **`setText`/`setHint` literals** trip `SetTextI18n` / `HardcodedText`; put them in `strings.xml`.
- **`values-es` exists**, so a new string raises `MissingTranslation`. Debug-only harness strings take
  `translatable="false"` — the project already does this 9 times.
- Run `:gradle-test-app:lintDebug`, not just `compileDebugKotlin`. Lint failed twice after compile was
  green.

## Key facts to verify, not assume

- `ViewTreeObserver.OnWindowFocusChangeListener` and `add/removeOnWindowFocusChangeListener` are
  **API 18** (`$ANDROID_HOME/platforms/android-*/data/api-versions.xml`, `since="18"`), against
  `minSdk = 23` — so **no version gating is needed**. Confirm this rather than trusting it.
- `Application.ActivityLifecycleCallbacks` has **no** focus callback, and `Activity.onWindowFocusChanged`
  requires subclassing — neither is usable from inside an SDK. The window's `ViewTreeObserver` is.
- The SDK does **not** depend on `curtains`/`papa` (only the gradle-test-app does). `IWindowManager` in
  `platform/jvm/common` already exposes `getAllRootViews()` and `findFirstValidActivity()`, so window
  discovery needs no new dependency. Papa's contribution is the listener *bookkeeping* — hold the
  wrapper so it can be removed, and check `ViewTreeObserver.isAlive` on both add and remove, because a
  dead observer throws on mutation.
- Focus fires on far more than the app switcher: shade, IME, dialogs, rotation, Activity transitions.

## Codebase map

| What | Where |
|---|---|
| `IEventListenerLogger` (`start`/`stop`) | `platform/jvm/capture/.../events/IEventListenerLogger.kt` |
| Closest full example | `.../events/performance/JankStatsMonitor.kt` |
| Simplest example | `.../events/lifecycle/AppLifecycleListenerLogger.kt` |
| Idempotent decorator | `.../events/SafeEventListenerLogger.kt` |
| Wiring site | `LoggerImpl` — `eventsListenerTarget.add(...)`; see `addJankStatsMonitorTarget` for the `Application` guard |
| Feature flags | `platform/jvm/common/.../common/Runtime.kt` — `client_feature.android.<thing>` with `defaultValue` |
| Flush entry point | `IInternalLogger.flush(blocking: Boolean)` |
| Test-app focus logging | `.../diagnostics/lifecycle/LifecycleEventLogger.kt` — reuse it, do not re-log |

**Conventions that matter:** re-read the runtime flag inside each callback (not at `start()`) so a kill
switch works without a restart; register/unregister platform listeners inside `mainThreadHandler.run {}`;
constructor-inject every collaborator with a default so the future unit tests are possible.

## Verification

```bash
./platform/jvm/gradlew -p platform/jvm :capture:compileDebugKotlin       # detekt runs here too
./platform/jvm/gradlew -p platform/jvm :gradle-test-app:compileDebugKotlin
```

Adding a `NavigationAction` requires branches in **both** `FirstFragment` (which navigates) and
`MainViewModel` (whose `when` must stay exhaustive) — the second is easy to miss.

On-device, the harness for driving and parsing this lives in `.claude/skills/android-launch-run/`:
scenarios under `assets/scenarios/`, `sweep.py` to run them all, `parse_logs.py` to read a capture.
A focus-loss flush should appear as `window … focus=false` followed by
`bd_logger::logger: state flushing initiated`.

## Definition of done

- Focus-loss flush implemented in `capture`, gated, wired via `LoggerImpl`.
- New Activity reachable from the test app's navigation UI, exercising IME and permission-dialog focus loss.
- Scenarios for the adb-drivable matrix rows; IME/permission documented as manual.
- Both modules compile; detekt clean.
- Changes **staged, not committed**.
