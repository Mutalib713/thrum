# Thrum — task plan

Read `PROFILE.md` first. It is canonical; this file only sequences the work.

**Rules for this plan**
- One task = one sitting. If a task needs two, it was written wrong — split it.
- Every task ends with something Mutalib can see or feel, listed under *Proof*.
- ⚠ marks a task that can fail in a way that changes the project.
- Nothing is ticked on a claim. Test output, a logcat measurement, or a hand on the phone.
- The app runs on the real Pixel 6 Pro from **Task 1 onward**. There is no emulator and no other way to verify vibration.

---

## Milestone 0 — Prove it's possible

Nothing else gets built until this milestone passes. If Task 2 fails, stop and rethink rather than building a product around a hole.

### [x] Task 1 — ⚠ Skeleton that builds, installs, and buzzes — **DONE 2026-07-26**

**Result:** passed both halves.

```
BUILD SUCCESSFUL in 9m 8s
com.mosman.thrum.QaSuiteTest: tests=11 failures=0 errors=0 skipped=0 time=0.425s
CHECK PASSED - tests green, APK 24.93 MB
```

Mutalib on his Pixel 6 Pro: *"yes it does feel different"*, and the capability card reported **true for all three** — vibrator, strength control, sharp effects. **R7 is resolved:** amplitude-only vibration reads as rhythm on this hardware, and is clearly distinguishable from the flat buzz `Demo.systemBuzz()` imitates.

Cost more than it should have: the first build ran 3h 9m before failing on a rotated Avast root CA. Fixed properly, with `tools/TlsProbe.java` so it can never cost hours again. See `CLAUDE.md`.

<details><summary>original task description</summary>

An app with one button. Pressing it plays a hardcoded rhythmic vibration — deliberately varied, soft-then-sharp-then-soft, so the difference from the flat system buzz is obvious to the hand.

Also lands in this task: `check.ps1`, a `QaSuiteTest.kt` with one trivial passing test, and `.gitignore`d signing config.

**⚠ Why risky:** getting anything to build on this machine is a real task, not a formality. Avast breaks Gradle mid-build, and AGP 9's built-in Kotlin collides with the plugin every tutorial tells you to apply. Budget the whole sitting for the toolchain.

**Proof:**
- `check.ps1` runs green, output pasted.
- APK installed on the Pixel via `adb install`.
- Mutalib presses the button and confirms it feels varied, not like a flat buzz. **This also answers R7** — if amplitude-only vibration feels like stuttering rather than rhythm, we learn it now, in task 1, not task 5.

</details>

### [x] Task 2 — ⚠⚠ The wall: does it fire on a real incoming call? — **PASSED 2026-08-01**

**The wall is cleared. Milestone 1 is unblocked.**

**13 real incoming calls** over 2026-07-30 → 2026-08-01, on the Pixel 6 Pro (Android 17), read from the app's own event log rather than logcat — the listener records every decision it makes, which survives the log buffer rolling.

| Phone state | Calls | Ours fires? | Latency (median) | Mutalib's verdict |
|---|---|---|---|---|
| Vibrate mode, default settings | 9 | yes, 9/9 | **273 ms** (216–336 steady, 588–713 cold start) | *"a clear rhythm"* |
| Vibrate mode, "vibrate first then ring gradually" OFF | 0 | not tested | — | setting was already off (`apply_ramping_ringer` null) |
| Vibrate mode, adaptive alert vibration OFF | 0 | not tested | — | not varied |
| Vibrate mode, ring-vibration slider reset | 0 | not tested | — | not varied; stayed at 3/3 |
| **Silent mode** | 2 | **no — Android discards it** | n/a | *"the silent mode is just silent, nothing else"* |
| Ring mode | 5 | correctly skipped, 5/5 | — | `fireInRingMode` left off, as designed |

Rows 2–4 stayed blank on purpose. They existed to answer R2, and R2 was answered by hand in row 1: `vibrate_when_ringing` was **on** for every one of those nine calls, so the system's flat buzz should have been competing, and what Mutalib felt was a clean rhythm. Leaving them blank is honest; marking them tested would not be.

**R1 — passed.** Fired on 13 of 13 calls. Never once missed.

**R2 — passed in vibrate mode, and the mechanism is now known. The silent-mode fallback is dead.**

`adb shell dumpsys vibrator_manager` keeps the system's own record of every vibration and what it did with it. That is ground truth, and it is the tool to reach for whenever a haptics question comes up — the app's `FIRED` event only records that we *asked* the motor, never that the motor moved:

```
06:00:07.686  com.android.server.telecom   cancelled_superseded     527ms
06:00:08.213  com.mosman.thrum             cancelled_by_user       3267ms
```

