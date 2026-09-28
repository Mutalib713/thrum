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

### [x] Task 4 — Turn samples into a vibration score — **DONE 2026-08-01**

`ScoreBuilder` in `Analyser.kt`. Pure Kotlin, streaming and stateful so it consumes the decoder's chunks as they arrive — an analyser that wanted the whole file as an array would undo Task 3's memory work. What it holds is one number per step, about 6,000 for a four-minute track, against the 24 MB they came from.

Mutalib's verdict after three rounds: ***"it feels like the beat now."***

**The first design was wrong, and only real music showed it.** The plan says low-pass → envelope follower → downsample, and that is what was built. It passed every test, including "four-on-the-floor gives exactly four pulses". On Masha Allah it produced this:

| | first attempt | after the fix |
|---|---|---|
| Steps that are still | **3.3 %** | **69.8 %** |
| Pulses | 18 | **509** |
| Longest unbroken vibration | **142.9 s** | **0.48 s** |
| Mean amplitude | 124 / 255 | 33 / 255 |

Following *loudness* fails on real music because mastered tracks are loud almost all the time — a sustained bassline never falls back to the gate. It followed the music honestly and felt like a massage. **No threshold fixes that; the wrong thing was being measured.** The envelope is now also followed slowly, and only what rises above that slow average is kept: a held note pulls the average up until it cancels itself, a drum arrives faster than the average can follow. That difference is the beat.

**Then it was too weak to matter.** Mutalib put the phone on a table: nothing moved, while Android's own buzz shakes the table and so does the iPhone feature. Two causes, neither a threshold:
- **The bottom of a motor's range is not quiet, it is nothing.** Under roughly 140 the mass barely moves, so spreading hits across 0–255 spent half the scale on amplitudes the user never receives. What survives the gate now maps onto **185–255**.
- **A motor has mass.** A single 40 ms step ends while it is still spinning up. Every pulse is now held for at least **120 ms**, widened *forward* into the following silence — widening around the peak would move the hit's leading edge and put the rhythm ahead of the music.

**A strength slider ships on the probe screen.** The analysed levels are kept separately from the score, so moving it re-scores instantly instead of costing a seven-second decode. Tuning by feel is many small adjustments, and a rebuild between each one is how tuning stops happening.

