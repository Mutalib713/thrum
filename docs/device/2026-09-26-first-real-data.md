# First real device data — 2026-09-26

The Pixel 6 Pro was reachable for a short window today. This is everything it
gave up, because it is the first real-world evidence this project has had and it
should not live only in a chat log.

**Nothing was installed and nothing was lost.** See the end.

## What was on the phone

Pixel 6 Pro (`raven`), serial `1A131FDEE006MD`. The **debug** build — its package
flags include `DEBUGGABLE` — last updated 2026-09-21 01:02:52, with data written
as recently as 2026-09-26 12:16. So this is a build someone has actually been
using, not a stale install.

`thrum.xml` was copied off before anything else was attempted, 11,341 bytes. It
is kept **outside the repo**, because it contains the armed track's `content://`
URI. Do not commit it.

## The settings it was armed with

| dial | value |
|---|---|
| body | **400** |
| punch | 208 |
| distance | 61 |
| texture | 0 |
| fire_in_ring_mode | true |

**Body is already at 400 — the maximum.** That is the setting this whole project
has been waiting on a hand to judge, and it turns out it has been armed that way
all along. The standing gate is not "set Body to 400"; it is "feel Body at 400
and say whether the phone moves on a table".

Armed track: `AIZO__but_it_s_lofi_hiphop____Jujutsu_Kaisen(256k).mp3`.

## The armed score, and what it says about the opening-hit debt

45.0 s, 2,250 steps at 20 ms, peak 255, 67.2 % non-zero, 79 runs.

```
opening run      520 ms
median later run 400 ms
longest later   1540 ms
shortest          60 ms
```

**This partly deflates the opening-hit debt.** The opening run is 30 % longer
than the median — the defect is real and present on real audio. But it is
nowhere near the longest run on the track: a later run reaches **1540 ms**, three
times the opening. On this track the opening is not an outlier.

So the synthetic reproduction (480 ms against a typical 180 ms) overstates how
visible this is in practice. The `@Ignore`d test still describes a real defect
and the fix is still blocked on re-tuning `DETAIL_GATE`/`DETAIL_CURVE` — but this
is a "the first beat is a bit long" bug, not a "the first thing you feel is
wrong" bug, and the priority should be set accordingly.

The first 60 amplitudes, for anyone who wants the shape:

```
0 0 134 216 230 230 226 225 221 218 217 217 218 212 219 209 208 208 208
208 208 208 208 136 135 135 135 131 0 0 0 135 131 130 131 130 132 134 130 0
```

The opening is a rise, a long plateau at ~208–230 for 19 steps, then a short
decay — a sustained note, not a beat that ran long. That is worth remembering
before "fixing" it.

## The event log — this is Task 10 evidence

40 events spanning **27.2 hours** (2026-09-25 09:04:47 → 2026-09-26 12:16:28).

```
FIRED      14
STOPPED    14
SKIPPED     7   (duplicate notification, already playing)
LISTENER    5   (connected)
```

Latency, notification → vibration, over the 14 firings:

```
n=14   min 229   p50 360   p90 622   max 640 ms
```

**The app works on real calls.** 13 of the 14 firings were in vibrate mode, one
in ring mode — which is why `fire_in_ring_mode` exists. Every one succeeded.

**The 5.3-second delivery outlier did not recur.** Across 14 real firings the
worst case was 640 ms. That is the strongest evidence yet that the 5,324 ms event
recorded in `PLAN.md` was a one-off — most plausibly the first firing after the
listener had been re-bound — rather than a systemic delay. It moves from
"suspicious" to "probably transient", and one more soak should settle it.

### Two things worth a second look

**The listener is being killed and re-bound.** Five `LISTENER connected` events,
three of them inside 13 minutes on 2026-09-25 (21:45:59, 21:49:29, 21:58:40).
Thrum is **not** exempt from battery optimisation — `dumpsys deviceidle
whitelist` does not list it.

The system re-binds a notification listener when a notification arrives, so calls
still fire. But a re-bind at the moment of a call *is* latency, and it is the
best available explanation for the 640 ms tail against a 229 ms best case. This
is the concrete thing the next soak should measure: latency after a known
re-bind versus latency on a warm listener.

**Two firings stopped within 200 ms** — 54 ms and 9 ms after firing, both noting
"call notification removed". Those read as calls that ended instantly rather than
a Thrum fault, but 9 ms is fast enough to be worth confirming against a call that
is known to have rung normally.

## Install status — nothing changed

`lastUpdateTime` is still **2026-09-21 01:02:52** and `thrum.xml` is unchanged at
11,341 bytes. No install succeeded, so no destructive step was ever taken.

The installs failed because the USB link is unstable, not because of the APK:

- `adb devices` alternates between `device`, `offline`, and nothing at all.
- The adb daemon does not survive between commands, so every command forces a
  fresh handshake with the phone.
- `adb push` of the 27 MB debug APK transferred all 26,972,600 bytes and then
  died reading the response (`failed to read copy response: EOF`).
- `adb install` fails with an **empty** error string, and the device drops to
  `offline` immediately afterwards.
- Rebuilding at **2 MB** did not help, so it is not a size or bandwidth problem.

Before any of that, though: the phone was reachable long enough to read
`shared_prefs`, `dumpsys package`, and the package list. So it is not a driver or
authorisation problem either — it is the link dropping under sustained use.

### To unblock it

1. **A different cable.** A charge-only cable behaves exactly like this. Use the
   one the Pixel shipped with.
2. **A different port**, directly on the machine rather than through a hub.
3. **Keep the phone awake and unlocked** during the install.
4. **Or skip USB.** Developer options → Wireless debugging → *Pair device with
   pairing code*. That bypasses the cable entirely and is the more reliable path.

### A note on the two APKs

The release build is signed `CN=Thrum, O=Mutalib Osman, C=GH`
(`011c0805def3c2e2e18880df28edbd52de32ac9213d21a920901af925cc5f417`); the debug
build is `CN=Android Debug`. They cannot coexist — installing release over debug
requires an uninstall, which wipes the armed song, the dials, and the event log.

A third option was built and is sitting in the session workspace, not the repo:
`thrum-2mb-debugkey.apk`, a release-optimised 2 MB APK signed with the **debug**
key. It installs over the existing debug build with data intact, because the
signature matches. It is a supported path — it is exactly what
`app/build.gradle.kts` falls back to when `keystore.properties` is absent — and
it has the side benefit of exercising R8 on the device. It is not debuggable, so
`run-as` stops working once it is installed.

`keystore.properties` was moved aside to build it and **has been restored**;
`app-release.apk` has been restored to the properly-signed artifact. `git status`
is clean.