Telecom starts the system's flat buzz; our waveform arrives ~0.4–0.5 s later and **supersedes** it. Android hands the motor to the most recent `RINGTONE` vibration, so we win by arriving second, not by luck. Same shape on all four post-fix calls. **Consequence to design around: every call opens with ~half a second of the system's flat buzz before the rhythm starts.** Not objectionable at Demo length, and it shrinks with our latency, but it is real and it is not suppressible from an app.

**Silent mode does not work, and `PROFILE.md` §9's fallback must be rewritten.** The plan assumed silent mode was a blank canvas to retreat to if the system's buzz interfered. It is a wall:

```
01:26:33.954  com.mosman.thrum   ignored_for_ringer_mode   0ms
```

Android discarded the vibration before the motor moved — duration zero. This was originally recorded here as *"silent mode works, 240 ms"*, read off the app's own `FIRED` line. **Mutalib caught it by hand — *"the silent mode is just silent, nothing else"* — and he was right.** Sacred Rule 2 in practice: the log said we fired, the phone said nothing happened, and the phone was correct. We do not need the fallback because vibrate mode works, but there is no longer a second option if some other phone suppresses us there.

The caveat that stands: this is one phone. Whether the supersede behaviour holds across OEM dialers is unknown, and Task 10's soak is the next place to watch it.

**R6 — passed.** Median 273 ms in vibrate mode, settling to 216–336 ms once Android keeps the listener warm. The first calls after install ran 588–713 ms — cold start, not the steady state. Nothing near the one-second threshold that would make it feel broken.

**Stop path — clean.** 13 stops for 13 calls: 7 answered, 6 declined. Zero `CAPPED` events, so the safety cap was never needed. The phone was never left buzzing.

**Two defects found, which is what this task was for:**

1. **Double-fire — fixed, and the fix verified on hardware.** On 5 of 13 calls the listener fired twice for one call, 21–678 ms apart: the dialer updates its own call notification (caller ID resolving, a photo loading) and every update arrived as a fresh "incoming", restarting the waveform a fraction of a second into the rhythm. Not felt at Demo-rhythm length, but it would be on a real track. Guarded now by key and by a 2 s window, and the safety cap is armed unconditionally so a stuck `activeKey` can never make that guard swallow real calls.

   Verified over **4 more real calls** on the fixed build, 2026-08-01 06:00–06:05: one `FIRED` each, and **2 of the 4 logged `SKIPPED · duplicate notification, already playing`**. The duplicates still arrive — the dialer's behaviour is unchanged — so the guard is catching them rather than the bug having quietly gone away. That distinction is the whole point of the check: the first two calls were clean but proved nothing, since two clean calls happen ~38 % of the time at the old rate.

   Confirmed independently in the system's own vibration record, which is the stronger evidence. Before the fix, our second call to the vibrator killed our first after 25 ms:

   ```
   01:24:21.647  com.mosman.thrum  cancelled_superseded     25ms
   01:24:21.672  com.mosman.thrum  cancelled_by_user      8994ms
   ```

   After the fix, every call shows exactly **one** `com.mosman.thrum` entry.

   Latency on those four: 226, 247, 243, 244 ms. Tighter than the original run, with the cold-start outliers gone now the listener stays warm.

2. **One 5.3-second delivery — open, not blocking.** A ring-mode notification reached the listener 5,324 ms after `postTime` (and a second at 4,355 ms). Harmless there because ring mode is skipped, but the same delay in vibrate mode would start the rhythm five seconds into the call and look broken. Seen once, cause unknown — carried into **Task 10**, whose soak is where a doze/background-scheduling cause would show up.

**Also observed:** two `LISTENER connected` events carry timestamps from 2026-06-15, before the app existed on this phone — `System.currentTimeMillis()` read before the clock synced after a boot. Cosmetic in a probe, but any product code that orders by wall clock would misorder. `lastFireAt` uses uptime for exactly this reason.

<details><summary>original task description</summary>

Add a `NotificationListenerService` that spots the incoming-call notification from the Phone app and plays the same hardcoded vibration from Task 1.

Then test a **real incoming call** in every relevant state and record what actually happens.

**⚠⚠ Why this is the most dangerous task in the project:** it decides whether Thrum exists. It has to answer three open questions from `PROFILE.md` at once:
- **R1** — can our vibration fire at all during a real call?
- **R2** — can the system's flat buzz be silenced, or must users switch to silent mode?
- **R6** — how late is the notification? If the vibration starts a second after the ring, it feels broken.

