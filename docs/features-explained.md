# Thrum's features, explained

How each feature works and how we'll build it, written for Mutalib on 3 October 2026, the day the full app was agreed.

Every feature starts with the plain version: what it is and what it's like. The technical version comes after. Any technical word is explained the first time it appears. The screens are in [`design/screens/thrum-screens.png`](design/screens/thrum-screens.png); numbers like [14] are screen numbers. The rules behind all of this are in [`PROFILE.md`](../PROFILE.md), and the build order is in [`PLAN.md`](../PLAN.md).

## The one idea under everything

**Plain version.** Thrum listens to a song and writes down where the beats are and how hard each one should feel. That list is called a **vibration score**. Think of it as sheet music for the vibration motor: instead of notes, it says "push hard now, rest now, push gently now", once every 20 milliseconds. Every feature in the app is a different way of getting a score, or a different moment to play one.

**Technical version.** The **analyser** (the part of the app that turns sound into a score, in `Analyser.kt`) reads the song as numbers and splits it into the low sounds, where the kick and bass live, and everything above them, where the snare and hats live. In each part it doesn't follow how loud the song is. It follows how suddenly the sound *jumps*, because a hand feels a beat through its start, not its loudness. That is why a loud, steady bass line doesn't turn into one long buzz. The result is one strength, from 0 to 255, for each 20 ms step, stored as a `Score`. The phone plays it with Android's `VibrationEffect.createWaveform`, a command that takes a list of strengths and durations and drives the motor with them.

**What it can't do yet.** On 28 September the score for AIZO kept the motor running 89.6 % of the time at nearly the same strength, and a hand feels that as a hum, not a beat. Every feature below plays this same kind of score. That is why the first task (Task 15) is fixing the feel, before anything new gets built.

## 1. Calls (always first)

**Plain version.** You choose one song for your calls. When the phone rings, you feel the first 45 seconds of it, repeated until you answer. It works on vibrate, and with the ringer on if you leave that switch on. Home shows which song it is, lets you feel a test call, and tells you when the last call happened and how quickly Thrum started.

**How it works today** (this part is built and works on your Pixel):
- Thrum notices a call through **notification access**: Android's permission that lets an app see notifications. When a call comes in, the phone app posts a notification, Thrum sees it, checks it really is a call, and starts the vibration. That's why Thrum never needs permission to read your calls themselves.
- Android starts its own flat buzz first. About half a second later Thrum's vibration arrives and **takes the motor over**, because Android lets the most recent ringtone vibration win. That half second can't be removed.
- **Silent mode is a wall.** Android throws the vibration away before it reaches the motor, and no app can change that. Home says so in plain words.

**What changes in the build** (Task 19):
- "Song for calls" can be any haptic: a song, a Thrum Original, a video's sound or a catalog song. Choosing a new one replaces the old one.
- **No more demo rhythm.** Today, if nothing is chosen, a call still gets a made-up rhythm. In the new app, nothing chosen means Android's normal buzz.
- "Feel a test call" plays the exact same thing a call would, through the same route. So on Silent, the test also stays silent, and the test never lies.

## 2. First launch and the phone check

**Plain version.** The first time you open Thrum, it checks your phone's motor before anything else. If the motor can only buzz at one strength, Thrum says so and stops; there's no point pretending. If it can, seven short screens explain what Thrum does, let you feel it straight away, ask for call access (you can say "maybe later"), and finish with "choose a song for calls" [1–7].

**Technical version.** The phone check reads `Vibrator.hasAmplitudeControl()`: Android's own answer to "can this motor vary its strength?" Your Pixel says yes. A one-speed motor says no, and Thrum shows screen 25 with no button on it. The seven screens get built in Task 18, with the tabs underneath them: Home, My Haptics, Music, Settings.

**What it can't do.** The "phone can't do it" screen has never been seen on a real cheap phone. Task 6 needs someone to lend one.

## 3. Thrum Originals

**Plain version.** A few short pieces of music made *for* Thrum and built into the app: working titles Afro Groove, Heartbeat, Pulse and Energy. They do three jobs:
- You can **feel Thrum the moment you open it**, before giving any permission [3].
- The **Music tab is never empty**, even before you scan your phone [8, 11].
- You can **pick a song for calls on day one** [7, 27].

