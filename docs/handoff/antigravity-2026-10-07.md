# Antigravity's part of the plan, from 7 October 2026

**For Mutalib, in plain words.** The task list in `PLAN.md` (Tasks 31–39) is split in two. Claude does the parts that are hard to get right underneath: calls, the ringtone, timing and measuring. Antigravity does the wording round and the whole visual round: light mode, liquid glass, the dial and the bars. Antigravity asks you every design question itself, shows you the options side by side, and waits for your pick before building.

**Start when Mutalib says so.** He'll tell you; until then, read and ask questions only.

**For Antigravity.** Read these first, in this order: `C:\Users\USER\MyClaudeProjects\AGENTS.md` (workspace rules), `thrum/CLAUDE.md`, `thrum/PROFILE.md` (canonical; §4 says what each screen does, §6 holds the Sacred Rules), the task list at the end of the 7 October entries in `PLAN.md`, then this file. If anything here disagrees with `PROFILE.md`, `PROFILE.md` wins. Ask Mutalib, don't guess.

## Who does what

| Task | What | Who |
|---|---|---|
| 31 | One ringtone action ("Use for calls" and "Set as ringtone" become one) | Claude |
| 32 | Catching calls with the Phone permission (research and a test) | Claude |
| 33 | Getting Thrum to friends (depends on 32) | Claude |
| 34 | The sound-to-vibration gap in the player (measuring) | Claude |
| 35 | Instant Feel (a first play starts after about 1.4 s) | Claude |
| **36** | **Wording round** | **Antigravity, first** |
| 37 | "Your phone's ringtones" list | Claude (it has two data traps, below) |
| 38 | The haptic-channel test file | Claude |
| **39** | **The visual round: light mode, liquid glass, the dial, the bars** | **Antigravity, after 36** |

Why Task 37 isn't Antigravity's: the Music scan removes any scanned song it no longer finds (`Track.missingAfterScan`), and it deliberately skips ringtones. A ringtone added to the library would be deleted by the next scan. And `MyHaptics.isMade` treats anything that isn't `TrackKind.MUSIC` as made by the user, so a new kind of track would wrongly appear in My Haptics. Both need changes in the data layer, which is Claude's this week.

## Rules that apply to every step

**Git**
- One branch per task, made from the current tip of `ui-final-screens`: `ag/task-36-wording`, then `ag/task-39-look`. Run `git pull` on `ui-final-screens` and start from it fresh before each task. Never commit to `main`.
- Claude works on its own branches at the same time. When a task is finished, gated and approved by Mutalib, merge it into `ui-final-screens`. If `strings.xml` conflicts, keep both sides' strings.
- Commit **and push** after every meaningful step, and say honestly in the message what state it's in.
- **Never** add AI attribution to anything: no `Co-Authored-By`, no "Generated with", no "Antigravity" in commits, PR titles, PR bodies or file headers.

**Files that are Claude's this week. Read them, don't edit them:** `NotifService.kt`, `Ringtone.kt`, `RowActions.kt`, `LibraryActions.kt`, `Player.kt`, `PlaybackService.kt`, `Haptics.kt`, `Analyser.kt`, `Score.kt`, `Haptic.kt`, `HapticMaker.kt`, `HapticQueue.kt`, `HapticsWorker.kt`, `AudioDecoder.kt`, `MusicScan.kt`, `Track.kt`, `MyHaptics.kt`, `LibraryDb.kt`, `Tuning.kt`, `DeveloperTools.kt`, and `AndroidManifest.xml`. In `Store.kt`, only add the theme key that Task 39 needs. If a visual change seems to need one of these files, stop and tell Mutalib, so he can pass it to Claude.

**Strings that are Claude's this week. Don't reword them:** everything about calls and the ringtone (`home_calls_*`, `call_*`, `calls_*`, `ringtone_*`, `ring_mode_*`, `settings_song_for_calls`, `player_using_calls`, `onboarding_calls_*`, `onboarding_done_body`) and `make_as_played_help` (Task 35 changes it).