**Proof:**
- The table above, filled in from actual calls, committed into `PROFILE.md` §11.
- Latency measured from logcat: notification posted → vibration started, in milliseconds.
- Mutalib's verdict, in his own words, on whether it felt right or felt like mush.

**If this fails:** stop. Do not start Milestone 1. Bring the findings back and we redesign or kill it.

</details>

---

## Milestone 1 — The engine

Only starts once Task 2 passes.

### [x] Task 3 — Decode any audio file to raw samples — **DONE 2026-08-01**

All four required formats decode on the Pixel 6 Pro, verified against Android's own `MediaStore` durations rather than against a music player — the OS is the better witness, and it costs nothing:

| File | Reported by Thrum | MediaStore says | |
|---|---|---|---|
| `thrum-test-tone.wav` | audio/raw · 44100 Hz · stereo · 0:03 · 132,300 frames · peak 32 % | 3,000 ms | ✅ exact |
| `thrum-test-ringtone.ogg` | audio/vorbis · 48000 Hz · stereo · 0:12 · 612,000 frames · peak 75 % | 12,750 ms | ✅ exact |
| Keche — No Dulling `.mp3` | audio/mpeg · 44100 Hz · stereo · 3:58 · 10,514,721 frames · peak 100 % | 238,000 ms | ✅ |
| Harris J — Eid Mubarak `.m4a` | audio/mp4a-latm · 44100 Hz · stereo · 4:32 · 12,034,048 frames · peak 100 % | 272,881 ms | ✅ |

The generated WAV was written on the PC with known properties (44,100 × 3 s = 132,300 frames), so its row is a check against arithmetic rather than against another guess.

**Memory holds on real files.** The M4A streamed 12 million frames — about 24 MB of audio — through a reused buffer. Nothing in the app ever holds a whole track; [Pcm] downmixes each chunk and it is dropped. 7.4 s to read a 3:58 MP3, 10.1 s for a 4:32 M4A.

**Two defects found by testing, both fixed:**

1. **A long file was refused the slow way.** The 30-minute cap only fired once decoding had *passed* 30 minutes, so Mutalib's two 1h49m recordings each ground for most of a minute to reach a conclusion sitting in the header. Now the container's declared duration is checked before any decoding, and the refusal names the length: *"That file is 1:49:42 long."* The in-loop check stays as a backstop for files that declare no duration.

2. **Failure messages were useless for diagnosis.** `Over_the_Horizon.m4a` failed with a bare `IllegalStateException`. The cause was only found in **another app's** logcat crash: the file is **Dolby Atmos (E-AC3 JOC, 6 channels, 768 kbps)** in an `.m4a` container, and `c2.dolby.eac3.decoder` errors on it — Mutalib's own music player fails on the same file. Failures now name the format and say the phone lacks a working decoder for it. **The file is genuinely undecodable on this device; that row is not a Thrum bug.**

**Known gaps, stated rather than papered over:**
- **Mono is unit-tested but not device-tested.** Every real file to hand is stereo. `thrum-test-mono.wav` (22050 Hz, mono, 0:02, 44,100 frames) is staged on the phone for whenever it is convenient.
- The two fixes above are verified by code and unit tests, **not yet re-run on the phone** — the slow-refusal fix and the new Atmos message have not been seen in the log.

**13 new QA-suite tests** cover the arithmetic that has no phone in it: frame-to-millisecond rounding, the stereo downmix, overflow at full scale (two channels at `-32768` sum to `-65536`, which wraps positive in `Short` arithmetic and would read as a bright transient at the loudest moment of a track), trailing partial frames, and peak magnitude at `Short.MIN_VALUE`. Suite is 29 tests, all green.

<details><summary>original task description</summary>

`MediaExtractor` + `MediaCodec` to turn a user-picked file into plain numbers. Handle MP3, M4A, OGG, WAV. Handle mono and stereo. Handle a long file without running out of memory.

**Proof:** decode three files of different formats on the phone. Log sample rate, channel count, and computed duration for each, and confirm the duration matches what the music player shows. A file that fails to decode must produce a clear message, not a crash.

</details>

### [ ] Task 4 — Turn samples into a vibration score

Pure Kotlin, no Android imports. Low-pass filter, then an envelope follower, then downsample into `timings` and `amplitudes` arrays. Bass and drums drive it, because that's what a hand can feel.

**⚠ Small risk:** the tuning here is taste, not correctness. Expect to revisit it after Task 5.

**Proof:** the QA suite from `PROFILE.md` §12 passes on the PC in seconds — silent input gives an all-zero score, a four-on-the-floor beat gives exactly four peaks per bar, arrays are always equal length, amplitudes stay inside 0–255, and a score survives a JSON round-trip. This is the one part of the app that can be properly tested without hardware, so it gets real tests.