**Technical version** (Task 27):
- The audio files go inside the app itself, in its `assets` folder (files packed into the app when it's built). Nothing is downloaded, so Thrum still needs no internet.
- Each Original ships with a **ready-made score**, made by the same analyser on the laptop when the app is built. So it plays instantly, instead of making you wait about 8 seconds the first time.
- Size: a 30-second piece at good quality is about half a megabyte, so six of them add roughly 3 MB. The app is 2 MB today, so it stays small for people on mobile data.

**What has to happen first.** The pieces don't exist yet. Someone has to make them, and Thrum has to **own them outright**:
- **You**, if you make beats.
- **A producer** under a short written agreement. A Ghanaian Afrobeats producer would fit the app well.
- **Public-domain (CC0) sounds**, which anyone may use for anything.

Be careful with "royalty-free" packs: many of their licences don't allow handing the music itself to users. Sacred Rule 4 now reads "never ship audio Thrum doesn't own outright".

## 4. Music: scanning your phone

**Plain version.** The Music tab starts with the Originals and a "Scan for music" button [8]. When you tap it, Android asks whether Thrum may see your music. If you say yes, Thrum lists every song and audio file on the phone, with search [9, 11]. If you say no, you can still pick one song at a time [12].

**Technical version** (Task 20):
- The permission is `READ_MEDIA_AUDIO` on Android 13 and up, and `READ_EXTERNAL_STORAGE` on Android 12. It covers music and audio only: never photos, never videos.
- The list comes from **MediaStore**, Android's own index of the media on the phone. Thrum doesn't search through folders itself. It asks Android for the list, which is fast and already includes each song's title, artist and length.
- A file Android can't decode, like your "Over the Horizon" (Dolby Atmos), stays in the list, marked "can't read this file", instead of failing quietly.
- Before writing the store listing, we check Google Play's current rules for music access.

## 5. Making haptics: now or as you play

**Plain version.** Making a song's score takes your phone about 8 seconds. After a scan, Thrum asks [10]:
- **All of them, in the background.** Thrum works through your whole library while you use your phone. This is what you chose. It uses some battery, and the app says so.
- **One at a time.** Each song's score is made the first time you play it.

Either way, a song you play before its turn **jumps the queue**. A strip shows progress, like "2 of 5 done" [11], and you can change the choice in Settings.

**Technical version** (Task 22):
- "In the background" means work that keeps going when you leave the app. Android's standard tool for that is **WorkManager**, a library that runs long jobs, survives the app being closed, and lets Android pause them when the battery is low. A very long job may need to show a small notification while it runs, because Android wants the user to know.
- The queue is a list of songs waiting for a score. Playing a song moves it to the front. This logic is pure arithmetic, so it gets tests that run on the laptop: a played song really jumps ahead, and no song is ever made twice.
- Each score is made for the **whole song**, not just 45 seconds. A call then uses the first 45 seconds of it.

**Real numbers.** Your Pixel took 7.4 s to read a 3:58 MP3 and 10.1 s for a 4:32 M4A (Task 3). A library of a few hundred songs is therefore tens of minutes of work. That's why it runs in the background and why the battery cost has to be honest.

## 6. The player: hearing and feeling a whole song

**Plain version.** Tap a song and it plays inside Thrum with its vibration in time with the music, from the first second to the last [14]. You can switch between "hear and feel" and "feel only", skip to the next or previous song, keep a small player along the bottom while you move around the app, and use the song for calls or export it from the same page.

**Technical version** (Tasks 16 and 21):
- **The limit.** Android silently ignores a vibration longer than about 10,500 steps on your phone, so Thrum caps every piece at 8,000 steps (2 minutes 40 seconds). Your AIZO track is 8,862 steps, so even one ordinary song has to play **in pieces**.
- **Keeping in step.** The preview already starts the vibration only when sound really leaves the speaker, by reading the music player's position. For pieces, Thrum reads that position again at each join and starts the next piece from exactly there, so small timing drift never adds up.
- **The music itself** plays through **MediaPlayer**, Android's built-in player that the preview already uses. **Media3**, Google's newer player library, is the alternative if lock-screen controls or the catalog need it. That choice is yours (Task 17).

**What nobody knows yet.** Whether the vibration keeps going **with the screen off**. Android may stop it. Task 16 tests this on your phone before the player gets built, because if it fails, the player has to be designed differently.

## 7. Videos and audio files

**Plain version.** "Make a haptic from" lets you pick a video, an audio file (a voice note, a ringtone, a recording) or a Thrum file you exported earlier [16]. Thrum makes a haptic from its sound, and it goes into My Haptics.

**Technical version** (Task 23):
- Picking uses the phone's own **file chooser** (Android's Storage Access Framework): the same screen you'd use to attach a file anywhere. Thrum only ever sees the one file you picked, so it never needs permission to see all your videos.
- A video file holds pictures and sound side by side. Thrum's reader, `AudioDecoder`, already looks for the sound inside a file and ignores the rest, so videos should need little new code. That's untested, though, and Task 23 proves it on a real video.
- Files over 30 minutes are refused straight away, with their length in the message [26]. That's the real message one of your 1 h 49 m recordings got.