**The checks before calling anything done**
1. `powershell -File C:\Users\USER\MyClaudeProjects\thrum\check.ps1` must print `CHECK PASSED`. It runs the unit tests and a debug build.
2. Android lint stays at 0 errors: `.\gradlew.bat :app:lintDebug` (set `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'` first).
3. Every new or changed user-facing string lives in `res/values/strings.xml` and passes the humanizer gate. Copy the new strings' text into a `.md` file in a scratch folder, then run `python C:/Users/USER/.claude/skills/humanizer/scripts/gate.py <file.md> --profile ux-microcopy`. Tell Mutalib the score.
4. The design gate on changed Kotlin files: `python C:/Users/USER/.claude/skills/design-studio/scripts/gate.py <files>`. Fix every BLOCK. Don't trust `impeccable detect` on `.kt` files: it can't read Kotlin and reports a false all-clear.
5. Logic that can live outside the screen (a theme choice turning into light or dark, a count) goes in plain Kotlin with a unit test in `app/src/test`.

**Design rules** (from `C:/Users/USER/.claude/skills/design-studio/SKILL.md`; read its §3 Taste core)
- **Colours are pinned: sulphur-concrete, Mutalib's pick.** Use only the tokens in `Theme.kt` (`DarkPalette`, `LightPalette`, the two colour schemes). No new hex values. If glass needs a translucent colour, make it from an existing token with alpha. If a genuinely new colour seems needed, ask him, and derive it with `python C:/Users/USER/.claude/skills/design-studio/scripts/palette.py --seed "<an existing token>"`, never by taste. Light mode already exists and was designed, not inverted (`Theme.kt` line 59 onward).
- Type: Space Grotesk and IBM Plex Sans, as in `Theme.kt`. Icons: `ThrumIcon` in `Icons.kt` (vector, 24×24, 1.8 stroke). **No emoji anywhere in the UI.**
- **Never show sample data as if it were the user's.** The 4 October review (`docs/review/2026-10-04-ui-branch-review.md`) caught screens showing the design board's example songs as the user's own. Empty states say why they're empty.
- No network, ever (Sacred Rule 3): the app has no INTERNET permission, so no remote images or fonts.
- Respect "Remove animations" (Android's accessibility setting; animator duration scale 0): animations become still.
- Body text contrast at least 4.5:1, worked out from the actual colours, including text sitting on glass.
- Touch targets at least 48 dp.

**How to ask Mutalib** (`AGENTS.md`, his `CLAUDE.md`)
- Plain words first, then the technical version. Define every term the first time.
- Show options **side by side in one image, labelled with letters (A, B, C)**, say which you'd pick and why, then wait. Options should differ in structure, not just colour, and be filled with real content (his real song names, real rhythm data).
- Rough early shapes can be HTML in `thrum/scratch/` (gitignored). **The final pick renders in real Compose**, because HTML text and spacing don't match the phone. Use the "Look lab" described in Task 39.
- If he sends a reference image, **measure it** (PIL, Playwright, `python C:/Users/USER/MyClaudeProjects/_tools/trace_measure.py measure|trace`). Don't describe it in words and build from the description. If he says "not close" twice, stop and change the method.
- After building, screenshot at the same size as the reference and run `trace_measure.py diff <reference.png> <built.png>`.
- End every task with a plain walkthrough: the problem, how it was solved, the one idea that made it work, and what went wrong along the way.

**Devices**
- The Android 14 emulator `emulator-5554` is **shared** with other sessions and with Mutalib's Wird work. Check which app is in front before tapping, and put Wird back in front when you're done.
- An emulator has no vibration motor. Anything about feel needs Mutalib's Pixel 6 Pro over USB, and only when he says he isn't using it.
- Judge smoothness on a **release** build (`.\gradlew.bat :app:installRelease`). Debug builds ran about 10× slower in Wird. A release build can't install over the debug build (different keys); ask Mutalib before uninstalling, because uninstalling wipes his song for calls and tuning.

## Task 36: Wording round (first, small)

Four wording changes Mutalib approved after studying Sound To Haptics (`docs/research/sound-to-haptics.md`).

1. **"Kick drum → the main beat · Snare and hi-hats → extra taps."** A two-line legend saying what Thrum feels, matching what the analyser does and what the Extra taps switch controls. Thrum doesn't pick out vocals, so never claim it does. Where it goes is his call. Suggest the onboarding page "Turn sound into touch" (`onboarding_touch_*`) and the top of the Tune screen (`tune_intro`). Draw the arrows as `ThrumIcon` or plain text arrows, not emoji.
2. **"3 ready to feel"** as the My Haptics subtitle: a real plural (`<plurals>`) counted from the list the tab actually shows. With none, show the existing empty state, not "0 ready to feel".
3. **"Replay the introduction"** in Settings, in the "This phone and Thrum" section. It shows `OnboardingFlow` again and comes back to Settings. It must not reset anything: not `store.onboarded`, not permissions, not the song for calls, not the library. The flow's call-access step should cope with access already being on.
4. **File types under "From audio"** (`make_audio_help`): list only types confirmed to read on a real file. Pick one file of each type through "From audio" on the emulator and on his Pixel if he's free. Candidates: MP3, M4A, WAV, OGG, FLAC. Leave off any that fail. Note which ones were tested in the commit message.

**Done when:** all four are in the app; the humanizer gate passes with the score reported; "Replay the introduction" goes through the whole flow and back with the song for calls and the library untouched; the count shows the real number (checked with 0, 1 and several); `check.ps1` passes and lint shows 0 errors; Mutalib has seen the screens; the Task 36 line in `PLAN.md` says what was done and the proof.

## Task 39: The visual round (after 36)

The sketches already exist, all gitignored in `thrum/scratch/`, made on 7 October. Open them in a browser:

- `glass-options-live.html` / `glass-options.png`: liquid glass placed three ways (**A** on floating controls only, **B** on every card like his two Pinterest references, **C** in between), each in light and dark, plus three theme switches (**T1** a sun/moon button on Home, **T2** "Follow phone · Light · Dark" in Settings, **T3** a liquid "Dark mode" switch).
- `dial-options-live.html` / `dial-options.png`: the player's dial nine ways, **A–I**, taken from his Pinterest "beats" board.
- `pinterest-beats/beats-board-animations.mp4` and `montage-still.png`: his saved pins, recorded off his phone, numbered.
- `docs/design/screens/scores.js`: real rhythm data for sketches. `design-brief.md` and `docs/design/taste/` hold the earlier design direction.

Claude's recommendations, for him to accept or overrule: **glass A** (Apple's own guideline: glass belongs on controls and navigation, never on content, and never glass on glass), **theme T2**, **dial G** (main beat on the outside ring, extra taps inside), runner-up **I**. **Ask him all of these. He picks.**