### [ ] Task 5 — Preview: audio and vibration together

Play the chosen track out loud with its vibration score running alongside it, so the feel can be judged directly against the music.

**Proof:** Mutalib picks his own imported ringtone, hits preview, and says whether the vibration matches the track. This is a taste gate, and only his hand can pass it. Expect to loop back to Task 4 once or twice — that's the process working, not a failure.

---

## Milestone 2 — The product

### [ ] Task 6 — The honest hardware check

On first launch, detect whether this phone's motor can vary its strength. If it can't, a plain screen explains why the app cannot help, in ordinary words, with no false hope and no "try anyway" button. Sacred Rule 2.

**Proof:** verdict correct on the Pixel. **And correct on a borrowed budget phone** — this is the only way to test the failure path, and shipping without testing it means shipping the one-star-review bug.

### [ ] Task 7 — The one screen

Material You, dynamic colour, follows system light and dark. Pick a file, see the score, preview it, arm it. Empty state and error state written as real screens, not afterthoughts.

**Proof:** screen works end to end on the phone. Layout confirmed by measurement rather than screenshots, which are unreliable on this machine.

### [ ] Task 8 — Save it and survive an upgrade

Persist the score as JSON in SharedPreferences. Add the R8 keep rules for any enum persisted by name.

**⚠ Why flagged:** this exact bug wiped every saved routine in `pixel-routines`. R8 renames enum constants, `valueOf()` then fails, and the catch block silently returns empty. Do not repeat it.

**Proof:** save a score, force-stop the app, reopen — still there. Then install a **release** build over the debug one and confirm the score survives. Verify the keep rules landed by extracting `classes.dex` and grepping the string pool (`dexdump` times out on this machine).

### [ ] Task 9 — Setup guidance

A short screen telling the user exactly which system settings to change, based on whatever Task 2 discovered. Plain language. Deep-links to the settings screen; the app never changes a setting itself (`PROFILE.md` §9).

**Proof:** Mutalib follows the screen on a fresh install, without help, and it works.

### [ ] Task 10 — ⚠ Does it still work tomorrow?

Reliability soak. Arm it, leave the phone alone for 24 hours, call it. Reboot the phone, call it again. Turn on battery saver, call it again.

**⚠ Why risky:** R5. Android kills background services, and a notification listener that isn't bound at call time means the vibration silently doesn't happen. An app that works on Tuesday and not Thursday is worse than one that never worked.

**Also watch here:** the 5.3-second notification delivery seen once in Task 2. Record the latency of every soak call, not just whether it fired — a delayed rhythm is a failure even though the log says `FIRED`.

**Proof:** three successful calls — after 24h idle, after reboot, and under battery saver. If any fail, fix before Milestone 3.

---

## Milestone 3 — Ship it

### [ ] Task 11 — Hardening pass

Run the benchmark audit and red-team prompts from the pipeline (Phase 5). For this app specifically: a giant audio file, a zero-byte file, a file that isn't audio, a corrupt file, a file deleted after being picked, permission revoked while armed, and a call arriving mid-conversion.

**Proof:** each attack listed with what happened and the fix. No crashes.

### [ ] Task 12 — Release build, signed and shrunk

Release keystore (git-ignored), R8 on, size checked.

**Proof:** signed release APK installs and works on the Pixel. Size reported.

### [ ] Task 13 — Play Store listing

Name decided (Thrum is still provisional). Listing states the hardware requirement **prominently and honestly** — not buried. Notification-access use explained clearly, both in the listing and in-app, because Google will ask.

**Proof:** listing text reviewed against Sacred Rules 2 and 4. Hardware warning appears above the fold.

### [ ] Task 14 — Launch

Phase 6 of the pipeline: smoke-test the real journeys on a release build, tag `v1.0.0`, publish.

**Proof:** launch checklist table, pass or fail per row, blockers first.

---

## Deliberately not in this plan

From `PROFILE.md` §5. Do not add them because a sitting went quickly:

per-contact vibration scores (Mutalib's own v2 pick) · the OGG haptic-channel encoder · ring-mode support for imported files · notification and alarm sounds · a score library · a pattern editor · widgets · any server, account, or paid tier.

## Wildcard, not blocking anything

Mutalib wants to try flipping `enableRingtoneHapticsCustomization` over adb on his own phone when he finds a cable. Worth doing for the knowledge, and it would be a nice thing to feel. It changes nothing about the app, which must work with that flag off (Sacred Rule 7).
