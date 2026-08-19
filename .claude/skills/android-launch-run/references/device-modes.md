# Device modes

Nine states, each with setup, teardown, and a **verify** command. Several fail silently, so always
verify rather than assuming the setup command worked.

All commands verified on API 36 (emulator) and API 37 (Pixel 10).

## When each mode can be applied

This ordering is not cosmetic — getting it wrong produces clean-looking runs that measured nothing.

| Mode | Apply before launch? | Evidence |
|---|---|---|
| `airplane` | ✅ yes | global, survives launch |
| `battery-saver` | ✅ yes | after launch: `low_power=1`, `Restrict power: true` |
| `data-saver` | ✅ yes | global + per-app policy both persist |
| `bg-restricted` | ✅ yes | appop `RUN_ANY_IN_BACKGROUND: ignore` survives launch |
| `idle-allowlist` | ✅ yes | persists |
| **`standby-restricted`** | ❌ **no — launch resets it** | set to `restricted` (45), reads **10 (ACTIVE)** after launch |
| **`doze-deep` / `doze-light`** | ⚠️ survives launch, but changes the scenario | see below |
| **`freezer`** | ❌ no | needs an already-running cached process |

### standby-restricted is the trap

Launching the app counts as user interaction and promotes it to the ACTIVE bucket, silently undoing
the setup. It must be applied **after** backgrounding, which means it lands ~0.3s into whatever the
app is doing — so it races any in-flight work rather than being cleanly in place.

### doze changes the scenario if applied early

`force-idle deep` then `am start` leaves the device IDLE with the screen asleep — launching does not
break doze. But the app then sits in `procState=TPSL` (TOP_SLEEPING) with
`effective=DOZE|APP_BACKGROUND`: **network already blocked before it runs**. Any foreground wait is
impossible, and you're measuring "launched into a dozing device" rather than "user backgrounded the
app, then the device dozed."

Prefer the realistic order: pre-arm everything (unplug, un-allowlist, `enable deep`) before launch,
then after backgrounding issue only `force-idle deep`, so it lands ~0.3s after `ON_STOP` instead of
~2s. Like `standby-restricted`, it still races in-flight work; there is no way around that with
adb-forced idle.

---

## The modes

### airplane

```bash
adb shell cmd connectivity airplane-mode enable      # on
adb shell cmd connectivity airplane-mode disable     # off
adb shell cmd connectivity airplane-mode             # verify -> enabled|disabled
```

Cross-check with `settings get global airplane_mode_on` (1/0) and `cmd wifi status`. On a real
phone this drops calls and messages — restore it promptly.

### battery-saver

```bash
adb shell dumpsys battery unplug                                    # required: won't engage while charging
adb shell dumpsys battery set level 15
adb shell cmd power set-adaptive-power-saver-enabled false
adb shell settings put global low_power 1
# off:
adb shell settings put global low_power 0 && adb shell dumpsys battery reset
# verify:
adb shell settings get global low_power                             # -> 1
adb shell dumpsys netpolicy | grep 'Restrict power'                 # -> true
```

`dumpsys battery reset` is what undoes the simulated unplug — without it the device believes it is
on battery indefinitely.

### doze-deep / doze-light

```bash
# pre-arm before launch:
adb shell dumpsys battery unplug
adb shell dumpsys deviceidle whitelist -<pkg>
adb shell dumpsys deviceidle enable deep      # or: light
# after backgrounding, with the screen off:
adb shell input keyevent KEYCODE_SLEEP
adb shell dumpsys deviceidle force-idle deep  # or: light
# off:
adb shell dumpsys deviceidle unforce && adb shell dumpsys deviceidle enable all
# verify:
adb shell dumpsys deviceidle get deep         # -> IDLE (ACTIVE means not dozing)
adb shell dumpsys deviceidle get light
```

Requires the screen off. The app must not be on the idle allowlist. Check `effective=` in
`dumpsys netpolicy` for the `DOZE` reason to confirm it actually reached the app.

**With the pre-arm order there is no race at all** — better than previously documented. Measured on
a Pixel 10 and an emulator: `force-idle deep` lands **~1.1–1.2s *before* `process ON_STOP`**, because
`ProcessLifecycleOwner` debounces the stop by ~1.3s. So doze is already in force when the flush runs,
the app's state goes `APP_BACKGROUND` → `DOZE|APP_BACKGROUND`, and the upload is never acked. Pair it
with a screen-off-*without*-doze control (which acks in ~700ms) and the result is attributable to
doze rather than to screen-off or to the background firewall.

⚠ **A restriction applied after the flush has already run tests nothing.** Backgrounding alone kills
network within ~5s and freezes the process within ~70s. Force doze a minute later and you will
correctly observe "no uploads" while having isolated nothing — everything was already dead. A real
run did exactly this: doze forced ~95s after HOME, with the one background flush already *acked*
before doze existed, then concluded doze was not the cause. The experiment could not support either
verdict. If the question is "does <restriction> affect the backgrounding flush?", the restriction
must be armed **before `ON_STOP`**, which for doze means the pre-arm order above.

### data-saver

