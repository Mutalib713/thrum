# Review of the `ui-final-screens` branch, 4 October 2026

Mutalib asked for the whole codebase to be checked for bugs, problems and code that needs refactoring, with one known complaint: **the app crashes when you use it.** This is what the check found. The fixes are listed at the end and in git history.

## What was checked

- **The branch:** `ui-final-screens` at `d413a98`, Antigravity's UI commit, which sits on eleven implementation commits on `main` (`88f6f2e` to `56effdc`, 3–4 October, Tasks 15–26).
- **Every file** in `app/src/main` (about 11,300 lines of Kotlin), the manifest, the build files, the strings and both test files.
- **Run, not just read:** `check.ps1` (99 tests pass, debug build fine), then the app installed fresh on the Android 14 emulator and walked by hand: first launch, Music, scan, background haptics, the player.
- Line numbers below are from `d413a98`.

## The crash: found and reproduced

**In plain words.** When Thrum makes haptics "in the background", it keeps a waiting list of songs. The list has a self-check: a song that already has its haptic must never still be waiting. If that check ever fails, the app doesn't fix the list. It stops dead. Two parts of the app edit the list at the same time, the background worker and the player, and the worker can save an old copy over the player's newer one. That puts a finished song back on the list, and the next time you tap a song the self-check fails and the app closes. The bad list is saved, so **it happens again on every tap, even after a restart**, until the app's data is cleared.

**Reproduced on the emulator**, with four test songs (one ten minutes long, three short):

1. Music → Scan for music → Allow → "All of them, in the background" → Continue. The worker starts on the long song.
2. Tap "Short Beat". The player makes its haptic itself and correctly takes it off the list.
3. The worker finishes the long song (8½ minutes on the emulator) and saves the copy of the list it read when it started, which still has "Short Beat" on it.
4. Tap any other song. Android's crash log:

```
FATAL EXCEPTION: main
java.lang.IllegalArgumentException: a made song is still pending: [content://media/external/audio/media/1000000213]
    at com.mosman.thrum.HapticQueue.<init>(HapticQueue.kt:30)
    at com.mosman.thrum.Player$jumpAhead$1.invokeSuspend(Player.kt:111)
```

**Technically.** `HapticQueue`'s `init` throws when `pending` contains a made URI (`HapticQueue.kt:28–31`). `HapticsWorker.doWork` builds its queue *before* the eight-second decode and writes `queue.completed(head).pending` *after* it (`HapticsWorker.kt:50–63`), discarding every edit made in between. `Player.jumpAhead` then builds a `HapticQueue` from the corrupted store on the main thread, inside a scope with no exception handler (`Player.kt:71, 106–116`), so the throw kills the process. The same constructor runs in `MusicTab.topUpQueue`, `ExportScreen` and `SettingsTab`, so a rescan or an export can crash the same way. This bug came in with the implementation (Task 22), not the UI.

## Findings

Each one says what is wrong in plain words, where, and how it was found: **seen** on the emulator, or **read** in the code.

### A. Crashes and damaged data

| # | What is wrong | Where | Found |
|---|---|---|---|
| A1 | The crash above. | `HapticQueue.kt:28`, `HapticsWorker.kt:50–63`, `Player.kt:106–116` | seen |
| A2 | **The background walk stops after one song.** The worker asks Android to run it again with a setting that means "skip this if the job is already running", and it asks while it is still running, so the request is always skipped. "Making haptics · 1 of 4 done" never moves. | `HapticsWorker.kt:66, 85–91` | seen |
| A3 | A rescan in "one at a time, as I play them" mode still fills the background list (and never empties it), which leads to the same crash later. Also on `main`. | `MusicTab.kt:131–136` | read |
| A4 | Tapping a "Thrum Original" sends a made-up address (`original://Afro Groove`) to the player. Opening it fails, and the failure handler **saves a broken "Afro Groove" song into the music library**, marked unreadable. | `MusicTab.kt:353–366, 464–477` → `HapticMaker.kt:49, 88–94` | read |
| A5 | **Export crashes** if saving fails (storage full, the save location goes away): nothing catches the error. It also says "Saved to your phone" when nothing was written, and "This song" with no song open saves an empty file. | `ExportScreen.kt:79–98, 279–290` | read |
| A6 | Tap two songs quickly and **both can play at once**. The first one's player is never released, and the screen can show one song's haptic beside the other's sound. | `Player.kt:215–268` | read |
| A7 | If music access is taken away mid-scan, the scan's error is not caught and the app crashes. Rare. | `MusicTab.kt:121–139` | read |