## 8. My Haptics

**Plain version.** Everything you've made, in one list: songs, Originals, videos and files, filtered by All, Music, Videos or Files [19]. From there you can feel any of them, use one for calls, tune it, export it or delete it.

**Technical version** (Tasks 17 and 23). Today Thrum keeps its one score in a small settings file. Hundreds of scores need a proper home. The two options:
- **Room**, Android's own **database** library. A database is an organised store that can answer questions like "which songs have no haptic yet?" quickly.
- **One file per haptic** plus a small index file: simpler, but slower and fiddlier once there are a few hundred.

This choice is yours (Task 17). My pick is Room, because the questions the app keeps asking are exactly what a database is for.

## 9. Tune the feel

**Plain version.** Three presets, Crisp, Full and Strong, cover most people [15]. Under "Fine-tune" are three dials:
- **Intensity:** how hard each beat lands.
- **Focus:** whether you feel the whole drum kit, or only the main beat.
- **Duration:** how long each beat lasts. Longer pushes harder; shorter feels crisper.

**Technical version** (Task 24). These are today's three dials, renamed in plain words: Punch is now Intensity, Distance is now Focus, and Body is now Duration. The presets are fixed settings of all three. They get chosen in Task 15 by measuring them on your phone, not by guessing.

## 10. Export and import

**Plain version.** Save one haptic, or all of them, as Thrum files on your phone [17], to back them up or open them in Thrum on another phone. Songs that don't have a haptic yet get one first. **Only the vibration goes in the file, never the song.** A second option, "a ringtone with the vibration inside", is shown as not built yet.

**Technical version** (Task 25):
- A Thrum file is a small text file holding the score in the format the app already uses to remember it, plus the tuning it was made with. Saving uses the phone's own save screen. Opening one goes through "Make a haptic from → A Thrum file".
- A test proves that exporting and importing give back exactly the same score.
- **Why the ringtone option waits.** Android can play a sound file with an extra vibration track built in, and stock Pixel ringtones use that in ring mode. Writing such a file correctly is the hardest part of the whole project, which is why it was always planned for v2.

## 11. The online music catalog

**Plain version.** The Music tab gets a "Catalog" side [28]: search songs, artists or sounds, browse by genre (Afrobeats, Amapiano, Highlife, Gospel, Hip-hop, Electronic), and play any song from it with its vibration [30]. A catalog song can be saved as a haptic or used for calls. **Only the vibration is kept on your phone**; the song itself plays from the catalog over the internet. Offline, the catalog says so, and everything else keeps working [31].

**Technical version** (Task 28):
- Playing from the catalog means **streaming**: the music arrives over the internet while it plays, like YouTube, instead of being saved as a file. The analyser can work on the stream as it arrives, so the score is ready by the end of the song, and only the score is stored.
- **Two things block it, and both are your decisions:**
  - **The internet.** Thrum has none today. Sacred Rule 3 says "no network, ever", and the app's own permission screen promises there never will be. The catalog can't exist without changing that. If you change it, the permission screen, privacy policy, store listing and README all change in the same step.
  - **A legal source of music.** Searching for any song and downloading it would break copyright and Google Play's rules, so Thrum won't do that. The real options:
    - **Open-licence catalogs** that let apps play their music. These are mostly independent artists. Jamendo and Audius are examples to research first; I haven't checked their current terms.
    - **A paid licensing service.** This covers famous artists, but costs money and needs a contract.

**What it can't do.** Without a paid licence, the catalog won't have Asake or Burna Boy. Research first, from real sources, and then you choose.

## 12. Clean up the sound

**Plain version.** When a song is low quality, the kind of squashed copy that gets passed around between phones, the player says "Sound quality: low" and offers to clean it up [32]. You can listen to both versions and keep the one you like [33]. It happens on the phone and nothing is uploaded. The app calls it "Clean up" rather than "Enhance", because that says what it actually does.