```bash
# mark the network metered FIRST or this is a silent no-op:
for n in $(adb shell cmd netpolicy list wifi-networks | cut -d';' -f1); do
  adb shell cmd netpolicy set metered-network "$n" true
done
adb shell cmd netpolicy set restrict-background true
# off — all THREE, see below:
adb shell cmd netpolicy set restrict-background false
for n in ...; do adb shell cmd netpolicy set metered-network "$n" undefined; done
adb shell cmd netpolicy remove restrict-background-blacklist <uid>
# verify:
adb shell cmd netpolicy get restrict-background        # -> enabled
adb shell cmd netpolicy list wifi-networks             # the active SSID should read 'true'
adb shell dumpsys netpolicy | grep 'UID=<uid> policy'  # -> nothing, once cleared
```

⚠ **Data Saver is three independent switches, and the third one leaks.** Beyond the global toggle
and the metered-network marks there is a **per-uid** policy, `REJECT_METERED_BACKGROUND`, which
survives both of the others. Left behind it shows up as `METERED_USER_RESTRICTED` in the app's
`effective=` blocked state and silently restricts every later run — including runs of unrelated
scenarios, on someone's real phone.

This actually happened: a sweep left `UID=10304 policy=1 (REJECT_METERED_BACKGROUND)` on a Pixel 10,
and `adbctl.py mode reset` could not clear it because reset only handled the global toggle and the
network marks. `mode data-saver --off` (and therefore `mode reset`) now removes the blacklist entry
too, and `verify data-saver` reports all three switches so a leftover is visible instead of silent:

```
Restrict background status: disabled | metered=0 | uid_policy=none
```

`state_line` (`adbctl.py state`) is what surfaced it — the `blocked=` field carries the effective
reasons, so **check `adbctl.py state` after any sweep**, not just the per-mode verify.

**Data Saver only restricts metered networks.** On unmetered WiFi — the default for both emulators
and most home networks — it does nothing at all unless the network is marked metered first.

Also check for a pre-existing per-app policy: `dumpsys netpolicy | grep 'UID=<uid> policy'`.
`policy=1 (REJECT_METERED_BACKGROUND)` means the user disabled background data for that app in
Settings, which will confound the result. Either clear it or record it.

### standby-restricted

```bash
# AFTER backgrounding (see above):
adb shell am set-standby-bucket <pkg> restricted
# off:
adb shell am set-standby-bucket <pkg> active
# verify:
adb shell am get-standby-bucket <pkg>     # -> 45
```

Buckets: 10 ACTIVE · 20 WORKING_SET · 30 FREQUENT · 40 RARE · 45 RESTRICTED. The OS decays buckets
naturally over time, so a value of 20–40 on an idle app is normal rather than a leftover.

### bg-restricted

```bash
adb shell am set-bg-restriction-level <pkg> background_restricted
adb shell cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore
# off:
adb shell am set-bg-restriction-level <pkg> unrestricted
adb shell cmd appops set <pkg> RUN_ANY_IN_BACKGROUND allow
# verify:
adb shell cmd appops get <pkg> RUN_ANY_IN_BACKGROUND   # -> ignore
```

⚠ `am get-bg-restriction-level` returns `unknown` on API 36/37 and is **not** a usable check — use
the appop. Note the appop governs background *execution*, not network directly, so a run where the
upload still succeeds does not prove background-restricted apps can reach the network.

### freezer

```bash
# after backgrounding, ~10s later (matching the OS's own behaviour):
adb shell am freeze --sticky <pkg>
# off:
adb shell am unfreeze --sticky <pkg>
# verify:
adb logcat -b events | grep am_freeze
adb shell dumpsys activity processes | grep 'frozen=true'
```

API 34+ SIGSTOPs cached processes ~10s after backgrounding on its own, so this may fire without
being asked. The signature of a frozen process is that periodic work simply **stops** — timed
flushes vanish from the log.

### idle-allowlist

```bash
adb shell dumpsys deviceidle whitelist +<pkg>     # exempt
adb shell dumpsys deviceidle whitelist -<pkg>     # remove
# verify:
adb shell dumpsys deviceidle whitelist | grep <pkg>
```

Primarily a **control**: an allowlisted app is exempt from the background firewall chain, so it
keeps network while backgrounded. Useful for proving that a network failure is caused by the OS
rather than the app — with the exemption on, a backgrounded app held its stream for 45s with no
teardown, versus a cutoff at ~5s without it.

---

## The default background network cutoff

Worth knowing even when applying no modes at all: on API 35+ the **background firewall chain**
(`FIREWALL_CHAIN_BACKGROUND`) revokes a cached app's network ~**5s** after it backgrounds. It is
enabled by default — `dumpsys netpolicy` shows
`com.android.server.net.use_metered_firewall_chains: true` — and is *not* doze, battery saver, or
Data Saver.

Measured, both devices, with no modes applied:

| Hop | Emulator (API 36) | Pixel 10 (API 37) |
|---|---|---|
| `ON_STOP` → firewall allow revoked | +4.962s | +4.950s |
| revoke → socket aborted | +13ms | +51ms |

So any backgrounded network work has roughly a **5-second budget**, and `back` shortens it to
~3.8s. Because the block covers DNS, the resulting error is `UnknownHostException`.

---

## Always reset

Leave the device as you found it, especially a physical one. `adbctl.py mode reset` restores all
nine, un-meters every saved WiFi network, resets the simulated battery, and clears
`svc power stayon`.

Two extras that are easy to forget:

- `svc power stayon false` — harnesses often set it `true` to stop the screen sleeping mid-run.
- `debug.bitdrift.internal_rust_log` — persists until reboot. Restore it to whatever the user had,
  or `info`.
