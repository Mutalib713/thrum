# Sound To Haptics: what it does, measured on Mutalib's Pixel (2026-10-06)

Mutalib asked for a deep look at [RNDMBG/Sound-To-Haptics](https://github.com/RNDMBG/Sound-To-Haptics) (release [Stable-V2.1](https://github.com/RNDMBG/Sound-To-Haptics/releases/tag/Stable-V2.1)), its setup screens and its wording, and what Thrum can take from it. He had installed v2.0 on his Pixel 6 Pro from Chrome that afternoon. Everything below was read off the repo through the GitHub API, or off his phone over USB (`uiautomator dump` for exact wording, `screencap` and `screenrecord` for screens and motion, `dumpsys` for how it drives the motor). Screenshots stay in the gitignored `scratch/`, because this repo is public.

## The repo

- MIT licence, created 2026-07-29, 0 stars, no forks, no issues.
- **No source code.** The tree is a README, the licence and a logo. The README says the code will be open-sourced "soon". The app exists only as release APKs.
- Four releases, about 41 downloads between them. v2.1 (2026-09-26) is 54 MB and swaps its demo song for the one Apple ships in iOS 26 Music Haptics (their words).
- The README's method: FFmpeg converts an MP3 into an OGG file carrying the `ANDROID_HAPTIC` tag, and the HapticLabs Kotlin library plays it. It lists about 150 phones expected to support haptic playback. "Tested" marks are on 6 of them; Pixel 6 Pro is listed but untested; "any Samsung FE edition will not work".
- On the phone it asks for `RECORD_AUDIO`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `INTERNET` and `ACCESS_LOCAL_NETWORK`, with targetSdk 34 and minSdk 26 (the README says 28). It has **no** notification access, so Play Protect's install block doesn't apply to it: it installed from Chrome with no warning, where Thrum is refused.

## How it actually plays (measured)

While its demo played, Android's own records showed:

- A `MediaPlayer` with channel mask `0x20000003`: stereo plus **haptic channel A**. `dumpsys media.audio_flinger` reports "Haptic channel mask: 0x20000000 (haptic-A)".
- The vibrator in state `UNDER_EXTERNAL_CONTROL`, and `dumpsys vibrator_manager` logging `external` vibrations with usage MEDIA.

So the sound and the vibration are one file played by the audio system, which drives the motor itself. This is **audio-coupled haptics**: it works inside an app on this Pixel, not only for ringtones. Thrum's player instead sends `VibrationEffect` pieces and re-syncs each piece to the audio position (R10, Task 16).

## Setup and wording, verbatim

**Intro.** The header reads "A short introduction", with a six-segment progress bar and Skip, and Back/Next at the bottom.

1. Welcome to Sound To Haptics. "…a revolutionary app designed to convert any audio into haptic feedback."
2. Offline Music. "Import a local song, let the analyzer build its haptic layers, and keep the converted OGG in your private library between launches."
3. Spotify. "Connect Spotify and allow Android playback capture. The app listens to Spotify's device output."
4. Feel Different Parts of Music. Bass → THUD, Beats → IMPACT, Vocals / movement → SWISH, each with a small level bar. "Bass, beats, and vocals are carefully analyzed by the app as much as it can."
5. Haptic Strength. Five rising bars. "Control the strength and intensity of the haptic feedback easily to your comfort."
6. Start Listening. "Choose Offline or Spotify below and begin." The button reads "Start listening".

**Main screen.** "Convert your audio" / "Pick a file below, or try the built-in demo track" / **Select Audio** "MP3 · WAV · OGG · FLAC" / "Try the demo track instead" / "Saved haptic songs" ("Your haptic library is empty"). Tabs: Offline · Spotify · Settings.

**Player.** A circle of five bars, a seek bar, back 10 s, play/pause and forward 10 s. It shows no song title and no feel controls.

**Spotify tab.** Only "Spotify mode — Still Work In Progress!"

**Settings** ("Appearance, motion, and storage"):

- Appearance: Theme (System · Light · Dark); Dynamic color, "Match colors to your wallpaper (Android 12+)"; Background, "Behind the UI" (Dynamic · Static · Off), "Dynamic movement in the background".
- Motion & Haptics: Reduce motion, "Shorter, and more static animations."; Haptic strength 100 %; Bass emphasis 70 %; Signal density 60 %; Haptic engine Continuous, "Smooth, always-on haptic texture", or Transient (Beta), "Sharp individual taps on each detected beat, still WIP"; Keep screen on while playing, "Prevents the display sleeping mid-track".
- Storage: Confirm before clearing cache / library; Clear cached haptic files, "Frees space; Spotify songs can be re-cached again"; Clear offline library, "Delete converted songs saved inside the app".
- Help: Replay the introduction, "Review bass thuds, beat impacts, flow, and capture basics".
- Reset: Reset settings to default, "Theme, background, and motion preferences only".

**About** is a single pop-up: "Made By RandomBlenderGuy".

## Motion

- Intro pictures don't move while a page is showing. Each page draws its own line (a soft wave, sharp spikes, a smooth green wave, three level meters, rising bars, a single raised pulse), and the page changes are a 0.2–0.3 s crossfade.
- The main screen moves all the time: drifting background lines and a small three-bar icon.
- The player's bars change about ten times a second with the music, and the background lines move with them.

## Related facts checked while researching

- **Playing another app's audio is possible on Android 10+**, against the factual sentence in Sacred Rule 5. Android's [playback-capture documentation](https://developer.android.com/media/platform/av-capture) (updated 2026-10-01) says an app needs `RECORD_AUDIO` plus the user's approval of the screen-capture prompt. Only media, game and unknown usages can be captured, never calls, and any app can opt out; apps targeting Android 10+ are capturable unless they do. [RootlessJamesDSP](https://github.com/ThePBone/RootlessJamesDSP), an open-source app built on this, lists Spotify, Chrome and SoundCloud as blocking capture; YouTube, YouTube Music, Amazon Music, Deezer, Poweramp and Apple Music as working; and "increased audio latency" as a cost. Its date isn't shown. **The rule itself is Mutalib's to keep or change; nothing has changed.**
- **Android 12+ has a built-in sound-to-vibration effect**, `HapticGenerator` (AOSP source): "an audio post-processor which generates haptic data based on the audio channels". It attaches to an app's own player by audio session, and works only where audio-coupled haptic playback is supported (`isAvailable()`).

## Open, for Mutalib

1. Rule 5: keep it as written, or reword it to match the facts and consider a "feel what's playing" feature for apps that allow capture.
2. Audio-coupled haptics for Thrum's own player and for the v2 ringtone-with-vibration (option B). This needs an encoder on the phone: they ship FFmpeg, which is why their APK is 54 MB. It's a stack choice.
3. Which wording ideas to adopt (listed in the 2026-10-06 conversation).

## Re-check, 2026-11-28: v2.0 again, and what V3 adds

Walked every screen on the Pixel again: the six intro pages, Home, the demo player, the Spotify tab and Settings. **The wording is unchanged from v2.0 above.** The phone still runs v2.0; installing V3 is Mutalib's to do (the release is an APK with no published source, and V3 is signed with a new key, so v2.0 has to be uninstalled first).

From the release notes, not seen running: **Stable-V3** (2026-11-14) adds "BitChord", a YouTube Music client inside the app (sign in, search, library, playlists) wired to their haptic engine; it calms the Transient mode ("fewer textures, wider accent spacing") and replaces a silent wait on playback failure with "actionable copy and a Retry button". **Stable-V3.1** (2026-11-16) is bug fixes. Downloads at the time: 17 (V3.1), 3 (V3).

Their intro layout: a segmented progress bar across the top, Skip top right, Back bottom left, Next bottom right, one picture, a one-to-three-word title and one or two sentences per page.

### What Thrum can take from how they describe things

1. **Every setting carries one plain line saying what it does to you**: "Prevents the display sleeping mid-track", "Frees space; Spotify songs can be re-cached again". Several Thrum rows show a value with no line under it.
2. **Name what you feel, not the audio term**: Bass → THUD, Beats → IMPACT, Vocals → SWISH. Thrum's tune screen already does this (Crisp, Full, Strong, Extra taps); the old Focus dial is gone.
3. **Say what an action leaves alone**: "Theme, background, and motion preferences only" under Reset. Thrum's delete prompt already does this ("Your audio and video files stay on your phone").
4. **A subtitle under a screen title summing it up**: "Settings · Appearance, motion, and storage".
5. **A no-commitment first try**: "Try the demo track instead" sits right under the main action on an empty Home.

### What not to copy

- Marketing words in the intro ("a revolutionary app").
- File and engine terms in user copy: "OGG", "playback capture", "haptic layers", "Signal density", "Transient (Beta) … still WIP".
- A whole tab that ships saying "Still Work In Progress!".

## V3 on the phone, 2026-11-28

Mutalib installed V3 himself; Android reports `versionName=3.0`. Walked: the intro (replayed from Settings), Offline home, the demo player, the BitChord tab (not signed in), Settings, About.

**What changed:**

- **Spotify capture is gone.** The Spotify tab is now **BitChord**, "YouTube Music client": sign in with Google, then your library, likes and playlists play inside the app with haptics. The permissions match: `RECORD_AUDIO` and the screen-capture service are no longer requested; `INTERNET`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `POST_NOTIFICATIONS` are.
- **How it plays is unchanged:** `dumpsys vibrator_manager` still logs `external` vibrations, usage MEDIA, while the demo plays: the audio system drives the motor (audio-coupled haptics).
- Intro page 3 is now BitChord: "Connect BitChord to your YouTube Music account. The app plays through ExoPlayer and learns each track from the decoded audio itself."
- Haptic Strength's intro line now ends "…may not work with some devices."
- The empty library has a proper empty state: "Your haptic library is empty" / "Convert a song and it will stay here between launches." / **Convert a song**.
- BitChord's sign-in screen: "Feel every song", three benefit lines with icons ("Your YouTube Music library, likes, and playlists", "Haptics synced to every track, automatically", "Profiles cached on your phone after the first play"), **Continue with Google**, and under it "Uses your own YouTube Music account, nothing is being logged."
- Dynamic color's help line now reads "Makes the interface look prettier (atleast in my opinion). Requires Android 12 or up."
- Continuous engine: "Smooth, always present texture" (was "always-on").

**More for Thrum to take:** an empty state that says what will live there and offers the one action that fills it; a reassurance line right under the button that asks for trust (Thrum's call-access screen already does this); and owning a limit in the copy ("may not work with some devices"), which is Sacred Rule 2 in their words.

**Not for Thrum:** BitChord streams from a service through an unofficial client: network, someone else's audio, a sign-in to the user's Google account. That breaks Rules 3 and 4. Engine names in copy ("ExoPlayer", "decoded audio") and the joke help line, also not.

## Where Thrum's wording could change (audit of strings.xml, 2026-11-28)

Every line of `app/src/main/res/values/strings.xml` was read against the lessons above. These are **suggestions for Mutalib to pick from; nothing has changed in the app.** The string name is in brackets so each one can be found. Most of Thrum's copy already follows these lessons; the list is only the places that don't.

### 1. The word "haptic" is never explained

The app's main noun is used everywhere (My Haptics, "Make a haptic", "Making its haptic…", "Your first haptic goes here.") but no screen says what one is. The onboarding talks about "beats" and "the vibration motor" and never uses the word.

- **Suggestion:** one sentence on the onboarding page "Turn sound into touch" [`onboarding_touch_body`]: "…plays them on your phone's vibration motor. We call that pattern a **haptic**."
- Then "haptic" can stay everywhere else, as the tab name and in buttons.

### 2. Settings rows with a value but no line saying what they do

| Row | Today | Suggested line under it |
|---|---|---|
| Call access [`settings_call_access`] | "Change it in Android's settings" (says where, not what) | "Lets Thrum know when a call starts." |
| Music access [`settings_music_access`] | none | "Lets Thrum find the songs on your phone." |
| Make haptics [`settings_make_haptics`] | value only | "When Thrum makes a song's haptic: all at once, or when you play it." |
| Default feel [`settings_default_feel`] | value only (Crisp / Full / Strong) | "How beats feel on your ringtone and new haptics." |
| Bars [`bars_title`] | value only | "How the rhythm moves on the player." (the picker already says this, the row doesn't) |
| Phone check [`phonecheck_title`] | none | "What your phone's motor can do, and what Thrum did on your last calls." |
| Go back to my old ringtone [`settings_old_ringtone`] | none | "Your haptics stay in Thrum." (says what it leaves alone) |

### 3. A one-line summary under screen titles

- **Settings** [`tab_settings` heading]: nothing under it. Suggest: "Calls, music and how it feels".
- **My Haptics**: the explanation only appears when the list is empty [`haptics_empty_body`]. A short subtitle that stays: "Made from your audio and videos".

### 4. Say what an action leaves alone

- **Reset to default** on the tune screen [`tune_reset`]: add "Only the feel. Your ringtone and haptics stay."
- **Set as ringtone** prompt [`ringtone_ask_body`]: add "You can go back to your old ringtone in Settings." (true: Settings has that row).
- **Delete** [`haptics_delete_body`]: already does this. Keep.

### 5. A reassurance line right under the button that asks for trust

- **Allow modifying system settings** [`ringtone_ask_how`]: this is the scariest-sounding Android page Thrum sends people to, and nothing says why it's safe. Suggest under the button: "Thrum only uses this to set your ringtone." (verified: `WRITE_SETTINGS` is used only in `Ringtone.kt`).
- **Turn on music access** after a refusal [`music_denied_settings`]: add "Thrum reads music and audio files only. Nothing leaves your phone." (the line already exists as `music_intro_privacy`; reuse it here).
- **Turn on call access** in onboarding [`onboarding_calls_permission`]: the reassurance sits above the button. In the redesign, move it directly under.

### 6. Words that are jargon or vague

| Where | Today | Suggestion |
|---|---|---|
| Song rows and player [`rhythm hits` plurals, `player hits`] | "0:20 · 34 hits", "The rhythm: 34 hits over 0:20" | "34 beats": the onboarding already calls them beats. **His call:** "hits" may be deliberate, since not every hit is a musical beat. |
| Phone check [`phonecheck_amplitude`, `phonecheck_amplitude_help`] | "Strength control" / "The part rhythm needs" | "Changes how hard it buzzes" / "Thrum needs this to play a rhythm, not just one buzz." |
| Home status [`home_ringer_unknown`] | "ringer unreadable" | "can't tell the ringer mode" |
| Home last call [`home_last_call`] | "Thrum started your haptic 0.4 s after it rang" | "Thrum started vibrating 0.4 s after it rang" |

### 7. Don't promise more than the phone can do

- **From a video** [`make_video_help`]: "Feel the sound from **any** video" overclaims; some files can't be read (the Dolby case already has its own error). Suggest: "Feel the sound from a video on your phone".

### Already good, don't touch

The ringer-mode explanations ("Android throws the vibration away… No app can get around that"), the presets' help lines (Crisp "Short taps with space between them"), the export privacy lines, the Make-haptics battery warning ("It uses some battery while it does"), and the delete prompt.