Do it in this order, one piece at a time, each one picked, built, gated and committed before the next.

### 39a. The Look lab (do this first)
A test-build-only screen, opened from Developer tools, that shows the candidate versions of a component in real Compose with real data, so he picks on his own phone instead of from a web page. Put it in a new file (`LookLab.kt`) and add only the one line that opens it to `DeveloperTools.kt` (the one exception to the file rule above). Release builds never show it (`BuildConfig.DEBUG`, as Developer tools already does). No new library for this.

### 39b. Light and dark switch
- Ask: T1, T2 or T3.
- Store his choice as Follow phone / Light / Dark (a new key in `Store.kt`, default Follow phone). `MainActivity` passes it to `ThrumTheme(dark = ...)`. The status bar and navigation bar icons must flip with it.
- Don't expose the `wallpaperColours` option: the palette is pinned.
- **Done when:** the choice survives closing and reopening the app; every screen (Home, Music, My Haptics, the player, Tune, Settings, Phone check, export, onboarding, the dialogs and menus) has been looked at in both modes, with screenshots in both; body text is at least 4.5:1 in both; there's a unit test for the choice turning into light or dark.

### 39c. Liquid glass
- Ask: A, B or C, with his two references and the sketch side by side.
- **Adding a library is his decision (a stack choice). Present these and let him choose:**
  1. **Haze** (`chrisbanes/haze`, Apache-2.0, 2.0.1 released 2026-09-29, updated 2026-10-06 when checked). Blurs whatever is behind a surface; version 2 adds ready-made Glass styles and a fallback for phones that can't blur. Closest to frosted glass.
  2. **Backdrop / AndroidLiquidGlass** (`Kyant0/AndroidLiquidGlass`, Apache-2.0, 2.0.1 released 2026-08-26). Draws the bending, lens-like edge of Apple's Liquid Glass, so it's closest to "the one on iPhone". It only gives you the effect, not ready-made parts: you build the buttons and tabs yourself from its examples. **Check the lowest Android version it supports against Thrum's (Android 12, minSdk 31) before suggesting it.**
  3. **No library:** a see-through tinted surface with a thin light edge and no real blur. Nothing to add, cheapest on budget phones, and the least like glass.
