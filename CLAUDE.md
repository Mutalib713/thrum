# Thrum — session rules

**Read `PROFILE.md` before anything else. It is canonical.** Then `PLAN.md` for what to do next.

## What this app is, in one line

Turns sound into something you can feel: your phone vibrates to the rhythm of a song you chose when someone calls, and you can play your music inside Thrum and feel every beat. **Calls come first.**

Since 2026-10-03 Thrum is the full app (calls, music, videos, My Haptics, export). The screens to build are in `docs/design/screens/thrum-screens.png`; `PROFILE.md` §4 says what each one does.

## Standing rules

- Commit AND push after every meaningful change. Never wait for a green build.
- **NEVER add AI attribution to commits, PRs, or pushes.** No `Co-Authored-By`, no "Generated with", nothing.
- `PROFILE.md` is canonical. Sacred Rules need Mutalib's explicit approval to change.
- Verify with evidence — test output, measurements, a hand on the phone. Not claims.
- **Explain in plain language first, unprompted, then the technical version. Define every term on first use.** Mutalib is new to the field; leading with jargon means he silently doesn't follow and searches it up later. This applies to conversation AND to the app's own copy.
- If a request bundles several asks, restate them as a numbered list and confirm priority before starting.
- If "done" is undefined ("make it better"), propose concrete done-criteria and get a yes first.

## Sacred Rules (copied from PROFILE.md §6 — do not diverge)

1. **One song for calls at a time, and calls come first.** v1 is PROFILE.md §4 and the final screens. Not on them? It's v2.
2. **Never let a user believe the app works when their hardware cannot do it.** The capability check runs first and its verdict is honest.
3. **Everything runs on the phone. No server, no network, ever.** Nothing leaves the phone unless the user exports it, and an export never contains audio.
4. **Never ship audio Thrum doesn't own outright, and never host or redistribute anyone else's.** Convert what's already on the user's device, plus the Thrum Originals, which Thrum owns.
5. **Thrum does not do system-wide audio haptics.** It works only with sound it can open itself (files on the phone, the Thrum Originals). Android 10+ playback capture exists, but Spotify blocks it, calls can never be captured, and it would be a different product: a deliberate choice, not an Android limit. Thrum's own player plays only files on the phone, inside Thrum.
6. **Prove it on hardware before building around it.** No emulator exists here and emulators can't do vibration anyway.
7. **The feature flag is not a strategy.** The app must work with `enableRingtoneHapticsCustomization` OFF.
8. Plain-language first, always.

## Build commands

Repo lives at `C:\Users\USER\MyClaudeProjects\thrum` on the current laptop (2026-07-29 onward).
Anything in older notes reading `C:\Users\mutal\...` is a dead path.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

`JAVA_HOME` must be set in the shell — the launcher needs it before `gradle.properties` applies.

Full gate before committing code:

```powershell
powershell -File C:\Users\USER\MyClaudeProjects\thrum\check.ps1
```

Install to the phone (USB debugging on):

```powershell
adb install -r C:\Users\USER\MyClaudeProjects\thrum\app\build\outputs\apk\debug\app-debug.apk
```

## Fresh clone setup

One file is machine-specific and deliberately gitignored, so a fresh clone will not build until it exists:

- **`local.properties`** — `sdk.dir=C\:\\Users\\USER\\AppData\\Local\\Android\\Sdk`. Without it Gradle fails with *"SDK location not found"* before it compiles anything.

**No truststore any more.** The old `build-truststore.p12` existed only because Avast re-signed every HTTPS connection on the previous laptop. Avast is not installed on this one — verified absent, with zero Avast roots in either certificate store — so Java's own `cacerts` is correct and the three `systemProp.javax.net.ssl.*` lines are gone from `gradle.properties`. `tools/TlsProbe.java` stays, because it diagnoses any future certificate failure in seconds instead of after a long build.

## Signing — the release key (Task 12)

Two gitignored files at the repo root. Together they are the app's identity:

