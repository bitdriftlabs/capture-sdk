# Refactor WindowFocusListenerLogger for Simplicity and SOLID (Focused)

Refactor `WindowFocusListenerLogger` to improve its maintainability and simplicity by removing manual state tracking and improving interface segregation.

## User Review Required

> [!NOTE]
> The refactor eliminates the `WeakHashMap<Activity, WeakReference<View>>` in favor of self-cleaning `View.OnAttachStateChangeListener`s. This simplifies state management and reduces the risk of memory leaks or stale entries.

## Proposed Changes

### [Component Name] capture-events-lifecycle

#### [MODIFY] [WindowFocusListenerLogger.kt](file:///Users/miguel/src/github/bitdriftlabs/capture-sdk/platform/jvm/capture/src/main/kotlin/io/bitdrift/capture/events/lifecycle/WindowFocusListenerLogger.kt)

- **Remove `trackedRootViews`**: Use `OnAttachStateChangeListener` to remove focus listeners when views are detached, making the activity map unnecessary.
- **Interface Segregation**: Implement `Application.ActivityLifecycleCallbacks` using a private internal class or object. This keeps the public interface of `WindowFocusListenerLogger` focused only on its primary purpose.
- **SOLID - S**: The class will now focus on "attaching focus listeners to windows" and "executing the flush action", without the burden of manual activity-to-view mapping.

## Verification Plan

### Automated Tests
- Create `WindowFocusListenerLoggerTest.kt` to verify:
    - Listener registration on activity start.
    - Focus loss triggers flush.
    - Feature flag disables flushing.
- Verify that listeners are correctly added and removed without the map (via mocking/spying on ViewTreeObserver if possible, or observing behavior).

### Manual Verification
- Deploy the test app and verify that "window focus lost" flushes still occur.
