# Subject: window-focus-loss flush

The SDK flushes when the app's window loses focus, in addition to the backgrounding flush. Read
`lifecycle-investigation.md` for method and `log-signatures.md` for the exact lines.

This subject exists because of a gap in `ON_STOP`: **the app switcher never fires it.** While the
overview is open the carousel renders a live tile of the activity, so the OS still reports it visible
and the process is genuinely not backgrounded. An app swiped away from the overview therefore loses
whatever had not yet reached disk. Window focus *does* drop there, which makes it the only observable
signal for that case.

## What to drive, and what to expect

`reference_event` is **`window focus=false`** for every scenario here — using `ON_STOP` would report
"no reference event" for precisely the cases the feature exists to cover.

| Scenario | Transition | Shape to expect |
|---|---|---|
| `focus-recents` | app switcher | focus drops, **no `ON_STOP` at all** — only the focus flush runs |
| `focus-recents-kill` | app switcher, then process death | focus flush must reach disk and ideally ack before the kill |
| `focus-home` | HOME | focus loss **and** `ON_STOP`, so two platform flushes; downstream debounce absorbs the pair |
| `focus-navigate-activity` | activity transition | app never backgrounds, so no network cutoff either |
| `focus-rotate` | configuration change | **no focus loss on the hardware tested** — see below |
| `focus-permission-dialog` | permission dialog over the app | focus drops; dismissing regains focus and must **not** flush again |

The signal chain in a capture is short: `window <Activity> focus=false` → `state flushing initiated`
within a millisecond or two → the usual stats disk write and upload (see `stats-flush.md`).

## Transitions that do not drop window focus

Two intuitive cases turn out not to produce a focus loss at all on the hardware this was measured on
(a current-generation phone on a recent API level). Both are **platform behaviour, not SDK bugs**, and
both were nearly written up as broken automation:

| Transition | Observed | The check that proved it |
|---|---|---|
| **Rotation** | activity is destroyed and recreated; the new window gains focus without the old one ever reporting a loss | the capture shows the recreation lifecycle events with zero `focus=false` |
| **IME / soft keyboard** | keyboard opens over the activity, focus never drops | `dumpsys input_method \| grep mInputShown` → `mInputShown=true` while the capture has no `focus=false` |

`focus-rotate` is kept as a **regression guard**: `no reference event` is its expected result, and if a
future platform version starts dropping focus on rotation that row stops reading empty. There is no
IME scenario for the same reason there is no rotation flush — nothing to automate — but the test app's
`FocusMatrixActivity` still has the text field, so the probe can be repeated by hand.

**Re-verify both on any new device or API level before repeating the claim.** They are the kind of
behaviour that varies by OEM and platform version, which is exactly why the check command matters more
than the result.

## Traps specific to this subject

- **Do not reuse `ON_STOP` as the reference.** It fires for HOME but not for the app switcher, dialogs
  or activity transitions, so a mixed matrix reported against it looks half-broken.
- **HOME produces two platform flushes, not one.** Both paths coexist by design. A verdict counting
  platform flushes should expect 2 there and 1 for the focus-only cases.
- **The permission-dialog scenario needs its state reset first.** Two denials set `USER_FIXED` and the
  OS then answers instantly with no dialog — no dialog, no focus loss, no flush, and a summary that
  reads exactly like a broken feature. The `revoke-permission` action clears the flags; dismiss with
  BACK rather than *Don't allow* so the run records no decision. Prove repeatability by running it
  twice in a row.
- **A dialog that never appeared and a focus loss that never fired are indistinguishable in the
  capture.** Confirm the UI state changed (`uiautomator dump`) before reporting a negative.