**What we measured, and why it matters.** On 3 October I made 64 kbps and 32 kbps copies of AIZO (**kbps**, kilobits per second, is how much detail a file keeps; your original is 256). They gave almost the same vibration: 92 % and 88 % of the beats landed within 20 milliseconds of the original's. The reason is that squashing a song mostly throws away the high sounds, while your motor works around 150 Hz and the beat finder listens mostly to the low sounds. So:
- **The haptic is made from the original file.** Cleaning up changes what you *hear*, not what you *feel*.
- **It's a listening feature,** and that's fine: it makes the in-app player nicer.

**Technical version** (Task 29):
- **Spotting a low-quality file** is easy: every file records its **bitrate** (that kbps number), and Android can read it without playing anything. Below about 128 kbps, Thrum offers to clean up.
- **How it's done is your choice:**
  - **Plain sound processing.** Android has built-in sound effects that apply while music plays (`Equalizer`, `LoudnessEnhancer` and `DynamicsProcessing`): they balance the volume, lift clarity and soften harsh sound. This is small, instant and easy to switch off. It can't rebuild detail the file threw away; it only makes what's left sound better.
  - **An AI model on the phone** that tries to guess the missing detail. It can sound better, but it makes the app much bigger and slower, and it sometimes invents odd sounds. Which models could run on a phone needs research.
  - A model on a server is ruled out, because it would upload your music.

## 13. Settings, About and Phone check

**Plain version.** Settings holds the choices from every feature above in one place [20]: the song for calls, call access, the ring-mode switch, music access, scanning, how haptics get made, the online catalog, the default feel, and cleaning up low-quality songs. About tells why Thrum exists, in your words, and credits whoever makes the Originals [21]. Phone check shows what your phone can do, including how fast its motor can tap: separate taps up to 8 a second on your Pixel [22].

**Technical version** (Task 26). Each setting is read wherever it matters, and the proof for this task is that every setting really changes what it says it changes, read back from storage, or for vibration, from Android's own record (`dumpsys vibrator_manager`).

## The build order, and why

| Task | What | Why it comes here |
|---|---|---|
| 15 | Fix the feel | Every feature plays a score, and today's scores feel like a hum. |
| 16 | Test a whole song on the phone, including screen off | The Music tab is built on this. If it fails, the design changes. |
| 17 | You choose storage, background work and player | Everything after this stores and plays haptics. |
| 18 | The app's shell and first launch | The frame everything else sits in. |
| 19 | Home and calls | The first feature, already working; it moves into the new app. |
| 20 | Music scan | The library the player needs. |
| 21 | The player | Built on Task 16's answer. |
| 22 | Making haptics, now or in the background | Needs the library and the player. |
| 23 | Videos, files and My Haptics | More sources, same engine. |
| 24 | Tune the feel | Uses the presets Task 15 measured. |
| 25 | Export and import | Needs the library. |
| 26 | Settings, About, Phone check | Collects the choices of everything above. |
| 27 | Thrum Originals | Waits for the pieces to be made. |
| 28 | The online catalog | Blocked until you decide on the internet and a licensed source. |
| 29 | Clean up the sound | Needs the player and your choice of method. |

After that comes shipping: the reliability test over 24 hours and a reboot (Task 10), the cheap-phone test (Task 6), and a new store listing (Task 13).

## Decisions only you can make

1. **The internet**, for the catalog. Changing Sacred Rule 3 is yours alone.
2. **Where the catalog's music comes from**, after research.
3. **Who makes the Thrum Originals.**
4. **Five building choices**, each with my pick and the alternative in `PROFILE.md` §7:
   - where haptics are stored
   - how background work runs
   - which player plays songs
   - how clean-up works
   - the catalog source
5. **Still owed:** a copy of the release key (`thrum-release.jks`) and its password, kept somewhere other than this laptop. Without them the app can never be updated on the Play Store.

## What Thrum can never do

- **Make Spotify, YouTube Music, TikTok or WhatsApp calls vibrate.** Android doesn't let one app reach another app's sound. Thrum only plays music inside Thrum.
- **Vibrate in Silent mode.** Android throws the vibration away.
- **Change your phone's call screen.** That belongs to your phone; Thrum only moves the motor.
- **Remove the first half second of Android's own buzz** at the start of a call.