### B. Things the app shows that aren't true

PROFILE's Sacred Rule 2 is that the app never lets anyone believe it works when it can't. The UI commit filled the screens with the design board's sample content (Mutalib's own songs, call times, numbers) as if it were the user's real data.

| # | What is wrong | Where |
|---|---|---|
| B1 | **Home says "Ready" when Thrum can't see calls.** Call access is checked and the answer thrown away, so someone who tapped "Maybe later" is told calls work. | `ThrumScreen.kt:82–88, 205–220` |
| B2 | **Home ignores the ring-mode switch.** It shows "Ready · on ring" even with "Also when the ringer is on" switched off, when nothing will vibrate. The honest five-way verdict (`Setup.verdict`) is no longer used. | `ThrumScreen.kt:205–220` |
| B3 | On a fresh install with no calls at all, Home says **"Last call, Sun 20 Sep at 20:27: Thrum started your haptic 0.3 s after it rang."** That is Mutalib's real measurement from 20 September, hard-coded. | `ThrumScreen.kt:358–365` |
| B4 | "Recently played" always shows **"Active · Asake, Travis Scott · 3:04"**, and its play button plays the demo pattern. | `ThrumScreen.kt:424–459` |
| B5 | "Feel a test call" opens a **fake incoming call** from "+233 24 123 4567" with Answer and Decline buttons, under the design board's own annotation: "Sketch note: this is your phone's own call screen…". | `ThrumScreen.kt:477–587` |
| B6 | **"Use Afro Groove for calls" puts the demo pattern back as the call song.** PROFILE §4 item 3: "no demo pattern". | `ThrumScreen.kt:305–353` |
| B7 | **Thrum Originals that don't exist.** A shelf of four (Afro Groove, Heartbeat, Pulse, Energy) on the Music tab; first launch says "Afro Groove, with sound… a Thrum Original, built into the app" and plays the demo pattern with no sound. No piece of the collection has been chosen yet (PROFILE §4 items 14–15). | `Components.kt:465–511`, `Onboarding.kt:300, 318` |
| B8 | **The online catalog you dropped is back:** an "Online" badge, "You're offline", "Sample result" rows with play buttons that do nothing, empty artist circles, and "Online catalog: On" in Settings. PROFILE §4 item 15 and §6: no internet, a built-in collection instead. | `MusicTab.kt:200–220, 667–835`, `SettingsTab.kt:214–218` |
| B9 | While scanning, a progress bar stuck at 55% and a "found so far" list of five of Mutalib's songs. | `MusicTab.kt:283–332` |
| B10 | **Every song says "Sound quality: low… Make it clearer?"**, including a clean 192 kbps file (seen). "Clean it up" opens a sheet with a bar stuck at 40%, and "Use cleaned up" only closes it. Clean up is Task 29 and isn't built. | `PlayerScreen.kt:172–191, 294–369` |
| B11 | With no haptic, the player shows made-up numbers ("164 hits · still 42%") and a made-up position. When a song can't be read, the reason is never shown. | `PlayerScreen.kt:198–218` |
| B12 | "Making its haptic" shows a progress bar stuck at 40%. | `PlayerScreen.kt:122–167` |
| B13 | An empty My Haptics shows three made-up rows: "AIZO, but it's lofi hiphop" marked as the Calls song, "Active", "thrum-test-ringtone". Their buttons do nothing. | `MyHapticsTab.kt:207–236` |
| B14 | Settings says the song for calls is "AIZO" when none is chosen, "Last scanned today" when it has never scanned, "Default feel: Crisp" when the dials are custom, and offers "Clean up low-quality songs: Ask first", which only opens Home. | `SettingsTab.kt:121, 185, 230–243` |
| B15 | About says **"Made for Thrum by ."**, with the name missing. | `SettingsTab.kt:414–419` |
| B16 | Phone check says "Separate taps up to 8 a second. **Measured on this phone.**" on every phone. 8 was measured on Mutalib's Pixel. | `SettingsTab.kt:588–600` |
| B17 | First launch: screen 5 names AIZO as if it were the user's call song; screen 6 has a play button that does nothing beside "0:53 / 2:57"; screen 7 has an empty box; the "can't do it" screen shows the board's annotation "No button here, on purpose" inside a button-shaped outline. | `Onboarding.kt:446, 512–528, 564–568, 633–651` |
| B18 | Every song row and the mini player draw the same made-up rhythm instead of the song's own. | `MusicTab.kt:543`, `MyHapticsTab.kt:263`, `PlayerScreen.kt:395` |

