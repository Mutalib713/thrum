# Thrum — canonical spec

> This file is the constitution. Every session reads it FIRST, before touching code.
> Sacred Rules (§6) change only with Mutalib's explicit approval.
> The name is settled: Thrum (Task 13). Nothing here is casual.

Started 2026-07-26. Owner: Mutalib (Mutalib713).

**2026-10-03: scope redefined by Mutalib.** Thrum is now the full app: calls, music, videos, My Haptics and export. He approved the rule changes in §5 and §6 the same day ("this is the final build"). The final screens are in `docs/design/screens/`. Everything measured before that date (§11) still stands, and the calls feature it describes is the one already working on his phone.

---

## 1. WHAT

**Thrum turns sound into something you can feel. Hear it. Feel it.**

It started with one job, and that job still comes first: when someone calls, the phone's vibration follows the rhythm of a song you chose, instead of the flat buzz every Android phone makes. iPhones do this; Android leaves it switched off.

Since 2026-10-03 Thrum is also a place to feel your music. It finds the songs on your phone (after asking), plays any of them with a vibration that follows the beat, and can do the same for the sound of a video or an audio file. Every haptic is kept in My Haptics and can be exported.

Underneath, it is one idea. Thrum listens to a track, works out where the beats and the weight are, and saves that as a **vibration score**: one strength value for every 20 ms. On a call in vibrate mode, the score plays through the motor and no sound comes out. In the app, you hear the song and feel the score together, or feel it alone.

## 2. WHO

**Primary user:** an owner of a phone with a good vibration motor (Pixel, Samsung flagship, and similar) who keeps their phone on vibrate most of the time and is annoyed that vibrate mode feels identical no matter which ringtone they chose. Since 2026-10-03, the same person is also someone who wants to feel the music already on their phone.

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

Rewritten 2026-10-03. The layout reference for every screen is `docs/design/screens/thrum-screens.png`; the numbers in brackets are its screen numbers. The original one-screen scope is in git history, and most of it lives on inside items 1 and 3.

