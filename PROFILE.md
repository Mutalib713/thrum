# Thrum — canonical spec

> This file is the constitution. Every session reads it FIRST, before touching code.
> Sacred Rules (§6) change only with Mutalib's explicit approval.
> Name "Thrum" is provisional and may be changed before launch; nothing else here is casual.

Started 2026-07-26. Owner: Mutalib (Mutalib713).

---

## 1. WHAT

Thrum makes an Android phone's vibration follow the rhythm of your own ringtone instead of buzzing a flat, meaningless pattern — the thing iPhones do, which Android leaves switched off.

You pick any audio file already on your phone. Thrum listens to it, works out where the beats and the weight are, and saves that as a vibration score. When a call comes in while your phone is on vibrate, Thrum plays that score through the vibration motor. Your pocket feels the shape of your ringtone. No sound comes out.

## 2. WHO

**Primary user:** an owner of a phone with a good vibration motor (Pixel, Samsung flagship, and similar) who keeps their phone on vibrate most of the time and is annoyed that vibrate mode feels identical no matter which ringtone they chose.

**Reached via:** Google Play Store, strangers on the internet. Explicitly NOT a Ghana-first product — see §10, most phones here physically cannot do this.

**What they do today instead:** nothing. There is no alternative. The options available now are:

| Existing option | Why it doesn't solve this |
|---|---|
| Stock Android vibrate mode | Fixed buzz pattern, ignores your ringtone entirely |
| Pixel/Google Sounds app | Ships 12 preset vibration patterns (incl. one named "Synchronized") — still unreleased, and presets are not *your* audio |
| `@pixelhapticbot` (Telegram) | Converts MP3 → haptic OGG off-device. Works for ring mode only, requires Telegram, no preview, no app |
| Hapticlabs Studio | Professional desktop authoring tool, paid, aimed at haptic designers not phone owners |
| Manual Audacity method | Multi-step, requires understanding channel layouts and metadata tags |

None of them fix **vibrate mode**, which is the case that matters. That is the gap.

## 3. SUCCESS METRIC

**One number:** it works correctly on Mutalib's Pixel 6 Pro plus **at least 3 other real phone models** belonging to real people.

Secondary, post-launch: Play Store reviews from strangers saying the vibration feels right. Not download count — a thousand installs on incapable phones is worse than fifty on capable ones.

That primary metric doubles as the device test matrix, so it does two jobs.

## 4. V1 SCOPE

1. **Capability check on first launch.** Detect whether this phone can actually do rich vibration. If not, say so plainly and honestly before the user invests any time. (See §6, Sacred Rule 2.)
2. **Pick an audio file** already on the phone (device audio picker; includes stock ringtones, downloaded MP3s, WhatsApp audio).
3. **Analyse it into a vibration score** — an amplitude-over-time envelope derived from the low-frequency energy of the track, because bass and drums are what a hand can actually feel.
4. **Preview.** Play the audio and the vibration together so the user can feel it before committing. This is the core "does this feel right" loop.
5. **Arm it.** Store the score as the active one. On an incoming call while the phone is on vibrate, play the score through the motor.
6. **Setup guidance screen.** Plainly walk the user through the one or two system settings needed for this to work without fighting the system's own buzz (determined empirically in Task 1 — see §11).
7. **One screen.** Everything above lives on a single screen plus the capability/setup screens. Material You, follows system light/dark.

## 5. NOT IN V1

Explicit exclusions. No session builds these "helpfully":

- **No OGG haptic-channel encoder.** That is v2. It is the hardest part of the project and v1 must not depend on it.
- **No ring-mode support.** v1 fixes vibrate mode only. Ring mode already works for stock ringtones and needs the v2 encoder to work for imported files.
- **No per-contact vibration scores.** Mutalib explicitly deferred this ("i will try the who is calling one later"). It is the strongest v2 candidate.
- No notification sounds, no alarms, no messaging apps.
- No music-app haptics of any kind. Android does not permit it — see §6, Sacred Rule 5.
- No accounts, no login, no server, no cloud, no analytics beyond what §3 needs.
- No sharing or downloading of vibration scores between users.
- No library or collection of saved scores. One active score.
- No custom pattern editor, no drawing your own vibration.
- No widget, no Quick Settings tile.
- No paid tier, no ads, no in-app purchase.

