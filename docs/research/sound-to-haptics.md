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