1. **Honest phone check first** [1, 22, 25]. Before anything else, check that the motor can change strength. If it cannot, say so plainly and stop, with no "continue anyway" (Sacred Rule 2).
2. **First launch in seven screens** [1–7]: splash, welcome, how sound becomes touch (with a "feel the difference" button), calls/music/videos, calls (asks for call access; "Maybe later" allowed), music, you're set. **Calls come first** in every list and in this order.
3. **One song for calls** [5, 18, 24, 27]. Any haptic can be "used for calls", and choosing another replaces it. A call plays **the first 45 seconds** of it, repeated until the call is answered or ends. It plays in vibrate mode and, while the user leaves the switch on, in ring mode too. Silent mode gets an honest warning, because Android throws the vibration away there. With nothing chosen, calls get Android's normal buzz: **no demo pattern**.
4. **Music** [8–12]. The tab starts empty, with "Scan for music". Thrum asks for music access only when the user taps it, then lists every song and audio file it finds, with search. If access is refused, the user can still pick one song at a time.
5. **Haptics for songs** [10, 11, 13]. After a scan, Thrum asks: make every song's haptic now, **in the background**, or one at a time as each is first played. Either way, a song played before its turn jumps the queue. The choice can be changed in Settings, and progress shows as "2 of 5 done".
6. **The player** [14]. Plays the whole song with its haptic, "hear and feel" or "feel only", with previous and next. It is also each song's page: tune it, use it for calls, export it.
7. **Videos and audio files** [16]. One at a time, through the phone's own file chooser. Thrum reads the audio track of a video, and never asks for access to all videos.
8. **My Haptics** [19]. Every haptic in one list, filtered by All, Music, Videos, Files.
9. **Tune the feel** [15]. Three presets (Crisp, Full, Strong) and three dials: Intensity (was Punch), Focus (was Distance), Duration (was Body).
10. **Export and import** [16, 17]. Export one haptic or all of them as Thrum pattern files saved on the phone; songs without a haptic get one first. Import opens a Thrum file. **Only the vibration is exported, never audio.** "A ringtone with the vibration inside" is shown as not built yet: that is the v2 encoder.
11. **Home** [18]. The song for calls with its rhythm, "Feel a test call", the last call, shortcuts to Music and Create, and recently played songs.
12. **Settings, About, Phone check** [20–22]. The founder story lives in About, not in first launch.
13. **Look.** The current colours (sulphur-concrete), drawn flat. Liquid glass is wanted and gets discussed after everything else (Mutalib, 2026-10-03). Follows system light/dark.
14. **Thrum Originals** [3, 7, 8, 11, 27]. Added later on 2026-10-03. A few short pieces built into the app, owned outright, each shipped with a ready-made haptic so it plays instantly. First launch lets people hear and feel one *before any permission*. They sit at the top of the Music tab before and after a scan, and one can be the song for calls on day one. Working titles: Afro Groove, Heartbeat, Pulse, Energy. **Who makes them is still open** (§11 R14). *Resolved later the same day: Mutalib chose the open collections — curated rather than commissioned — and this item merged with item 15 into one built-in collection (see below and `docs/research/open-collections.md`).*
15. **The online music catalog** [28–31]. Added at Mutalib's request on 2026-10-03. A "Catalog" side of the Music tab: search songs, artists or sounds, browse by genre (Afrobeats, Amapiano, Highlife, Gospel, Hip-hop, Electronic), and play any song from it with its haptic. A catalog song can become a saved haptic or the song for calls. **Only the vibration is kept on the phone**; the song itself streams from the catalog, so Thrum never stores anyone else's audio (Sacred Rule 4). Offline, the Catalog says so plainly and everything else keeps working. **Blocked on two decisions:** it needs the internet, which Sacred Rule 3 forbids (§6), and it needs a licensed source of music (§11 R15). *Both resolved by Mutalib on 2026-10-03, by removing the internet from the question: **the catalog stays offline** and merges with the Thrum Originals into one **built-in collection** — open-licence music and ringtone collections, curated, each shipped with a haptic made by the same analyser, searchable by name and browsable by genre, all inside the APK. Nothing streams, nothing is downloaded, the permission screen's promise stands. The candidate sources and their licence terms are researched in `docs/research/open-collections.md`; only audio the licence lets Thrum ship may enter (Rule 4). Nothing has been picked yet.*
16. **Clean up the sound** (audio enhancement) [32, 33]. Added at Mutalib's request on 2026-10-03. When a song is low quality (Thrum can tell from its bitrate), the player offers to clean it up. It runs on the phone with nothing uploaded, lets the user compare original and cleaned up, and keeps whichever they choose. The haptic is made from the original, because the 2026-10-03 measurement shows cleaning up barely changes it (§11 R16): this is a listening feature. The app's wording says "Clean up", not "Enhance", because that says what it does. How it's done is an open choice (§7).

## 5. NOT IN V1

Rewritten 2026-10-03, when ring mode, a library of haptics, music, videos and export moved *into* v1 with Mutalib's approval. These stay out, and no session builds them "helpfully":

- **No ringtone files with the vibration built in** (the OGG haptic-channel encoder). Export shows it as "not built yet". It is v2, and still the hardest part of the project.
- **No per-contact vibration.** Mutalib deferred it ("i will try the who is calling one later"). It is the strongest v2 candidate.
- **No music from other apps.** Spotify, YouTube Music, TikTok and WhatsApp calls stay out of reach (Sacred Rule 5). Thrum plays only files that are on the phone, inside Thrum.
- **No scanning of videos or photos.** Videos are picked one at a time.
- No drawing or editing a pattern by hand. Tuning, yes; a pattern editor, no.
- No accounts, no login, no server, no cloud, no analytics, and no syncing between phones beyond the user exporting a file.
- No notification sounds, alarms or messaging-app vibrations.
- No widget, no Quick Settings tile.
- No paid tier, no ads, no in-app purchase.
- **No ripping or downloading music from YouTube, Spotify or any source Thrum isn't licensed for.** The online catalog (§4, item 15) carries only music Thrum has the right to play, and keeps only the vibration on the phone.

*Moved into v1 on 2026-10-03, so no longer exclusions:* ring mode, a library of saved haptics (My Haptics), sharing scores as exported files, and music beyond the one ringtone.