## 6. SACRED RULES

Decisions no future session may reopen without Mutalib saying so:

1. **v1 ships one screen and one active vibration score.** If a feature does not fit that, it is v2.
2. **Never let a user believe the app works when their hardware cannot do it.** The capability check runs before anything else and its verdict is honest, not hedged. A one-star review saying "does nothing" is worse than a user who never installs.
3. **Everything runs on the phone. No server, ever.** No audio, no score, and no file leaves the device. This keeps running costs at zero regardless of user count, and means there is no privacy story to get wrong.
4. **Never ship, host, or redistribute audio.** The app converts files that are already on the user's device. Stock ringtones are Google's property; converting one locally for personal use is fine, distributing it is how apps get pulled.
5. **Never claim or attempt system-wide audio haptics.** Android gives no app access to another app's audio. Spotify, YouTube Music, WhatsApp calls and TikTok are permanently out of reach. Do not design around a workaround for this; there isn't one.
6. **Prove it on hardware before building around it.** Every assumption about how the system ringer behaves gets tested on a real phone with a real incoming call. Emulators cannot test vibration and this machine has none anyway.
7. **The feature flag is not a strategy.** `enableRingtoneHapticsCustomization` may be flippable via adb on Mutalib's own phone, but a Play Store app can never flip it. The app must work with that flag OFF.
8. **Plain-language first, always** — in the app's copy and in every conversation about it. See the user memory `explain-plainly-always`.

## 7. STACK & ARCHITECTURE

Deliberately mirrors `pixel-routines`, because that stack is already proven to build on this machine.

| Piece | Choice | Why |
|---|---|---|
| Language | Kotlin | Proven on this machine |
| UI | Jetpack Compose + Material You dynamic colour | Matches Pixel UI; Mutalib's stated preference; follows system light/dark |
| Build | AGP 9.2.1 **built-in Kotlin** + compose plugin 2.2.20 | ⚠ Do NOT apply `org.jetbrains.kotlin.android` — it collides. See §10 |
| SDK | compileSdk 36, targetSdk 36, **minSdk 31** | API 31 = Android 12, the floor for the haptics work this depends on |
| Storage | SharedPreferences + JSON | No Room in v1. Same call as Pixel Routines; one score does not need a database |
| Audio decode | `MediaExtractor` + `MediaCodec` → raw PCM | Platform APIs, no third-party library, no NDK |
| Analysis | Pure Kotlin DSP (low-pass + envelope follower) | **Pure Kotlin = unit-testable on this PC with no emulator.** This is the main verification lever |
| Vibration out | `VibrationEffect.createWaveform(timings, amplitudes, repeat)` | Amplitude control on an LRA gives real expressiveness; no encoder needed |
| Call detection | `NotificationListenerService` | Sees the Phone app's incoming-call notification. **Avoids `READ_PHONE_STATE` and avoids replacing the dialer**, both of which are Play Store friction. Already proven in `pixel-routines` (`NotifService`) |
| Network | none | Sacred Rule 3 |
| Package | `com.mosman.thrum` | Matches `com.mosman.routines` convention |

### Why there is no background audio player

In vibrate mode the phone produces no sound, so there is nothing to synchronise against. The app only needs to play the right rhythm at the right moment, which the vibrator API does directly. This removes the entire audio-encoding problem from v1.

### Flow

```
first launch → capability check → (incapable? honest dead-end screen)
                    ↓ capable
             pick audio file
                    ↓
        decode to PCM → low-pass → envelope → vibration score
                    ↓
              preview (feel it)
                    ↓
                  arm it
                    ↓
   incoming call + ringer == VIBRATE → play score on vibrator
```

## 8. DATA MODEL

Everything in SharedPreferences. No database.

**`Score`** — the active vibration score. Pure Kotlin, no Android or JSON imports, because it is the only layer that can be tested without a phone (§12).
- `stepMs: Int` — one amplitude covers this many milliseconds. Default 20ms, tune in Task 4
- `amplitudes: List<Int>` — 0–255 per step. 0 is still, 255 is as hard as the motor goes
- `sourceName: String` — display name for the UI
- `sourceUri: String` — the audio file it came from, so a score can be rebuilt later
- `durationMs` and `timings()` are **derived**, not stored