### C. Features that stopped working

| # | What is wrong | Where |
|---|---|---|
| C1 | **The call rhythm is built from the whole song**, squeezed to 40 ms steps to fit the vibrator's limit, ignoring Focus and Duration. A call should play the first 45 seconds at full 20 ms detail with all three dials (PROFILE §4 item 3); 40 ms is the "chunky" feel Mutalib rejected on 21 September. `main` did this right. | `ThrumScreen.kt:126–134` |
| C2 | Picking a song for calls shows nothing for the ~8 seconds it takes, and if the file can't be read, nothing at all: the reading and error states are set and never drawn. | `ThrumScreen.kt:76–77, 113, 137` |
| C3 | **Tune's "Done" doesn't change the vibration.** It saves the new numbers beside the old rhythm without rebuilding it. If no call song is chosen, the settings aren't saved at all. Opened from a song's page, it doesn't tune that song (PROFILE §4 item 6). | `Tuning.kt:197–205` |
| C4 | Lost from Home: "Play it with the song", the stop button, the phone's-own-buzz comparison (Task 15's tool, fixed on `main` the day before), and the Silent and ring-off explanations. | `ThrumScreen.kt` vs `main` |
| C5 | Errors are collected but never shown in My Haptics (a video or file that can't be read, an import that fails) or in the player. | `MyHapticsTab.kt:69, 97, 128`, `PlayerScreen.kt` |
| C6 | The play button on an imported haptic asks for sound that isn't in the file, so the player sticks. The row itself handles it correctly. | `MyHapticsTab.kt:272–275` |
| C7 | First launch's last button says "Go to your music" and lands on Home. | `Onboarding.kt:587–590` |
| C8 | In Settings, the ring-mode switch and the "Make haptics" row save your tap but don't redraw, so they look broken. Also on `main`. | `SettingsTab.kt:149–152, 190–212` |
| C9 | Export says "All 1 songs" with no songs, counts songs rather than haptics, and promises "Thrum makes them first, then exports", which it doesn't. | `ExportScreen.kt:155–171` |
| C10 | A test call keeps vibrating after you leave Home; first launch's two play buttons stay on "pause" after the pattern ends. | `ThrumScreen.kt:143–151`, `Onboarding.kt:282–313` |

### D. Getting around

| # | What is wrong | Where |
|---|---|---|
| D1 | **The phone's Back button closes the whole app** from Tune, Export, Create, About and Phone check, and from the two bottom sheets. Only the player handles Back. | `Shell.kt`, `Tuning.kt`, `ExportScreen.kt`, `MyHapticsTab.kt`, `SettingsTab.kt` |
| D2 | Back on the Welcome screen goes to the splash, which jumps forward again a second later. | `Onboarding.kt:57` |
| D3 | A bottom sheet closes when you tap empty space inside it: the "don't close" guard is switched off, so the tap falls through. | `Components.kt:529` |

### E. Look and accessibility

| # | What is wrong | Where |
|---|---|---|
| E1 | **Dark only.** PROFILE §4 item 13 says the app follows the phone's light or dark setting, and `main` did. On a phone in light mode the status bar's icons are drawn dark on Thrum's dark background. | `Components.kt:43–53` and raw colours in every screen |
| E2 | Four colours that aren't in the palette or on the board: `#F5F5F0`, `#8E8E86`, `#131312`, `#2A2A26` (Settings, About, Phone check, Export). Each is a near-copy of an approved token. | `SettingsTab.kt`, `ExportScreen.kt` |
| E3 | Headings are drawn in the reading font: every text sets its own size, so Space Grotesk, the display face in `Theme.kt`, is never used. | every screen |
| E4 | The custom switch, play buttons and tabs don't tell a screen reader what they are or whether they're on. | `Components.kt`, `Shell.kt` |
| E5 | The tab bar's border is drawn on all four sides, not just the top. | `Shell.kt:147` |

### F. Code that needs refactoring

| # | What is wrong | Where |
|---|---|---|
| F1 | **217 of the 224 reviewed strings are unused**; about 200 English strings are typed straight into the Kotlin instead, which is how the fake content got in unnoticed. | every screen |
| F2 | Raw sizes and colours everywhere; `Tokens.kt` (spacing, touch size) is unused by the new screens. | every screen |
| F3 | The same permission and ringer polling is copied into four files. | `ThrumScreen`, `MusicTab`, `SettingsTab` ×2 |
| F4 | Dead code: the ribbon's "glow" builds a paint and draws nothing; unused helpers and imports; `ThrumSegmentedControl` takes the same list under two names. | `PulseRibbon.kt:76–83`, `Components.kt:292–300`, `Icons.kt:39–141` |
| F5 | `Divider` is deprecated; `HorizontalDivider` replaces it. | several |
| F6 | The song list draws every row at once inside a scrolling column. A library of hundreds of songs builds hundreds of rows, each with its own drawing, before showing anything. `main` used a lazy list. | `MusicTab.kt:523–570` |
| F7 | Garbled characters in the debug test bench (`Reading”¦`, `Ã—`): the file was saved in the wrong text encoding at some point. Debug builds only. | `MainActivity.kt:365, 524, 580, 644` |
| F8 | Values read straight from storage while drawing don't update when they change (the progress line, the Calls badge, Settings rows). | `MusicTab.kt:480–481, 529`, `SettingsTab.kt` |

### G. Not bugs: for Mutalib to decide

- **Extra permissions.** No internet permission, so Sacred Rule 3 holds. But WorkManager adds four permissions to the installed app: `WAKE_LOCK`, `ACCESS_NETWORK_STATE` ("view network connections"), `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE`. The store listing will show them, and the privacy policy should say why. Belongs to Task 13 / Task 30.
- **Media3 was picked, MediaPlayer is used.** Recorded in PROFILE §7 as a later migration. Not a bug.
- **Export all** queues missing haptics instead of making them first. The implementation recorded this as a deliberate deviation from PROFILE §4 item 10, and its text says so honestly. Kept.
- **The hum is still the open problem.** A test song with a clean kick every 0.4 s came out as "1 hit, still 0%": one unbroken drive for 15 seconds. That is R9, Task 15's job, not this review's.
- **The emulator is shared.** Something else launched Wird on it over `adb` during this review. Thrum was only brought forward briefly, once, to reproduce the crash.

## What gets fixed

Everything in A to F. The approach: keep Antigravity's look (cards, buttons, icons, layout), put back `main`'s honest behaviour and its reviewed strings, and replace every sample value with the user's real data, or nothing. Where a feature isn't built yet (the collection, Clean up), its screens come out rather than pretend. Group G stays as it is.

Each group's fix is a separate commit, listed here when it lands.