## 6. SACRED RULES

Decisions no future session may reopen without Mutalib saying so:

1. **One song for calls at a time, and calls come first.** v1 is the app described in §4 and drawn in `docs/design/screens/`; anything not on those screens is v2.
2. **Never let a user believe the app works when their hardware cannot do it.** The capability check runs before anything else and its verdict is honest, not hedged. A one-star review saying "does nothing" is worse than a user who never installs.
3. **Everything runs on the phone. No server, no network, ever.** No audio, score or file leaves the phone unless the user exports it themselves, and an export never contains audio. This keeps running costs at zero regardless of user count, and means there is no privacy story to get wrong.
4. **Never ship audio Thrum doesn't own outright, and never host or redistribute anyone else's.** The app converts files that are already on the user's device, plus the Thrum Originals, which Thrum owns. Stock ringtones are Google's property; converting one locally for personal use is fine, distributing it is how apps get pulled.
5. **Never claim or attempt system-wide audio haptics.** Android gives no app access to another app's audio. Spotify, YouTube Music, WhatsApp calls and TikTok are permanently out of reach. Thrum's own player only plays files that are on the phone, inside Thrum. Do not design around a workaround for this; there isn't one.
6. **Prove it on hardware before building around it.** Every assumption about how the system ringer behaves gets tested on a real phone with a real incoming call. Emulators cannot test vibration and this machine has none anyway.
7. **The feature flag is not a strategy.** `enableRingtoneHapticsCustomization` may be flippable via adb on Mutalib's own phone, but a Play Store app can never flip it. The app must work with that flag OFF.
8. **Plain-language first, always** — in the app's copy and in every conversation about it. See the user memory `explain-plainly-always`.

**Changed with Mutalib's approval on 2026-10-03**, when he made the full app "the final build":

| Rule | Was | Now |
|---|---|---|
| 1 | v1 ships one screen and one active vibration score. If a feature does not fit that, it is v2. | One song for calls at a time, and calls come first. v1 is §4 and the final screens. |
| 3 | No audio, no score, and no file leaves the device. | Nothing leaves the phone unless the user exports it, and an export never contains audio. |
| 5 | (unchanged in meaning) | Adds that Thrum's own player only plays files on the phone, inside Thrum. |
| 4 | Never ship, host, or redistribute audio. | Never ship audio Thrum doesn't own outright, and never host or redistribute anyone else's. *(Changed later the same day, when Mutalib said yes to Thrum Originals.)* |

Rules 2, 6, 7 and 8 are untouched.

> **Resolved 2026-10-03, Mutalib's decision.** He chose to keep Rule 3 exactly as written — the app stays offline — and the catalog became **built-in content** instead: a searchable collection of open-licence sounds and ringtones bundled with the app at build time, each with a ready-made haptic (§4 item 15). Nothing streams, nothing is downloaded, and the permission screen's promise stands unchanged. The candidate sources and their licence terms are researched in `docs/research/open-collections.md`; only audio the licence lets Thrum ship may enter the collection (Rule 4).

## 7. STACK & ARCHITECTURE

Deliberately mirrors `pixel-routines`, because that stack is already proven to build on this machine.

