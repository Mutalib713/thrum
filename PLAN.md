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

### [ ] Task 2 — ⚠⚠ The wall: does it fire on a real incoming call?

Add a `NotificationListenerService` that spots the incoming-call notification from the Phone app and plays the same hardcoded vibration from Task 1.

Then test a **real incoming call** in every relevant state and record what actually happens:

| Phone state | System's own vibration | Ours fires? | Feels like? |
|---|---|---|---|
| Vibrate mode, default settings | | | |
| Vibrate mode, "vibrate first then ring gradually" OFF | | | |
| Vibrate mode, adaptive alert vibration OFF | | | |
| Vibrate mode, ring-vibration slider reset | | | |
| **Silent mode** | | | |
| Ring mode | | | |

**⚠⚠ Why this is the most dangerous task in the project:** it decides whether Thrum exists. It has to answer three open questions from `PROFILE.md` at once:
- **R1** — can our vibration fire at all during a real call?
- **R2** — can the system's flat buzz be silenced, or must users switch to silent mode?
- **R6** — how late is the notification? If the vibration starts a second after the ring, it feels broken.

**Proof:**
- The table above, filled in from actual calls, committed into `PROFILE.md` §11.
- Latency measured from logcat: notification posted → vibration started, in milliseconds.
- Mutalib's verdict, in his own words, on whether it felt right or felt like mush.

**If this fails:** stop. Do not start Milestone 1. Bring the findings back and we redesign or kill it.

---

## Milestone 1 — The engine

Only starts once Task 2 passes.

### [ ] Task 3 — Decode any audio file to raw samples

`MediaExtractor` + `MediaCodec` to turn a user-picked file into plain numbers. Handle MP3, M4A, OGG, WAV. Handle mono and stereo. Handle a long file without running out of memory.

**Proof:** decode three files of different formats on the phone. Log sample rate, channel count, and computed duration for each, and confirm the duration matches what the music player shows. A file that fails to decode must produce a clear message, not a crash.

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