*Two deviations from the first draft of this spec, both deliberate:*

1. **`timings` is derived from a uniform `stepMs` rather than stored.** A `timings`/`amplitudes` length mismatch throws inside the vibrator and takes the app down. Deriving one from the other makes that class of bug impossible instead of merely tested for.
2. **Serialized as a compact single line, not JSON** — `1|stepMs|escapedName|amp,amp,amp`. A score is a few thousand small integers, so JSON's overhead buys nothing, and the `org.json` available inside Android unit tests is a stub that throws on every call. Hand-rolling it keeps the whole score layer testable on the PC. `decode()` returns null rather than throwing, so corrupt stored data degrades to "no score" instead of a crash loop.

**`Settings`**
- `armed: Boolean`
- `onboarded: Boolean`
- `capabilityVerdict: String` — cached result of the hardware check
- `repeatScore: Boolean` — loop the score for the length of the call

## 9. INTEGRATIONS & KEYS

**None. Zero.** No API keys, no env vars, no secrets, no `.env`, no network permission in the manifest.

DRY_RUN is not applicable — nothing sends a message and nothing spends money. The equivalent safety rule for this project:

> **The app never changes a system setting on the user's behalf.** Where a system setting must change, the app explains why in plain language and sends the user to the settings screen to do it themselves. Silently editing someone's phone settings is how an app becomes a thing people uninstall angrily.

Permissions v1 will request: notification listener access (with prominent in-app disclosure of why), audio file read via the system picker (no blanket storage permission), `VIBRATE`.

## 10. CONSTRAINTS

**Build machine** (see memory `mutal-machine-build-env`):
- Weak Windows 10 PC. **Android Studio crashes — never launch it. No emulator. Command-line Gradle only.**
- Avast MITMs HTTPS → needs the JKS truststore approach from `pixel-routines`.
- Avast also locks Gradle transform outputs mid-build → build with `--max-workers=1`, or just re-run.
- Builds take ~4 minutes. Budget accordingly; do not plan a task that needs ten build cycles.
- ~10 GB free disk.

**Testing:**
- Vibration cannot be tested on an emulator, and there is no emulator anyway. Every haptic claim needs Mutalib's physical Pixel 6 Pro.
- Mitigation: the analysis layer is pure Kotlin and gets real unit tests that run on the PC in seconds. Only the playback layer needs hardware.