- **`thrum-release.jks`** — the real release key. 4096-bit RSA, SHA384withRSA, valid until 2056.
- **`keystore.properties`** — `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

`app/build.gradle.kts` reads the properties file when it exists and signs release builds with it. When it does not exist — a fresh clone, or any machine without the secret — it falls back to the debug key so `assembleRelease` still runs and R8 can still be checked. Play rejects a debug-signed upload at the door, so the fallback cannot reach users.

> **⚠ Back the keystore up somewhere you will still have in five years, and do not lose the password.** Google Play ties an app to its signing key permanently. If `thrum-release.jks` or its password is lost, the app can never be updated again: a new key means a new listing, and every existing install has to be uninstalled first — which wipes the armed score. Before publishing, a copy belongs somewhere that is not this laptop.

A **release** build cannot install over a **debug** install — different keys, `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Uninstall first, and know that uninstalling wipes the armed score and the tuning dials.

## Machine gotchas

- **AGP 9 has built-in Kotlin.** Do NOT apply `org.jetbrains.kotlin.android` — it collides with "Cannot add extension 'kotlin'". Apply only `com.android.application` + `org.jetbrains.kotlin.plugin.compose`. This is about AGP, not the machine, so it survived the laptop change.
- **KSP must be 2.3.x, never the Kotlin-versioned line.** The Kotlin-matched KSP (e.g. `2.2.20-2.0.4`, which every tutorial names) throws `KSP is not compatible with Android Gradle Plugin's built-in Kotlin` at configuration time. The newer `2.3.x` scheme works with built-in Kotlin: **`2.3.12` compiles and generates Room code on this project, proven 2026-10-03** (Task 17). When Kotlin is bumped, re-check that the KSP in use still accepts built-in Kotlin before believing a clean build.
- Builds took about 4 minutes on the old laptop and should be faster here. Still: don't design a task that needs ten build cycles.
- **R8 renames enum constants.** If anything persists an enum by name via JSON + `valueOf()`, add `-keepclassmembers enum com.mosman.thrum.** { *; }` or saved data silently wipes on upgrade. This exact bug bit `pixel-routines`.
- Screenshots are flaky on this machine. Prefer logs, measurements, and Mutalib's hand on the phone.

## Ground truth for anything haptic

```powershell
adb shell dumpsys vibrator_manager
```

The system's own record of every vibration: who asked, what usage, how long it actually ran, and
what the system did with it — `finished`, `cancelled_by_user`, `cancelled_superseded`,
`ignored_for_ringer_mode`. **Use it before believing any claim about vibration.**

The app's `FIRED` event only records that we *called* the vibrator. It cannot tell the difference
between a motor that ran and a request Android threw away. Task 2 sat on "silent mode works,
240 ms" for a day on the strength of that event; `dumpsys` showed `ignored_for_ringer_mode`,
`duration: 0ms`, and Mutalib's hand had already said so.

## Testing reality

- **Vibration cannot be tested without the physical phone.** Emulators don't do haptics, so an emulator being available on this laptop changes nothing here.
- So the analysis layer is deliberately pure Kotlin with no Android dependencies — it gets real unit tests that run on the PC in seconds. Put logic there, not in Activities.
- Anything touching the vibrator or the notification listener needs Mutalib's Pixel 6 Pro and a real incoming call.

## Key technical fact worth not re-deriving

Android's call ringer (`Ringer.java`, AOSP Telecom) only reads a ringtone's embedded vibration data in vibrate mode if the feature flag `enableRingtoneHapticsCustomization()` is on. On Mutalib's phone it is off, so vibrate mode falls through to `vibrateIfNeeded()` and the flat default pattern. Ring mode works fine. **That gap is the entire product.** Full detail and the source snippet are in `PROFILE.md` §11 R1.

## Related projects

`pixel-routines` (`C:\Users\USER\MyClaudeProjects\pixel-routines`) already solves several problems this app needs: a working `NotificationListenerService`, Material You theming, the permission-grant card pattern, and this exact build setup. Read from it rather than reinventing.