| Piece | Choice | Why |
|---|---|---|
| Language | Kotlin | Proven on this machine |
| UI | Jetpack Compose + Material You dynamic colour | Matches Pixel UI; Mutalib's stated preference; follows system light/dark |
| Build | AGP 9.2.1 **built-in Kotlin** + compose plugin 2.2.20 | ⚠ Do NOT apply `org.jetbrains.kotlin.android` — it collides. See §10 |
| SDK | compileSdk 36, targetSdk 36, **minSdk 31** | API 31 = Android 12, the floor for the haptics work this depends on |
| Storage | SharedPreferences + JSON for the call settings; **Room for the haptic library** (Mutalib's pick, 2026-10-03) | One score does not need a database; hundreds do — lists and search are exactly what Room is for. Task 17 builds it |
| Audio decode | `MediaExtractor` + `MediaCodec` → raw PCM | Platform APIs, no third-party library, no NDK |
| Analysis | Pure Kotlin DSP (low-pass + envelope follower) | **Pure Kotlin = unit-testable on this PC with no emulator.** This is the main verification lever |
| Vibration out | `VibrationEffect.createWaveform(timings, amplitudes, repeat)` | Amplitude control on an LRA gives real expressiveness; no encoder needed |
| Song playback | **Media3 (ExoPlayer)** — Mutalib's pick, 2026-10-03, over the MediaPlayer lean | The better base for lock-screen controls, playback with the app closed and any future streaming. Costs a bigger library; the preview keeps MediaPlayer until Task 21 migrates it |
| Call detection | `NotificationListenerService` | Sees the Phone app's incoming-call notification. **Avoids `READ_PHONE_STATE` and avoids replacing the dialer**, both of which are Play Store friction. Already proven in `pixel-routines` (`NotifService`) |
| Network | none | Sacred Rule 3 |
| Package | `com.mosman.thrum` | Matches `com.mosman.routines` convention |

### Open decisions — Mutalib chooses (added 2026-10-03)

The full app needs five things the one-screen app did not. Each is his choice; nothing below is decided until he picks, and only then does it move into the table above.

**Decided 2026-10-03, Mutalib:** storage **Room**; background work **WorkManager**; song playback **Media3** (his pick, recorded in the table above); clean-up **plain sound processing**. The catalog's direction is decided too, and it removes the internet question entirely — see §4 item 15 and §6. The table below is kept as the record of what each choice costs.

| Need | My pick, in plain words | Alternative | What each costs, and what it can't do |
|---|---|---|---|
| **Somewhere to keep hundreds of haptics** | **Room**, Android's own database library. Built for exactly this: lists, search, "which songs have a haptic yet". | One file per haptic in the app's private folder, plus a small index file. | Room adds a library and a code-generation step to the build. Plain files are simpler, but searching and sorting get slow and fiddly past a few hundred. The SharedPreferences used today cannot hold this much. |
| **Making haptics in the background** | **WorkManager**, Android's standard way to run long jobs that survive the app closing. | A background service the app runs itself. | Android can delay WorkManager jobs to save battery, and very long runs may need a visible notification. A hand-made service is more code and easier to get wrong. |
| **Playing whole songs** | Keep **MediaPlayer**, which the preview already uses. | **Media3 (ExoPlayer)**, Google's newer player library. | MediaPlayer covers play, pause and seek on local files. Media3 is the better base for lock-screen controls, playback with the app closed and streaming from the catalog, at the cost of a large library. |
| **Cleaning up the sound** | **Plain sound processing**: level the volume, lift the clarity, soften harsh squashed sound. Small, fast and predictable. | **An AI model on the phone** that guesses the missing detail. | Plain processing can't rebuild what a squashed file threw away; it only makes it sound better. An AI model can try, but it makes the app much bigger and slower, and it can invent strange sounds. A model on a server is ruled out: it would upload the user's music. |
| **Where the catalog comes from** | Decide after research: open-licence catalogs that let apps play their music, or a paid licensing service. | Thrum Originals only, made bigger. | Open-licence catalogs are mostly independent artists, not stars. A paid licence covers famous artists but costs money and needs a contract. Either way it needs the internet (§6). |

### Why a call needs no audio player

In vibrate mode the phone produces no sound, so there is nothing to synchronise against. The app only needs to play the right rhythm at the right moment, which the vibrator API does directly. This removes the entire audio-encoding problem from v1.

The Music player is a separate thing: there the user *is* listening, and the score plays alongside the song, kept in step with it (Task 5 already does this for the preview).

### Flow

```
first launch → phone check → (motor can't change strength? honest stop, no button)
     → welcome → sound becomes touch → calls, music, videos → calls (call access) → music → you're set

tabs:  Home ⇄ My Haptics ⇄ Music ⇄ Settings

Music:  Scan (asks first) → song list → play → haptic made now, or already made in the background
        → player: hear and feel / feel only → use for calls · tune · export
Create: a video or audio file (file chooser) or a Thrum file → haptic → My Haptics

decode to PCM → low-pass → envelope → vibration score   (same engine for every source)

incoming call + ringer on vibrate (or ring, switch on) → first 45 s of the song for calls, repeated
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

**`Settings`** (the first draft's list; as built, `Store.kt` holds `fire_in_ring_mode`, `loop_while_ringing`, `armed_score`, `source_uri`, `punch`, `distance`, `body` and the event log)
- `armed: Boolean`
- `onboarded: Boolean`
- `capabilityVerdict: String` — cached result of the hardware check
- `repeatScore: Boolean` — loop the score for the length of the call

**Added 2026-10-03 for the full app.** Where these live is an open decision (§7):
- **`Track`** — a song or audio file found by the scan, or a video or file the user picked: display name, artist, duration, source URI, kind (music / video / file), and whether this phone can read it.
- **`Haptic`** — the score for the *whole* track, the tuning it was made with, and when it was made. A call uses its first 45 seconds. A whole song can be longer than the vibrator accepts in one go (§11 R10), so playback splits it into pieces.
- **`Settings`** gains: the song for calls, how haptics get made (in the background / as played), music access, and the last scan.

## 9. INTEGRATIONS & KEYS

**None. Zero.** No API keys, no env vars, no secrets, no `.env`, no network permission in the manifest.

DRY_RUN is not applicable — nothing sends a message and nothing spends money. The equivalent safety rule for this project:

> **The app never changes a system setting on the user's behalf.** Where a system setting must change, the app explains why in plain language and sends the user to the settings screen to do it themselves. Silently editing someone's phone settings is how an app becomes a thing people uninstall angrily.

Permissions, updated 2026-10-03:
- `VIBRATE`.
- **Call access**: notification listener access, asked on the calls screen with a plain reason; "Maybe later" is allowed.
- **Music access**: `READ_MEDIA_AUDIO` on Android 13 and up, `READ_EXTERNAL_STORAGE` on Android 12. Asked only when the user taps "Scan for music". Refusing it still leaves "pick one song at a time".
- **Nothing for videos or photos.** Videos come through the phone's file chooser, one at a time.
- Whatever background work and playback need (foreground-service permissions, possibly notifications) gets settled in the build tasks and listed here then.
- Still **no `INTERNET`**. The online catalog would need it, which is the open Rule 3 decision in §6. Thrum Originals ship inside the app rather than being downloaded. At a decent quality a 30-second piece is roughly half a megabyte, so six of them add about 3 MB; the release APK is 1.98 MB today.

## 10. CONSTRAINTS

**Build machine:** replaced on 2026-07-29. The notes that used to be here (a weak Windows 10 PC, Avast breaking HTTPS, four-minute builds) describe the old laptop and no longer apply. `CLAUDE.md` holds the current setup: command-line Gradle through `check.ps1`, `JAVA_HOME` set in the shell, and no Avast. An emulator changes nothing for this app, because emulators cannot vibrate.

**Testing:**
- Vibration cannot be tested on an emulator, and there is no emulator anyway. Every haptic claim needs Mutalib's physical Pixel 6 Pro.
- Mitigation: the analysis layer is pure Kotlin and gets real unit tests that run on the PC in seconds. Only the playback layer needs hardware.

**Hardware reality (the product's ceiling):**
- Needs an actuator with amplitude control. Budget phones (most Tecno, Infinix, itel, low-end Samsung) have a spinning-weight motor that can only buzz. **The app will do nothing useful on those, by physics.**
- This is why the audience is worldwide flagship owners, not Mutalib's local circle.

**Distribution:** Google Play. Notification-listener access requires a clear disclosure in the listing and in-app. Budget for review friction. The full app adds music access and background work, so the store listing and its data-safety answers (`docs/store/listing.md`, written for the one-screen app) must be redone before release.

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

**R2 — Can the system's own buzz be suppressed?** ✅ **Resolved by Task 2, 2026-08-01.** It does not need to be suppressed: **the last vibration wins.**

Telecom starts its flat pattern when the call arrives; our waveform reaches the vibrator ~0.4–0.5 s later and supersedes it. Android hands the motor to the most recent `RINGTONE` vibration, so the system's buzz is cut off mid-pattern. Verified on every one of 13 real calls, in the system's own record:

```
06:00:07.686  com.android.server.telecom   cancelled_superseded     527ms
06:00:08.213  com.mosman.thrum             cancelled_by_user       3267ms
```

Mutalib's verdict on the same calls: *"a clear rhythm."* No mush.

- **(a) was never needed.** `vibrate_when_ringing` stayed **on** and `ring_vibration_intensity` at 3/3 throughout, and it made no difference — supersede beats them all. Users change nothing. Task 9's setup screen shrinks accordingly.
- **(b) is dead. Silent mode is not a blank canvas — it is a wall.** Android discards a `RINGTONE` vibration outright in silent mode: `ignored_for_ringer_mode`, duration `0ms`. Mutalib felt nothing, and was the one who caught it — *"the silent mode is just silent, nothing else."* **Never tell a user to switch to silent mode; it disables the app.**
- **(c) verified irrelevant**, as suspected. Left on for all 13 calls with no effect on our waveform.

**Two consequences to design around:**
1. **Every call opens with ~0.4–0.5 s of the system's flat buzz** before the rhythm takes over. Not suppressible from an app — the only lever is our own latency, so anything that delays the listener lengthens the flat prefix.
2. **There is no fallback left.** If a phone ever suppresses us in vibrate mode too, the app has nothing to retreat to. Task 6's honest-hardware-check screen is the whole answer on such a device.

**Method note:** `adb shell dumpsys vibrator_manager` is the ground truth for any haptics question — it records every vibration, who requested it, and what the system did with it. The app's own `FIRED` event only proves we *asked* the motor. Task 2 recorded "silent mode works, 240 ms" off that event for a full day before a hand on the phone disproved it.

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

**R8 — Waveform length limits. ✅ RESOLVED 2026-08-01, and the failure mode is worse than feared.**

There is a cap, and **it is silent**. `createWaveform` accepts an oversized score without complaint, `vibrate()` throws nothing and returns nothing, and the request then fails crossing into the system process — `FAILED_TRANSACTION` in the binder log, nothing at all in the app. A 3:58 track at 20 ms is 11,922 steps, and it simply never vibrated while the app logged a successful play.

Measured on the Pixel 6 Pro by walking a ladder of step counts and reading `dumpsys vibrator_manager` afterwards, since the app itself cannot tell which ones arrived. Two runs agreed exactly:

| steps | reached the motor |
|---|---|
| 500 · 1,000 · 1,500 · 2,000 · 3,000 · 4,000 · 6,000 · 8,000 · 9,000 · 9,500 · 10,000 · 10,500 | ✅ |
| 11,000 · 11,500 · 12,000 | ❌ |

*Mitigation, all three in place:* `Haptics.MAX_STEPS` is **8,000**, not the measured 10,500 — that binder buffer is shared across the whole process, so a limit measured on an idle phone is an upper bound, not a safe one. `Haptics.play` refuses anything longer and says so, instead of letting the app believe it vibrated. `Score.fitWithin` coarsens until it fits, rounding the factor **up** (rounding down lands one group over the limit and fails invisibly, which is the bug being defended against). Scores are fitted when built, so an unplayable one is caught while its step count is still on screen rather than at the moment a call arrives.

8,000 steps is 2 minutes 40 at 20 ms, and a phone rings for about thirty seconds — roughly 1,500 steps. The margin costs nothing real.

**The probe that found this is kept** on the debug screen, because the limit is per-device and Task 6 will meet other phones.

**R9 — The feel is still a hum. ⚠ The first thing to fix.** Measured 2026-09-28 (`docs/device/2026-09-28-vibration-measurement.md`): the motor really does shake the phone, peaking at 1.75 g sideways. But the armed score kept it running 89.6 % of the time at a near-constant 0.84 of full strength, and a hand reads that as a flat hum rather than a beat. Every new feature plays the same kind of score, so a hum in calls becomes a hum in music too. Direction from the measurement: more silence and contrast, not more strength.

**R10 — Whole songs are longer than the vibrator accepts in one go.** The whole AIZO track is 8,862 steps at 20 ms. Android silently drops a waveform somewhere between 10,500 and 11,000 steps on this phone, and Thrum caps at 8,000 (R8). So the player must play a song in pieces and keep each piece in step with the audio. Nothing about that is proven yet.

**R11 — Vibration with the screen off.** Nobody has tested whether the haptic keeps going while music plays with the screen off, or with Thrum in the background. Android may limit it. Test it on the phone before building the player around it (Sacred Rule 6).

**R12 — Background work costs battery, and Android may hold it back.** Reading a song took 7.4 s for a 3:58 MP3 and 10.1 s for a 4:32 M4A on the Pixel 6 Pro (Task 3), so a few hundred songs is tens of minutes of work. Mutalib chose "in the background" over "only while charging" (2026-10-03), so the battery cost has to be honest in the app, and the job has to survive Android pausing it.

**R13 — Play Store review.** Music access, background work and call access each need a plain reason in the listing and in the app. Video access is avoided on purpose: videos are picked one at a time.

**R14 — The Thrum Originals don't exist yet.** Who makes them is open: Mutalib himself, a producer under a written agreement (a Ghanaian Afrobeats producer would suit the app), or public-domain (CC0) sound. Whoever it is, Thrum must own them outright, because many "royalty-free" licences don't allow handing the track itself to users. They should be made to be felt: a clear kick with gaps between beats. That also makes them the test material for the hum fix (Task 15). *Updated 2026-10-03: the path chosen is the open-licence collections (R15) — Thrum curates and verifies licences rather than commissioning, and the Originals merge with the old catalog into one built-in collection. A commissioned Afrobeats piece remains open as a later addition, because the open world has almost none of those genres. The "made to be felt" test is now measurable: candidates go through the analyser and `Feel.of()` on the PC, and only tracks whose haptic reads as a rhythm headline the collection.*

**R15 — The catalog's music has to be legal.** Searching for any song and downloading it would break copyright and Google Play's rules, and Thrum won't do it. A legal catalog means either an open-licence catalog that lets apps play its music (mostly independent artists) or a paid licence (famous artists, but money and a contract). Which services exist, what they allow and what they cost has to be researched from real sources before anything is chosen. Nothing here is settled. *Updated 2026-10-03: the question shrank when the catalog went offline (§6) — no streaming licence is needed, only a **redistribution** licence for bundled files. The research is done and recorded in `docs/research/open-collections.md`: CC0 sources (OpenGameArt, Kenney, itch.io CC0 packs, Freesound, Wikimedia Commons, Musopen) are the clean tier; CC-BY would need a Rule 4 amendment plus a credit in About; **Pixabay is ruled out** — its licence forbids redistributing audio "as part of a … collection product", which is exactly what the collection is; firmware rips and ringtone sites were never candidates. Nothing has been downloaded yet, and no source is final until its own licence page is read and recorded in the ledger.*

**R16 — Cleaning up the sound doesn't improve the vibration.** Measured on 2026-10-03, so the feature is designed around the result. Copies of AIZO squashed to 64 kbps and 32 kbps gave almost the same haptic as the 256 kbps original: of the original's 582 beats, 92 % and 88 % landed within 20 ms, and 96.5 % and 94.7 % of steps stayed within ±10 of the original strength. The motor works around 150 Hz (measured on 2026-09-28), and the beat finder listens mostly below 200 Hz, while squashing mostly removes high sounds. So the haptic is made from the original file, and cleaning up only changes what the user hears. `tools/measure/quality.py` reproduces the numbers.

**The name — settled.** Thrum, decided in Task 13. It matches the permanent `applicationId`, `com.mosman.thrum`.

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

**Added for the full app (2026-10-03).** New pure-Kotlin tests, so the arithmetic is proved on the PC before any phone is involved:
- the call window is exactly the first 45 seconds of a whole-song haptic;
- a whole-song score split into pieces under `Haptics.MAX_STEPS` keeps every step once: none dropped, none doubled;
- the haptic queue lets a song that is played jump ahead, and never makes the same song twice;
- export, then import, gives back an identical score.

**Before any release build:** the capability check must be verified against at least one incapable device, not just Mutalib's Pixel. Borrow a budget phone.

**Evidence rule:** no task is done on a claim. Test output, a measurement, or Mutalib's own hand on the phone. Screenshots are unreliable on this machine — prefer numbers and logs.