- Claude's suggestion: try 1 and 2 on placement A in the Look lab on his Pixel, on a release build, and let him feel which one looks right and scrolls smoothly.
- Glass sits on the floating layer only (the bottom navigation, the mini player and floating buttons for A) unless he picks B or C. Never glass on glass.
- Text on glass: 4.5:1 against the worst background that can scroll under it. Measure it on screenshots with a bright and a dark song cover behind.
- Performance (the Ghana floor: budget phones and slow networks): measure `adb shell dumpsys gfxinfo com.mosman.thrum` while scrolling Music, before and after glass, on a release build. Glass mustn't make it noticeably worse. Report both numbers.
- **Done when:** his pick is built in both modes; the contrast and frame-timing numbers are reported; he has seen it on his own phone.

### 39d. The dial
- Ask: one to three of A–I, shown in the Look lab with a real song playing.
- **The dial shows the vibration, not the sound.** That's Thrum's twist and it's honest: draw it from the haptic score at the current playback position, the same data the pulse ribbon (`PulseRibbon.kt`) uses. Never random or decorative movement. It freezes when the song pauses, and goes still under "Remove animations".
- Put it in its own file (e.g. `Dial.kt`) and change only the one place in `PlayerScreen.kt` where it's shown. Claude edits `PlayerScreen.kt` for Task 35, so keep that change small.
- Smoothness: draw in a `Canvas` driven by the frame clock, without recomposing the whole screen every frame. Check it on a release build.
- **Done when:** his pick plays in time with what the motor does (his hand on the phone, with the ribbon or the sketch for comparison); it's still when paused and under "Remove animations"; it looks right in both modes.

### 39e. The bars
Parked item 1 in `PLAN.md`: his Pinterest board's favourite is bars growing up and down from a middle line (pins 2, 3, 8 and 12), then a ring of bars (1, 4 and 7) and a block meter with falling caps (11). Almost all are white on black.
- First a rough HTML sketch, **A–F**, animated with the real rhythm from `scores.js`, showing the bars as the vibration, not the sound. One screenshot grid, plus a short screen recording if the movement matters.
- Ask him where the bars go: instead of the dial, alongside it, or somewhere else (the mini player, the song card on Home). Then build the pick in the Look lab and in the app.
- **Done when:** his pick is built, driven by real score data, still when paused, and seen on his phone.

**When Task 39 is finished:** the Play Store screenshots in Task 13 get retaken from the new look. Note that in the Task 39 line of `PLAN.md`.

## Handing back

After each task, update its line in the 7 October task list in `PLAN.md` with what was built and the proof (test output, gate scores, screenshots, his verdict). Commit and push, then give Mutalib the plain walkthrough. If something in this file turned out to be wrong, say so in `PLAN.md` instead of quietly working around it.