**Hardware reality (the product's ceiling):**
- Needs an actuator with amplitude control. Budget phones (most Tecno, Infinix, itel, low-end Samsung) have a spinning-weight motor that can only buzz. **The app will do nothing useful on those, by physics.**
- This is why the audience is worldwide flagship owners, not Mutalib's local circle.

**Distribution:** Google Play. Notification-listener access requires a clear disclosure in the listing and in-app. Budget for review friction.

## 11. RISKS & OPEN QUESTIONS

**R1 — The vibrate-mode wall. CONFIRMED, not theoretical.** ⚠ Highest risk.

Mutalib tested this on his Pixel 6 Pro on 2026-07-26: stock Pixel ringtones vibrate in sync during a real incoming call **in ring mode**, but in **vibrate mode** the phone falls back to the flat default buzz — even with Google's own ringtone files.

Cause, from AOSP `Ringer.java`:

```java
if (!isHapticOnly) {
    ringtoneInfoSupplier = () -> mRingtoneFactory.getRingtone(
        foregroundCall, mVolumeShaperConfig, finalHapticChannelsMuted);
} else if (Flags.enableRingtoneHapticsCustomization() &&
           mRingtoneVibrationSupported) {
    ringtoneInfoSupplier = () -> mRingtoneFactory.getRingtone(
        foregroundCall, null, false);
}
```

`isHapticOnly` is true when the ringer is inaudible (vibrate mode). With the flag off, the ringtone is never loaded, so there is nothing to read haptic data from, and it falls through to `vibrateIfNeeded()` and the default pattern.

*Resolved by:* Task 1. A rough test build that proves our own vibration score can play on a real incoming call in vibrate mode. **If Task 1 fails, stop and rethink — do not build UI around a hole.**

**R2 — Can the system's own buzz be suppressed?** ⚠ Open, and Task 1 must answer it.

In vibrate mode the system plays its flat pattern. If ours plays on top, it will feel like mush. Candidate answers, in order of preference, to be tested empirically:
- (a) Some combination of "Vibrate first then ring gradually" off / ring-vibration slider / adaptive alert vibration off silences the system's pattern. Untested.
- (b) Instruct the user to use **silent mode** instead of vibrate mode. Silent = no sound and no system vibration, a blank canvas the app fills. Costs the user notification vibration, which must be disclosed honestly.
- (c) `Settings.System.VIBRATE_WHEN_RINGING` — likely irrelevant in vibrate mode, since vibrate mode vibrates by definition. Verify, don't assume.

**R3 — Google ships this themselves.** The `enableRingtoneHapticsCustomization` flag is already present in Android on Mutalib's phone, and the Pixel Sounds app has an unreleased "Synchronized" ringtone vibration pattern. Google is clearly building this. If it ships, Pixel users get something similar for free.
*Mitigation:* not controllable. Samsung and other capable non-Pixel phones remain, and Google's version uses presets rather than the user's own audio. Do not build a twelve-month roadmap on the Pixel audience alone.

**R4 — Wrong-phone reviews.** Users on incapable hardware install, feel nothing, leave one-star reviews.
*Mitigation:* Sacred Rule 2, the honest capability screen, plus an explicit hardware warning in the Play listing.

**R5 — Notification listener reliability.** Android can kill background services. If the listener isn't bound at call time, the vibration doesn't fire.
*Mitigation:* notification listeners are relatively durable and `pixel-routines` already proves the pattern works on this phone. Needs a "does it still fire after 24h idle and after reboot" test before launch.

**R6 — Latency.** The notification may arrive slightly after the call starts, so the vibration could begin late.
*Resolved by:* measure it in Task 1. If it's bad, the score can be started from an offset.

**R7 — Does amplitude-only vibration actually feel good? RESOLVED 2026-07-26.** Yes. Mutalib felt Task 1's three demo patterns on his Pixel 6 Pro and confirmed the rhythm is clearly different from the imitation of Android's flat buzz. The phone also reported `hasVibrator`, `hasAmplitudeControl` and rich primitives all true. Frequency control is not needed for v1.

*Original concern:* the plan uses amplitude steps, not true frequency control, so it might feel like stuttering rather than music.
*Resolved by:* **moved forward to Task 1.** Pressing a button and feeling a hardcoded rhythm next to an imitation of Android's flat buzz answers this as well as a full preview would, and answers it before the audio engine exists. `Demo.kt` exists for exactly that comparison.

**R8 — Waveform length limits.** ⚠ Open. `VibrationEffect.createWaveform` may cap how many steps a single effect can hold. A 30-second ringtone at 20ms per step is 1,500 steps, which could be refused or silently truncated — and truncation would present as a bug that only shows up on long tracks.
*Resolved by:* Task 3 or 4. Play a deliberately long score on the phone and find the real limit rather than guessing a safe number. If there is a cap, either widen `stepMs` for long tracks or play the score in chunks.

**Open question — the name.** "Thrum" is provisional. Decide before the Play Store listing exists.

## 12. VERIFICATION

**`check.ps1`** at repo root. Must pass before any commit that touches code:
1. Assemble debug (`--max-workers=1`, no daemon).
2. Run unit tests, including the QA suite.
3. Fail loudly on any warning that indicates the AGP 9 Kotlin-plugin collision (see §10).

**QA suite:** `app/src/test/java/com/mosman/thrum/QaSuiteTest.kt` — pure-JVM tests, no device, runs in seconds on this PC. Starts small and only grows. Initial checks:
1. A silent audio buffer produces an all-zero score.
2. A steady 4-on-the-floor beat produces exactly 4 amplitude peaks per bar.
3. `timings` and `amplitudes` are always the same length (a mismatch throws at the vibrator and crashes the app).
4. Amplitudes are always within 0–255.
5. A score round-trips through JSON unchanged.

**Before any release build:** the capability check must be verified against at least one incapable device, not just Mutalib's Pixel. Borrow a budget phone.

**Evidence rule:** no task is done on a claim. Test output, a measurement, or Mutalib's own hand on the phone. Screenshots are unreliable on this machine — prefer numbers and logs.