**Proof — QA suite, 55 tests, all green on the PC in under a second.** The five checks `PROFILE.md` §12 names, plus the ones this task's failures earned:
- a held bass note must **not** vibrate continuously
- over half a bar must be stillness
- no hit may outlast the gap to the next beat
- no hit may be weaker than the felt threshold, or shorter than the pulse minimum
- the beat is still found underneath a continuous 8 kHz wash
- ragged chunk boundaries give a byte-identical score (the decoder's chunk size is whatever the codec felt like emitting; if the analyser depended on it, the same file would score differently on different phones)
- 44,100 Hz and 48,000 Hz give the same rhythm

**Counting pulses alone could not have caught the smearing** — "four pulses per bar" passes happily while the motor never stops. That is why the stillness and pulse-length checks exist.

### [x] Task 5 — Preview: audio and vibration together — **DONE 2026-08-01**

Brought forward by Mutalib mid-Task-4, and he was right to: *"because im playing the song different i dont get to start at the same time."* Judging whether a rhythm matches a song is impossible when the two are started by hand in different apps.

**The vibration starts when sound actually leaves the speaker, not when `start()` returns.** A player buffers and the audio path takes time to wake; firing at step zero would run the rhythm ahead of the music for the whole track. `getCurrentPosition()` only advances once audio is genuinely running, so that is the signal, and `Score.from()` drops the steps already gone. Assuming a player has played because it was asked is the same mistake as assuming a vibration happened — see R2 and R8.

**Proof:** Mutalib played his own music against it and confirmed the rhythm matches. The loop back to Task 4 the plan predicted happened **three times** — smearing, then weakness, then strength — which is the process working.

---

## Milestone 2 — The product

### Tuning notes — what has been tried, and what failed

Kept because two of these were dead ends that look obviously correct on paper, and the next person to think about this will have the same ideas.

**The motor is not the bottleneck. Measured 2026-08-01:** Mutalib ran `Demo.pulseTrain` up a ladder of rates and felt taps as *separate* up to **8 a second**, blurring at 12. Scores at the time ran at **3.1 a second**. Several rounds of tuning had been aimed at a hardware ceiling that does not exist. The probe is on the debug screen — run it before assuming a limit again.

**Failed: a constant "body" layer under the beats.** Taking the envelope's *level* rather than its onsets, capped below the hit floor, to fill the silence between beats. It measured almost nothing — at maximum, a real track was still 75.2 % silent — because it was built on the fast envelope, which collapses between beats. Mutalib reached the same verdict by hand: the setting he preferred was zero. Replaced by the detail layer.

**Failed: a 1.5 kHz band-pass for the detail layer.** The reasoning is sound. "Everything above 200 Hz" carries the vocal and the melody as well as the kit, and those are continuous, so the onset detector rejects them along with the taps hiding underneath; narrowing to where cymbals live should isolate the kit.

It does — and it also **bridges neighbouring beats into one run**, which the cymbal-wash check catches every time: eight beats become seven. Tried with the detail gate at 0.06, 0.08, 0.12 and 0.14; with both layers sharing one normalisation scale instead of one each; and with a warm-up on the followers. All still merged. **Reverted.** Do not retry without a plan for the merge, and do not soften the cymbal-wash check — it was the only thing that caught it.

**Open, found while chasing that:** the analyser has no history at the start of a track, so the opening hit runs long — 280 ms against a typical 180 ms on the score currently armed.

**Reproduced 2026-09-26.** It took two fixture changes, and the second is the one that matters.

*A lead-in was not enough.* Measured across five lead-ins — none, 0.5 s of digital silence, and noise floors at −55, −40 and −20 dB — on a two-bar `fourOnTheFloor` at 120 bpm: every run came out 400 ms, the first included, at every lead-in. The sparse fixture is **silent between kicks**, so the "recent average" follower never rises, every beat towers over a floor of nearly zero, and all of them produce the same six above-gate steps. Uniform input, so nothing to see.

*A bed is what was missing.* Real music is loud almost all the time. `Fixture.fourOnTheFloorOverBed` puts a continuous low bed under sharp 20 ms kicks, and the follower finally has something to settle at — which it can only do **after** it has heard something, and that is precisely the history the opening beat does not have. Body is set to 180 for the measurement rather than the 400 default, because at 400 the hold swallows the extra onset steps and hides the defect a second time.

At bed −12 dB, the reproduction:

```
first=480ms  typical=180ms   runs=[480, 180, 180, 180, 180, 180, 180, 180]
```

Sixteen beats collapse into eight. The first two are welded into one 480 ms buzz — at the very start of the ringtone, which is the part most often felt.

**The fix was attempted twice and reverted, and that is the useful part.** `sustained` starts at zero and needs 350 ms to become a usable estimate, so during that window `envelope - sustained` is not an onset, it is the whole signal — and every one of those steps is held by `holdPulsesAtLeast`, fusing them into a single smear.

1. **Settle `sustained` fast (30 ms) for the first 350 ms of the low band.** 480 ms → 400 ms. Nothing else broke, and it is not a fix.
2. **Do the same for the high band.** The opening run is fixed — and `moving the detail dial actually changes the score` fails. The spurious opening transient was what set the detail band's normalisation scale; removing it rescales the whole band below `DETAIL_GATE` and the detail layer collapses to nothing.

That second failure is not a flaw in the attempt, it is a real dependency: **the detail layer's scaling rests on an artifact of the opening.** Untangling it means re-tuning `DETAIL_GATE` and `DETAIL_CURVE`, which this file classifies as taste rather than correctness — and taste needs a hand on the phone, which is the standing gate on this project. Guessing at it would repeat the 1.5 kHz band-pass mistake above.

**Parked, with the test already written.** `the opening beat is no longer than the beats that follow it` carries the reproduction and is `@Ignore`d with the reason; enable it the moment the re-tune happens. `Fixture.noise()` and `Fixture.fourOnTheFloorOverBed()` are new and are what make the defect expressible at all. The analyser itself is untouched — no partial fix was shipped.

Still small, still real, still not urgent — but it now has a reproduction and a named blocker instead of a guess at the fix.

### [~] Task 6 — The honest hardware check — **BUILT 2026-08-01, half-proven**

The detection has worked since Task 1 (`Haptics.capability`). What this task added is the **screen**, designed alongside Task 7 rather than twice: it names what is wrong, says no setting or future version will fix it, and **carries no action at all**. A "try anyway" button would be a lie with a tap target.

**Proof — one half done, one half honestly not.** The verdict is correct on the Pixel 6 Pro: it reports capable, and the app proceeds. **The failure path has never run**, because it needs a phone whose motor cannot vary strength and there isn't one to hand. That is the case the screen exists for, so this task stays open rather than ticked.

Do not close this by reasoning about it. Borrow a budget phone (Tecno, Infinix, itel — `PROFILE.md` §4 says most of them physically cannot do this) and look at the screen it actually draws.

### [x] Task 7 — The one screen — **DONE 2026-08-01**

Built through the design-studio Studio pipeline. Full record in `design-brief.md`.

**Direction: instrument, not app.** Space Grotesk over IBM Plex Sans, both bundled (no `INTERNET` permission means downloadable fonts are unavailable, and the system face as the only face is how an Android app announces that nobody chose anything). Colour world **sulphur-concrete** — wet board-marked concrete with a sulphur-yellow safety line — because the language of *machinery that moves* is exactly what this product is.

Every colour pair computed rather than eyeballed. The one that mattered: **the yellow is 1.53:1 on the light field and can never be text there**, so light mode uses a darkened equivalent at 5.45:1 and keeps the raw yellow as a fill with dark ink on it (9.68:1). The data layer's own suggestion — indigo-violet with Righteous/Poppins — was rejected as the strongest AI tell on the banned list.

**Signature move: the pulse ribbon.** The score drawn as bars with a playhead sweeping it as the rhythm plays. Every other app asks the user to take vibration on faith; this shows the shape before it is felt, and its empty state is a flat line, which is exactly what the phone gives them today. One `Canvas`, not 6,000 layout nodes.

**Material You is opt-in, not default — a deliberate deviation from this task as written.** Handing the identity to the wallpaper reproduces the baseline-purple look on a purple wallpaper, and would make the *armed* and *blocked* states wallpaper-derived. Those two are what a user most has to read correctly.

**Seven states built, not remembered later:** blocked · permission · empty · reading · failed · ready · armed.

**Proof — measured on the device, not screenshotted.** `uiautomator` view dump on the Pixel 6 Pro:

| | |
|---|---|
| Ribbon | 336 px = 96 dp exactly (560 dpi, 3.5×) |
| Touch targets | 168 px = 48 dp, every one |
| Ribbon accessible | announces *"The rhythm: 214 hits over 164 seconds"* |
| Empty state | announces *"No rhythm yet"* |
| Armed indicator | labelled, not colour alone |
| Armed score survives restart | force-stop → relaunch → still armed |

Real track end to end: a 2:44 lofi hiphop file → 4,121 steps × 40 ms, 214 hits, **76 % still**, saved and re-read from preferences.

**Gates.** `gate.py` 0 block / 0 warn across 14 files. humanizer 100.0/85 on every user-facing string, 0 findings. **impeccable's detector did not run** — no output on the Kotlin files, on the repo, or on a known-good HTML file, so it is recorded as not run rather than as a pass.

**What the critique and the device found, fixed:**
1. Four equal-weight buttons, one of which ("Stop") did nothing unless something was playing. A dead control teaches people to distrust the live ones — it now appears only during playback.
2. The armed indicator was colour and nothing else: invisible to TalkBack, and to anyone who cannot separate the accent from the field.
3. The technical row printed "strength 255", which is 255 on every score by construction. It now shows what percentage of the track is **still** — the number that separates a rhythm from a buzz, and the one that was wrong for most of Task 4.
4. **Found only on the device:** pressing Stop silenced the motor but left the playhead sweeping and the Stop button on screen for the rest of the track, because the coroutine driving it was never cancelled. `dumpsys` said `CurrentVibration: null` while the UI still claimed to be playing. **This project's recurring bug in its newest costume — a surface reporting an intention rather than a fact.**

### [x] Task 8 — Save it and survive an upgrade — **DONE 2026-08-02**

**The upgrade test, run for real.** A debug build with live data, then a release build with R8 on installed straight over the top — no uninstall, because an uninstall would wipe the very data under test. Everything survived:

| | before | after R8 |
|---|---|---|
| Armed track | Asake — Active | **same** |
| Score | 2,250 steps × 20 ms | **same** |
| Tuning | Punch 193 · Distance 0 | **same** |
| Event log | 40 entries | **same** |

**The keep rule, proven in the shipped bytes rather than in the config.** All six `Event.Kind` names — `FIRED` `SKIPPED` `STOPPED` `CAPPED` `LISTENER` `DECODED` — were found in `classes.dex` extracted from the release APK. R8 left them alone, which is the whole point: they are written into SharedPreferences as strings and matched back by string, so a rename would turn every saved event into an unreadable line and every armed score into "nothing was ever saved".

**Why the rule list is nine lines and not ninety:** `Score` and `Event` serialise themselves by hand into a pipe-delimited string rather than through a reflection-based JSON library, so the shrinker has almost nothing it *can* break. That was a `PROFILE.md` §8 decision made long before this task, and it paid here.

`NotifService` is also kept: the system constructs it from the manifest name, so no code references it and the shrinker cannot see that it is used. Left to R8 it would be deleted, and the app's entire reason for existing would vanish from release builds only.

**Also verified:** the diagnostics screen is correctly absent from the release build (`BuildConfig.DEBUG`), and the APK shrank from **25.07 MB to 1.98 MB** — 92 % smaller.

**One thing deliberately deferred:** the release build is signed with the **debug key** for now. Android refuses an update signed with a different key, and the only way round it is an uninstall — which would have destroyed the data this task exists to protect. The real keystore is Task 12, and the build file says so at the line where it matters.

<details><summary>original task description</summary>

Persist the score as JSON in SharedPreferences. Add the R8 keep rules for any enum persisted by name.

**⚠ Why flagged:** this exact bug wiped every saved routine in `pixel-routines`. R8 renames enum constants, `valueOf()` then fails, and the catch block silently returns empty. Do not repeat it.

**Proof:** save a score, force-stop the app, reopen — still there. Then install a **release** build over the debug one and confirm the score survives. Verify the keep rules landed by extracting `classes.dex` and grepping the string pool (`dexdump` times out on this machine).

</details>

### Task 8 follow-up — persistence gaps, and three "I can't feel it" causes — **CODE DONE 2026-09-17, PC-verified only**

Mutalib reported *"it doesnt vibrate to the max i cant feel it sometimes"*, and asked
for Task 8 finished at the same time. A read of the code found four defects, none of
which needed the phone to find. **All four are fixed and covered by tests. None is
device-verified yet — the phone was not connected, so no claim of "fixed" stands until
a hand confirms it.**

**Strength — three separate causes, all real:**

1. **Held hits decayed below the motor's floor.** `holdPulsesAtLeast` fades each held
   step so a hit sounds like a drum rather than a square pulse, but nothing stopped the
   fade. A 185-peak kick walked down to ~102 across its held steps, and under roughly
   140 the mass barely moves — so most of the hold was time the hand never received.
   `holdPulsesAtLeast` now takes a `floor`; held steps stop there. A pulse already
   weaker than the floor is left alone, so the floor limits the decay and never lifts a
   quiet hit into a loud one.
2. **Pulse lengths rounded down.** `DETAIL_PULSE_MS / stepMs` truncated: 45 ms at 20 ms
   became 40 ms — *under* the minimum the constant names — and at 40 ms steps it became
   one step, so the hold did nothing at all. Now ceiling division, via a named
   `ScoreBuilder.stepsFor` so the arithmetic is testable on its own.
3. **The detail dial was dead across most of its travel.** `ceilingFor` was
   `punch × (100 − distance) / 100`, and the layer switched itself off once the ceiling
   reached `DETAIL_MIN`. At the default Punch of 185 that was **distance 30**, so
   seven-tenths of the dial did nothing; at Punch ≤ 130 *no* position did anything. The
   0–99 range is now spread across the whole usable span with an explicit off at 100.
   This is the old "every setting felt the same" complaint in a new costume.
4. **Stereo downmix cancelled out-of-phase content.** Averaging L and R gives
   `+10000 + −10000 = 0` for anything the mastering spread in opposite polarity — a
   wide synth bass, a stereo-widened kick. The bass did not get quieter, it vanished,
   and that part of the track scored as silence. No Punch setting recovers a hit that
   was never detected. Now the greater magnitude wins with its sign kept, which also
   stops hard-panned content arriving at half strength.

**Also fixed, found while chasing the above:** normalisation was against the whole
track, but only the first `RINGTONE_SECONDS` is ever played. A track that opens quietly
and peaks minutes later had its opening scaled down until every beat fell under `GATE`
and came out as a literal zero. The scale is now set by the window that plays, with a
guard: below `WINDOW_NORM_GUARD` of the track's peak the window is dither rather than
music, and the global peak is used instead, so a near-silent opening cannot turn its
noise floor into a drum kit.

**Ring mode re-asserted the rhythm wrongly — the bug was in the default-on path.**
The ringtone repeats, and every repeat makes Android re-issue its own vibration, so the
rhythm has to be re-asserted every 2 s from wherever it *would* be by now. That used
`Score.from(into)`, which **truncates**: replayed with looping on it looped only the
tail, so the opening of the rhythm was never heard again, and replayed with looping off
it played the remainder once and then went silent for the rest of the ring — `from`
past the end returns an empty score, which `Haptics.play` refuses, so there was no
vibration at all. New `Score.rotated(startMs)` wraps instead of truncating: same steps,
same length, started at the right moment. `from` is untouched and still correct for the
preview, which is genuinely chasing a file that ends.

**Persistence (Task 8 gaps):**

- `Store.arm()` writes score + source URI + tuning in **one** `edit()`. Written
  separately, a restart could land between them and restore a score carrying a
  different file's name and URI — the screen claiming one rhythm while the motor played
  another. One write makes that mismatch impossible rather than unlikely.
- The picked URI is a **draft** until Arm. It used to be written on pick, so choosing a
  file and walking away left the *armed* score's stored URI pointing at the file being
  auditioned; after a restart the dials rebuilt B's amplitudes under A's name.
- `pickedUri` is seeded from storage at startup, so **Preview works after a restart**
  instead of being a dead button — the file was in storage the whole time.
- A failed draft pick no longer blanks a score that is still armed.

**Proof — measured on the Pixel 6 Pro, 2026-09-17, against Mutalib's own armed track.**

QA suite **76 tests, 0 failures** on the PC (was 64; 12 new, 1 rule changed). Build
green, debug APK 26 MB.

Task 8's upgrade test, run for real: the new debug build installed **over** the existing
one, no uninstall. Everything survived — `punch 208`, `distance 66`, `fire_in_ring_mode
true`, the armed score, and the event log. The stored `source_uri` still names the same
file as the armed score's `sourceName`, which is the pairing [Store.arm] exists to
protect:

```
armed score says : AIZO__but_it_s_lofi_hiphop____Jujutsu_Kaisen(256k).mp3
source_uri says  : ...%2FAIZO__but_it_s_lofi_hiphop____Jujutsu_Kaisen(256k).mp3
                   MATCH
```

The app then launched with no crash and restored the armed state on screen
("Ready", 2250 steps, Punch 208).

**The strength fix, measured rather than asserted.** Reading the armed score out of
`shared_prefs` before and after a rebuild through the fixed analyser, same track, same
Punch of 208:

| | before (old analyser) | after (fixed) |
|---|---|---|
| non-zero steps | 537 (23.9 %) | 1024 (45.5 %) |
| pulses | 64 | 134 |
| kick layer | 488 steps, 188..254 | 609 steps, **208..255** |
| detail layer | 49 steps, 134..183 | 415 steps, 130..151 |
| **the gap 160–207** | **47 steps** | **0** |
| below the detail floor | 0 | 0 |

Read that table as the two bugs it is. The **kick floor is now exact**: the weakest kick
step is 208, the Punch value, instead of decaying to 188. And the **gap went from 47
steps to none** — 47 steps used to sit between the texture ceiling and a beat, too weak
to land as a beat and too strong to sit under one, which is precisely the "I can't feel
it sometimes". The detail layer went from 49 leaky steps to 415 in a tight 130–151 band.

**This also confirmed the dead-dial bug in Mutalib's own settings**, not just in theory:
at `punch 208` the old `ceilingFor` gave `208 × 34 / 100 = 70`, under the motor's floor,
so his detail layer was switched off entirely at `distance 66`. He had been feeling
kick-only and had no way to know, because moving the dial anywhere past 30 changed
nothing.

**Still owed:** Mutalib's hand. The measurements prove the score is right; only he can
confirm it *feels* right. Play it with the song, feel it on its own, and take a real
incoming call in vibrate mode. `dumpsys vibrator_manager` is the witness for whether the
motor actually ran, not the app's `FIRED` event.

### Task 8 follow-up, part two — **why it never shook a table** — CODE DONE 2026-09-21

Mutalib, after the above: *"still not strong enough ... I can only feel it when I'm holding
or touching the phone but a normal vibration should be higher. Lets say the phone is on a
table it should vibrate the table or something. And also the ringtone made by Pixel already
comes with this vibrate feature and it's more powerful than ours."*

Everything before this had treated the complaint as an **analyser** problem. The four bugs
in the section above were all real and all worth fixing, and none of them was this. This one
is not about the analyser at all.

**The measurement.** `dumpsys vibrator_manager` keeps the waveform the system actually
received. Three of them, all at `usage: RINGTONE` and `scale: NONE (1.00)` — so **nothing is
being attenuated on either side**:

| | longest run at 255 | mean while driving |
|---|---|---|
| **Android's own incoming-call vibration** (`com.android.server.telecom`) | **1000 ms** | 250/255 |
| **Thrum's armed score** | **100 ms** | **177/255** |
| a flat 1500 ms at 255 through `Haptics.play` (control) | 1500 ms, `finished` | 255/255 |

The system's own call vibration is literally `[0ms @ 0.00, 1000ms @ 1.00, 1000ms @ 0.00]`,
repeating. **One full second at maximum.** Thrum's longest full-strength stretch was a tenth
of that, at 70 % of the strength. And the control proves the amplitude path can sustain 255
for a second and a half and be accepted.

**So the motor was never the limit, and neither was the API. The score simply never asked.**
Every dial in the app controlled *how hard* a beat lands; none controlled *how long* it is
driven. That is the axis that decides whether a phone lying on a table moves, and it was the
one nobody had measured.

**The fix: a Body dial.** `MIN_PULSE_MS` (100) becomes `BODY_MS`, with `BODY_MIN_MS` 100 and
`BODY_MAX_MS` 400 as the dial's ends. `Store.arm` writes it with the score and the other
tuning, so a restart cannot separate them. The dial shows **milliseconds**, not an invented
0–100: it is a duration, and a duration is a fact the person tuning it can reason about.
Detail deliberately does not scale with it — a hat that lasts as long as a kick stops being a
hat, and the detail layer carries the least energy.

### The measurement, 2026-09-21 — and the two bugs it found

The numbers below are **measured**, not estimated. The analyser was reproduced in Python
(`Analyser.kt` + `Score.kt` transcribed, ffmpeg decoding, `Pcm.downmixToMono`'s
greater-magnitude rule included) and the reproduction was checked against the score actually
armed on the phone, out of `shared_prefs`. It reproduces it at **99.82 % of steps identical**,
so these are the phone's numbers rather than a model of them.

Sustained drive is `duty × mean amplitude / 255` over the 45 s window that plays — 1.000
meaning 255 held constantly:

| | duty | mean on | sustained drive | longest run ≥ `MIN_FELT` | still |
|---|---|---|---|---|---|
| Android's own call buzz | 50.0 % | 255 | **0.500** | 1000 ms | 50.0 % |
| Thrum, Body 100 ms | 45.6 % | 187 | **0.334** | 400 ms | 54.4 % |
| Thrum, Body 240 ms | 54.3 % | 196 | **0.417** | 840 ms | 45.7 % |
| Thrum, Body 400 ms | 67.2 % | 205 | **0.540** | 1460 ms | 32.8 % |

**Two corrections to what this document previously claimed.**

1. **The estimates were far too generous.** The 240 ms default was written up as ~0.60 (1.2×
   the buzz); it is **0.417, i.e. 0.83× — still a downgrade**. The 400 ms maximum was written
   up as ~0.73 (1.5×); it is **0.540, i.e. 1.08×**. The inference behind the estimates — that
   held steps land at the kick's floor and so *raise* the mean — was wrong in the direction
   that mattered: a hold only runs into the silence *immediately* after a hit and stops at the
   next one, so it cannot fill a gap longer than itself, and this track is 54 % silent at the
   old length for reasons no dial touches.
2. **The stillness table that stood here was wrong**, and wrong in the flattering direction.
   It claimed 400 ms leaves the motor still 10 % of the time; the score says **32.8 %**. The
   old figures described what the *hold* was asked for, not what survived the gate and the
   track's own silence.

**The honest headline, corrected:** at 240 ms Thrum still delivered less than the buzz it
silences. Only at 400 ms does it reach parity — 0.540 against 0.500 — and it beats it on the
axis a table actually responds to, a felt run of **1460 ms against the stock buzz's 1000 ms**.
So **the default moved to 400 ms.** A default weaker than the thing it replaces is not worth
shipping, and there is no headroom above it worth having: at ~3 taps a second the gap between
beats is ~333 ms, so a longer Body is capped by the next hit and buys nothing. Going
materially past 0.540 is a different question — the actuator path — not a larger number here.

**Also added, in the debug probe only:** `Demo.flatMax` (1500 ms flat at 255), `Demo.thrumTap`,
and `Haptics.playPrimitives` — a THUD/CLICK path the product does **not** use, kept to answer
whether primitives hit harder than the amplitude path. Measured: `Primitive=THUD(scale=1.00)`
runs **323 ms** and is accepted. That remains the next lever, and it is an architecture
decision rather than a tuning one.

### The bug this measurement found: the phone was armed with a Body it was not showing

`shared_prefs` said `body=400`. The armed score reproduced **exactly at `body=100`** — 99.82 %
of steps, against 68.53 % at 400. The screen said 400 ms and the motor was playing 100 ms.

The cause was a split between two writers of the same setting. `Store.arm` wrote the score and
all its tuning in one `edit()` precisely so they could not disagree — but the dial handlers
*also* wrote their key directly:

```kotlin
onBody = { v -> body = v; store.body = v; rescore() }   // the pref moves here
```

and `rescore()` only re-arms when `armed` is true:

```kotlin
if (armed) { store.arm(rebuilt, …, body) }               // …but not here
```

Picking a file sets `armed = false` (the draft path). So a dial moved while a draft was being
auditioned moved the stored value, rebuilt the score on screen, and then **skipped the write
that would have armed it**. The phone kept the old rhythm; a restart restored dials no score
had ever been built with. It is the same class of lie `rescore()`'s own comment says the
project keeps having to fix — a screen showing one rhythm while the phone would play another.

**The fix is structural, not a comment.** `punch`, `distance` and `body` are now `val`s with a
getter only, so `Store.arm` is the compiler-enforced single writer of the tuning, and the dial
handlers just call `rescore()`. A setting the phone will not play must not be what survives.

**The part that explains the complaint better than the waveform does.** Across three separate
real calls in the same `dumpsys` dump, the system's own call vibration is recorded as
**`cancelled_superseded`** about 0.8 s after it starts, and Thrum's begins at that instant —
every time. So Thrum does not merely fail to be stronger; it **takes the motor away from a
1000 ms at 255 buzz and substitutes its own**. The comparison Mutalib was making was against
the stock buzz Thrum had just silenced, which is exactly why *"the ringtone made by Pixel is
more powerful than ours"* is a correct observation rather than a mistaken one.

**Still owed, and it is the whole remaining question:** Mutalib's hand, at 400 ms. The armed
score on the phone was the 100 ms build, so he has never actually felt the setting the screen
was showing him — every test call so far was against a rhythm weaker than the one he chose.
The 400 ms score is now written to the device. If 400 ms still does not shake a table, the
ceiling on a rhythmic score against a continuous buzz is real and the primitive path is the
next step.

### Known gap: the two guards about the detail layer cannot fail

Found while checking the measured distribution for dead-zone steps, and recorded rather than
changed, because it is a design question and it does not affect what the phone plays.

`QaSuiteTest.analyse` pins `detailCeiling` to `ScoreBuilder.BODY_CEILING`, which is 150. A
ceiling of 150 makes 151–184 unreachable **by construction**, so both guards about the detail
layer are asserted against a condition their own fixture cannot produce:

- `nothing lands in the dead zone` filters `> BODY_CEILING && < MIN_FELT` — always empty.
- `hits still stand clear of the texture underneath them` takes the minimum of everything
  `>= MIN_FELT` — and with a ceiling of 150 every such step is a kick, so it is always `> 150`.

The app does not use that ceiling. It passes `ceilingFor(punch, distance)`, which at Punch 208
runs from **208 at distance 0** down to 131 at distance 99. Measured on the real track, at
Body 400:

| distance | ceiling | steps in 151–184 | loudest detail step |
|---|---|---|---|
| 0 | 208 | 46 (2.0 %) | 182 |
| 25 | 188 | 20 | 172 |
| 50 | 168 | 4 | 157 |
| **61 (the phone's)** | **159** | **1** | **151** |
| 70 | 152 | 0 | 146 |
| 99 | 131 | 0 | 131 |

So the dead zone **is** entered at any distance below 70 — the guard would fail if it used the
real ceiling. Two things worth separating out:

1. **`the beat leads` does hold.** The loudest detail step is 182, under `MIN_FELT` at every
   setting, because `ceilingFor` never exceeds the kick's floor and the detail curve pulls
   most hits well below it. The invariant is sound; it is simply not guarded by a test that
   can fail.
2. **Whether texture *should* be barred from 151–184 is a real question, not an obvious yes.**
   The dead-zone rule was derived from the *kick* layer, where a hit mapped under `MIN_FELT` is
   a hit the user never receives. A detail hit is texture rather than a beat, so a slightly
   loud hat at 160 is not obviously a defect — and clamping the ceiling to 150 to enforce the
   rule would put the Distance dial back where it started, dead across three quarters of its
   travel. That is the trade `ceilingFor` was written to escape, so this needs deciding rather
   than patching.

**What to do about it:** make `analyse` able to take the app's own ceiling, assert
`detail max < MIN_FELT` there (which holds and is worth guarding), and restate the dead-zone
expectation with the bound measured above rather than as an absolute. Not done in the same
change as the strength work, deliberately — it is test hygiene, not a fix, and mixing it in
would have made a tuning commit look like a refactor.

### [~] Task 9 — Setup guidance — **BUILT 2026-09-26, proof pending**

**The task shrank, and that is a result rather than a shortcut.** It was written expecting a screen listing system settings to change. Task 2's measurement removed almost all of them: `vibrate_when_ringing` stayed on and `ring_vibration_intensity` stayed at 3/3 through all thirteen real calls and made no difference, because the last `RINGTONE` vibration wins. A screen telling users to change either would be teaching a superstition.

What is left is one setting that silently kills the app, and one that quietly does:

| Ringer | `Also when the ringer is on` | What happens | Verdict |
|---|---|---|---|
| Vibrate | either | the case the app exists for | fires |
| Ring | on | fires — hears one song, feels another | fires |
| Ring | off | dead by the user's own switch | blocked |
| **Silent** | **either** | **Android discards the vibration** | **blocked** |
| unreadable | either | nothing can be promised | unknown |

**Silent mode is the whole point of the screen.** Android throws a `RINGTONE` vibration away outright when the ringer is silent — `ignored_for_ringer_mode`, `duration 0ms`, in the system's own record. Mutalib felt it and named it. No app can work around it, and until now the screen said *"Put your phone on vibrate"* and then never checked whether it was. To anyone not thinking about it, silent and vibrate are both just *no sound* — so the wrong one makes the app look broken, which is exactly what R4 is about.

`armed_body` and `armed_ring_note` are gone, replaced by a verdict read live from `AudioManager.ringerMode`, polled at the same cadence as the permission. Both were static lines describing an intention, and the note was also wrong whenever the ringer was not actually on.

**Structure.** `Setup.kt` is pure Kotlin with no Android imports, for the same reason `Score` has none: the decision table is the part that can be proved on this PC. `SetupVerdict` on the product screen renders it. The app still changes nothing itself — silent mode gets a button that *opens* Sound & vibration, not a button that fixes it (§9).

**Proof — the table, exhaustively.** **90 tests, 0 failures** (82 before, 8 new); APK 25.72 MB. The new eight cover every ringer × switch combination rather than the two happy ones:

- vibrate fires whichever way the switch is set, and silent never does — the switch cannot reach into either
- ring mode follows the switch both ways, matching `NotifService.onIncoming`'s own condition
- an unreadable ringer reads as neither working nor broken
- only silent and ring-off set `blocked`, which is what picks the headline and the colour
- only silent sends the user out to system settings
- the table is total: all five verdicts are reachable, so no ringer mode is left with nothing to say
- `Ringer.of` maps exactly and case-sensitively — a looser match would hide a change to `Haptics.ringerMode`'s contract

**Not proven, and it needs the phone:** that `AudioManager` reports silent as `"silent"` on the Pixel, and that the verdict reads correctly at fontScale 1.3 with a long track name above it. **The proof as written — Mutalib follows the screen on a fresh install without help — has not happened**, so this stays `[~]` rather than ticked.

**Open question, deliberately not guessed at: Do Not Disturb.** DND may suppress an incoming call's ringtone and its vibration together, which would be a *fourth* silent-failure mode. It is readable (`NotificationManager.getCurrentInterruptionFilter`), but **nothing about it has been measured on this phone** and the evidence rule does not allow inventing a verdict. Measure it in Task 10: turn DND on, call the phone, read `dumpsys vibrator_manager`. Only then decide whether it earns a row.

### Seen on the phone, 2026-09-28 — the setup screen runs, and what that exposed

**Task 9's screen was seen running for the first time.** It had been built on 2026-09-26 and never once looked at, because the phone could not be reached. Over wireless debugging it came up armed and correct:

- masthead, the pulse ribbon, the track name, `0:45 · 79 hits`, and **`Ready`**
- `Vibrate mode, playing your own music` — so the ringer verdict reads correctly on the Pixel **in vibrate mode**, which is half of what line 653 says is unproven
- the disclosure paragraph, the `Also when the ringer is on` toggle on, and both action buttons
- `2250 steps · 20 ms each · still 32% of the time`
- `Tune the feel`: Punch, Beat length 400 ms, Distance from the music 61
- the tap test, and a `Diagnostics` row

The long track name wraps to two lines without breaking the layout. Two of the three things line 653 lists as needing the phone are therefore answered for vibrate mode; **silent mode and fontScale 1.3 are still unmeasured**, and the proof as written — Mutalib follows the screen on a fresh install without help — still has not happened.

**`still 32% of the time` reconciles the two numbers that looked wrong.** The armed score has 1,513 non-zero steps of 2,250, which reads as 67%. The screen says 32%, and both are right: 67% is steps the score *marks*, 32% is what survives `MIN_FELT` and actually moves the motor. A score that is vibrating two-thirds of the time would be a buzz, not a rhythm.

**New defect — the preview button loses a word.** The label is `ready_preview` = `"Play it with the song"`. On screen it reads **`Play it with the`**. The two buttons sit in a `Row` with `Modifier.weight(1f)` each, so at 411 dp — the Pixel 6 Pro's own logical width — the text does not fit. `Secondary` passes `maxLines = 1` and no `overflow`, and Compose defaults to `TextOverflow.Clip`, so the word is dropped with **no ellipsis**. It does not look truncated; it looks like a sentence that stops. The accessibility tree still reports the full string, so the two disagree — which is the class of lie this project keeps having to fix. One line to fix (`overflow = TextOverflow.Ellipsis`), or shorten the string, or let it wrap like `Primary` does.

**The debug score dump does not cover the live path.** `files/last-score.txt` is written only inside `ProbeScreen` in `MainActivity.kt` — the *legacy* diagnostics screen. The live UI is `ThrumApp` in `ThrumScreen.kt`, which never writes it. Picking a song the normal way therefore leaves the file untouched: it carried an mtime of `2026-09-21`, a week stale, while the app had decoded that same track minutes earlier. It was pulled and read before that was noticed. **Check the mtime before trusting it**; for a song armed through the normal screen the source of truth is `armed_score` in `shared_prefs/thrum.xml`, written by `Store.arm`.

**Punch's ceiling is 210, not 255.** `range = 120f..(Score.MAX_AMPLITUDE - ScoreBuilder.MIN_HEADROOM)` = `120..210`, so a Punch of 210 is the dial pinned at maximum. It read 208 on arrival and 210 afterwards; the likely cause is a synthetic `input swipe` whose press landed on the track, since a Compose slider jumps to the position pressed. **`input swipe` is not safe around this screen** — press-to-position means an automated scroll can silently retune a dial. Body (400, its own maximum), Distance (61) and Texture (0) were untouched.

**The app re-decodes, ~6 s, when `levels` is lost.** `levels` is in-memory only, so losing it sends `rescore()` down its rebuild path: `c2.android.mp3.decoder`, 48 kHz stereo, 17:10:16 → 17:10:22 for the 45 s track, with `Reading` on screen. That is by design and the code says so. What is *not* established is what lost `levels` — the process was 3 h 8 m old throughout and the scroll position survived, so the composition was not recreated. Left unresolved rather than guessed at; it is not a correctness problem, only a 6-second one.

### The standing gate, answered 2026-09-28 — and the motor is not the problem

Mutalib, verbatim: *"still i cant feel anything aint strong enough"*. Body was already at 400 (its maximum) and Punch at 210 (its ceiling), so there was no dial left to turn up and the question became whether the motor was doing anything at all.

**It is. The phone physically shakes.** Measured with `dumpsys sensorservice`'s last-50 accelerometer window, verified live by the wall-clock timestamps advancing between captures rather than assumed:

| capture | \|g\| sd | max jerk |
|---|---|---|
| idle (flat, untouched) | 0.0096 | 0.030 |
| during the tap test, 1 s | 0.0193 | 1.160 |
| during the tap test, 8 s | **0.1110 (×11.6)** | **3.240 (×108)** |

Peak sample `(-0.57, -1.75, 10.01)` — **1.75 g sideways**. A handset that is not moving cannot do that, so the motor, the HAL and the app's call path are all working.

**The defect is the shape, and it is the buzz the app exists to replace.** `dumpsys vibrator_manager` shows Thrum asking for 2,313 steps of 20 ms with **89.6% of them non-zero at a near-constant 0.84 amplitude**. A hand feels a haptic through its *onset*, and a drive that is already on and stays on has almost no onset to feel. It reads as a flat hum. **Punch at its maximum made this worse, not better**: Punch is the floor, so at 210 of 255 almost nothing is silenced and the score saturates into a solid band. Louder, flatter, less like a beat.

**⚠ Open, and the most important lead in the file: the score may not be reproducible.** `still` read **32%** earlier the same day and reads **10%** now, and the waveform's step count moved 2,488 → 2,313 — with only Punch changed, 208 → 210. Raising `minFelt` can only push *more* steps below the floor, so `still` should have risen, not fallen by two thirds. Something rebuilt the score differently. **Until that is explained, tuning by ear on the phone is tuning against a moving target.** Decode `armed_score` and compare its histogram against a fresh PC analysis.

**Next:** the tap test is the ten-second discriminator — its pulses are sharp, they are what moved the phone by 1.75 g, and if Mutalib can feel *those* but not the armed rhythm then the fault is entirely in the score's shape. Then the fix direction is **more silence and more contrast, not more amplitude**, which is already at the ceiling.

Full write-up, with the method and the reproduction: `docs/device/2026-09-28-vibration-measurement.md`.

**Note for anyone trying this again:** `cmd vibrator` **does not exist** on this device — `adb shell cmd vibrator` returns `Can't find service: vibrator` — so there is no way to fire a test vibration over adb. The tap test is the only on-device pulse source, and the accelerometer is the only objective readout.

### [ ] Task 10 — ⚠ Does it still work tomorrow?

Reliability soak. Arm it, leave the phone alone for 24 hours, call it. Reboot the phone, call it again. Turn on battery saver, call it again.

**⚠ Why risky:** R5. Android kills background services, and a notification listener that isn't bound at call time means the vibration silently doesn't happen. An app that works on Tuesday and not Thursday is worse than one that never worked.

**Also watch here:** the 5.3-second notification delivery seen once in Task 2. Record the latency of every soak call, not just whether it fired — a delayed rhythm is a failure even though the log says `FIRED`.

**And the Task 9 open question.** Turn **Do Not Disturb** on, call the phone, and read `dumpsys vibrator_manager`. If DND suppresses the call's vibration as well as its sound, that is a *fourth* way to be silently dead — and the verdict screen from Task 9 currently says "Ready" in that state, which would make it a screen reporting an intention. Measure before deciding whether it earns a row; do not reason it out.

**Proof:** three successful calls — after 24h idle, after reboot, and under battery saver. If any fail, fix before Milestone 3.

---

## Milestone 3 — Ship it

### [~] Task 11 — Hardening pass — **BUILT 2026-09-26, three of seven attacks need the phone**

The headline is that **four of the seven attacks were already handled**, and Task 3 deserves the credit. The other three were not, and two more turned up while reading the decoder closely that were never on the list.

| Attack | What happened | Fix |
|---|---|---|
| **A giant audio file** | Refused *before* decoding. The container states its own length, and anything over 30 minutes is turned away in the header rather than after being ground through — the check exists because Mutalib's two 1h49m recordings each took most of a minute to refuse. A file that declares no length is refused in-loop once it passes 30 minutes, and both messages name the length they are refusing. | none needed |
| **A zero-byte file** | `setDataSource` throws, caught, and answered with "Couldn't open that file. It may have been moved or deleted, or it isn't audio this phone can read." | none needed |
| **A file that isn't audio** | Same path. If the container opens but holds no `audio/*` track: "There's no audio in that file." | none needed |
| **A corrupt file** | Either the extractor refuses it outright, or the codec throws mid-stream. `CodecException`, `IllegalStateException` and a deliberately wide generic net each produce a sentence instead of a crash — the `IllegalStateException` branch names the Dolby-in-`.m4a` case that cost Task 3 an hour. | none needed |
| **A file deleted after being picked** | **Broken, and it is the worst kind.** The re-decode correctly returned a failure — and `rescore()` dropped it on the floor. The dial moved, the score did not change, and the screen said nothing. A control that moves and does nothing is indistinguishable from a broken control. | Fixed: the failure is surfaced beside the dials in the decoder's own words, and a score with no recorded source says so instead of returning silently. Deliberately **not** the full error state — the armed score is still armed and still plays, so ejecting the user would overstate it. |
| **Permission revoked while armed** | Already correct. The listener permission is polled, so the screen falls back to the Permission state; the armed score survives, so re-granting restores it. | none needed |
| **A call arriving mid-conversion** | Already correct, and worth writing down because it is not obvious. `Store.arm` is the only writer of the armed score and arming is a deliberate press, so a call during a decode plays the *previously* armed score — which is the right answer, because the new one is not armed yet. | none needed |

**Two more, found by reading rather than by attacking:**

1. **8-bit and 24-bit audio decoded as noise.** The sample reader knew about exactly two encodings, 16-bit and float. Everything else fell through to `size / 2` and a short read, which does not fail — it produces *numbers*. A 24-bit WAV became 1.5× too many samples and an 8-bit WAV half as many, both as noise, and both became a vibration score that looked exactly like a working one. Now all five encodings are read correctly (8-bit unsigned, 24- and 32-bit sign-preserved) and anything else is refused with a sentence rather than guessed at. The arithmetic moved to `Pcm` so it could be proved on the PC — which is how the next one was caught.
2. **A decoder that accepts no input spun forever.** The stall guard only counted rounds *after* the last input had been queued, so a codec that never accepted input could never start the counter: the loop had no exit at all, and the app would hang until the user force-stopped it — on a file they only wanted to preview. The guard now counts rounds where *nothing* progressed, with a looser cap before input is done so a brief pause is not mistaken for death.

**Also caught while writing the test:** the 32-bit branch read its sign byte from offset +2 instead of +3. Little-endian four-byte samples carry the sign in the *fourth* byte, so it read the wrong end of every sample — a plausible waveform, entirely wrong. The test failed, which is the entire reason the arithmetic was moved somewhere it could be tested.

**Proof — split honestly, because the three are not equal.**

- **Proven by test:** the encodings. **99 tests, 0 failures** (90 before, 9 new): every readable encoding round-trips, an unreadable one writes *nothing* rather than something plausible, no encoding can overrun the output array, and the mirrored `AudioFormat` constants still match the platform's. All of it runs on the PC with no phone.
- **Proven by reading, not by attack:** the giant / zero-byte / not-audio / corrupt cases, and the mid-conversion call. Each is a code path with a return, and each was read line by line — but none has been fed the real file it defends against.
- **Not proven, and it needs the phone:** the deleted-file message actually appearing beside the dials, and the stall guard not producing a false positive on a slow decode. A false positive there refuses a file that was fine, which is the failure mode to watch for.

### [~] Task 12 — Release build, signed and shrunk — **BUILT 2026-09-26, install pending**

Real release key generated: 4096-bit RSA, SHA384withRSA, valid until 2056. `thrum-release.jks` and `keystore.properties` sit at the repo root, both git-ignored — confirmed with `git check-ignore` rather than by trusting the file.

`app/build.gradle.kts` reads the properties file when it exists and signs with it; when it does not — a fresh clone, or any machine without the secret — it falls back to the debug key so `assembleRelease` still runs and R8 can still be checked. **Play rejects a debug-signed upload, so the fallback cannot reach users.**

| | |
|---|---|
| Release APK | **1.98 MB** (2,079,266 bytes) |
| Debug APK, for scale | 25.72 MB |
| Signer | `CN=Thrum, O=Mutalib Osman, C=GH`, SHA-256 `011c0805…` |
| Tests | 90, 0 failures |

**R8 did not break the serialisation — checked, not assumed.** This is the bug that silently wiped every saved routine in `pixel-routines`, and it only appears in a release build, so it is the one thing here worth verifying rather than hoping:

- `mapping.txt` shows `Event$Kind CAPPED -> CAPPED` and `DECODED -> DECODED` — the constants kept their names. Only the class was renamed (`Event$Kind -> ao`), which is harmless because what gets persisted is `kind.name`, a string.
- All six constant names — `FIRED`, `SKIPPED`, `STOPPED`, `CAPPED`, `LISTENER`, `DECODED` — are present in the release `classes.dex`, so `Event.decode`'s string match still resolves.

**Not proven, and it needs the phone:** that the signed release APK installs and runs on the Pixel. It **cannot install over the debug build** — different keys, `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — so the first release install requires an uninstall, which wipes the armed score and the tuning dials. Do that when the current tuning is already written down, not casually.

**The keystore is now the app's identity.** Losing `thrum-release.jks` or its password means the app can never be updated again: a new key means a new listing, and every existing install has to be uninstalled first. `CLAUDE.md` carries the warning. **The backup has not been taken, and that one is Mutalib's to do — it cannot live on this laptop alone.**

### [~] Task 13 — Play Store listing — **ALL ASSETS DONE 2026-09-28, hosting and the Console still to do**

**Name settled: Thrum.** It already matches `applicationId` (`com.mosman.thrum`), which is permanent on Play once published, so keeping it costs nothing — and renaming later would leave a store name that does not match the identity underneath it. Mutalib's call, made explicitly rather than assumed.

Everything is in `docs/store/listing.md`. Limits confirmed against Play's current rules rather than recalled: **title 30 · short description 80 · full description 4000.**

| Field | Length |
|---|---|
| Title — `Thrum: ringtone you can feel` | 28 / 30 |
| Short description — `Hear your ringtone, feel it too. Needs a phone with a good vibration motor.` | 75 / 80 |
| Full description | 3,049 / 4,000 |

Counted by script, not by eye. **The first draft of this file claimed 2,742 for the full description and said the counts were real, not estimated.** It is 3,049. The claim was wrong, which is exactly what the evidence rule exists to catch.

**The hardware warning is in the short description**, which is the only text that appears in search results. That spends most of the 80 characters on a caveat instead of on keywords, and it is the right trade: Sacred Rule 2 and R4 are about someone installing on a phone that cannot do this and leaving a one-star review. The full description repeats it in its own section, *before* any feature is described.

**Rule review, which is what this task asked for:**

- **Rule 2** — the requirement is in the short description, again in the first section of the long one, naming which phones can and cannot, saying it is physics rather than software, and saying the app checks on first launch and stops. Nobody can install without having been told.
- **Rule 4** — states that the app converts files already on the device, and nowhere claims to provide, download or share music.
- **Rule 3** — the missing `INTERNET` permission is offered as evidence rather than as an assurance.
- **Rule 5** — a whole section headed "What Thrum does not do" names Spotify, YouTube Music, TikTok and WhatsApp calls as permanently out of reach. This is the most common way an app like this earns a one-star review, so it is answered before anyone can be disappointed.
- **Rule 8** — no "haptics" and no "amplitude envelope" outside the section explicitly addressed to people who want the numbers.

**Checked against the built artifact, not just written down.** `aapt2 dump badging` on the debug APK:

```
application: label='Thrum' icon='res/mipmap-anydpi-v26/ic_launcher.xml'
uses-permission: name='android.permission.VIBRATE'
```

**No `INTERNET` permission.** The listing's central privacy claim is checkable, and it checks out. 99 tests, 0 failures.

**Also fixed, because it turned out the app had no launcher icon at all.** No `android:icon` in the manifest and no `mipmap` or `drawable` directory — it was shipping with Android's default, which blocks publication and looks unfinished even for testing. Now an adaptive icon drawn as vector XML: the pulse ribbon, five square bars in the safety yellow on the concrete field. The app's own signature mark, rather than a second idea invented for the icon. minSdk is 31, so adaptive-only is sufficient and there is no legacy PNG mipmap to keep in step; a `<monochrome>` layer is included for Android 13+ themed icons.

**Still to do — and it is no longer the assets:**

- ~~512×512 store icon PNG, exported from the vector~~ — **done**, `docs/store/assets/icon-512.png`, RGBA, checked against the pixel geometry rather than eyeballed
- ~~1024×500 feature graphic~~ — **done**, `docs/store/assets/feature-graphic.png`
- ~~**2–8 phone screenshots. Needs the Pixel**~~ — **done 2026-09-28**, `screenshot-01-armed.png` and `screenshot-02-tuning.png`, both 1080×1920 (9:16) RGBA, taken over wireless debugging. How, and the two traps in re-taking them, are in `docs/store/listing.md`
- **A privacy policy at a hosted URL.** Play requires the URL even for an app that collects nothing. `privacy-policy.html` is written and self-contained; **it still needs to be put somewhere public**
- **Confirm `mutalibusman713@gmail.com` may appear publicly** as the policy's contact address — it is in the written page and has not been signed off
- The Console forms — data safety, notification-access declaration, content rating. Paste-ready text for the first two is in `docs/store/listing.md`, but read the form wording on the day: Play changes labels without changing what they mean

### [ ] Task 14 — Launch

Phase 6 of the pipeline: smoke-test the real journeys on a release build, tag `v1.0.0`, publish.

**Proof:** launch checklist table, pass or fail per row, blockers first.

---

## Deliberately not in this plan

From `PROFILE.md` §5. Do not add them because a sitting went quickly:

per-contact vibration scores (Mutalib's own v2 pick) · the OGG haptic-channel encoder · ring-mode support for imported files · notification and alarm sounds · a score library · a pattern editor · widgets · any server, account, or paid tier.

## Wildcard, not blocking anything

Mutalib wants to try flipping `enableRingtoneHapticsCustomization` over adb on his own phone when he finds a cable. Worth doing for the knowledge, and it would be a nice thing to feel. It changes nothing about the app, which must work with that flag off (Sacred Rule 7).
