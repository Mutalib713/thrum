# Thrum — session rules

**Read `PROFILE.md` before anything else. It is canonical.** Then `PLAN.md` for what to do next.

## What this app is, in one line

Makes Android's vibrate mode follow the rhythm of your own ringtone instead of buzzing a flat pattern.

## Standing rules

- Commit AND push after every meaningful change. Never wait for a green build.
- **NEVER add AI attribution to commits, PRs, or pushes.** No `Co-Authored-By`, no "Generated with", nothing.
- `PROFILE.md` is canonical. Sacred Rules need Mutalib's explicit approval to change.
- Verify with evidence — test output, measurements, a hand on the phone. Not claims.
- **Explain in plain language first, unprompted, then the technical version. Define every term on first use.** Mutalib is new to the field; leading with jargon means he silently doesn't follow and searches it up later. This applies to conversation AND to the app's own copy.
- If a request bundles several asks, restate them as a numbered list and confirm priority before starting.
- If "done" is undefined ("make it better"), propose concrete done-criteria and get a yes first.

## Sacred Rules (copied from PROFILE.md §6 — do not diverge)

1. v1 ships **one screen and one active vibration score.** Doesn't fit? It's v2.
2. **Never let a user believe the app works when their hardware cannot do it.** The capability check runs first and its verdict is honest.
3. **Everything runs on the phone. No server, ever.** Nothing leaves the device.
4. **Never ship, host, or redistribute audio.** Convert what's already on the user's device, nothing else.
5. **Never claim or attempt system-wide audio haptics.** Android gives no app access to another app's audio. Spotify, YouTube Music, WhatsApp calls are permanently out of reach.
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

## Machine gotchas

- **AGP 9 has built-in Kotlin.** Do NOT apply `org.jetbrains.kotlin.android` — it collides with "Cannot add extension 'kotlin'". Apply only `com.android.application` + `org.jetbrains.kotlin.plugin.compose`. This is about AGP, not the machine, so it survived the laptop change.
- Builds took about 4 minutes on the old laptop and should be faster here. Still: don't design a task that needs ten build cycles.
- **R8 renames enum constants.** If anything persists an enum by name via JSON + `valueOf()`, add `-keepclassmembers enum com.mosman.thrum.** { *; }` or saved data silently wipes on upgrade. This exact bug bit `pixel-routines`.
- Screenshots are flaky on this machine. Prefer logs, measurements, and Mutalib's hand on the phone.

## Testing reality

- **Vibration cannot be tested without the physical phone.** Emulators don't do haptics, so an emulator being available on this laptop changes nothing here.
- So the analysis layer is deliberately pure Kotlin with no Android dependencies — it gets real unit tests that run on the PC in seconds. Put logic there, not in Activities.
- Anything touching the vibrator or the notification listener needs Mutalib's Pixel 6 Pro and a real incoming call.

## Key technical fact worth not re-deriving

Android's call ringer (`Ringer.java`, AOSP Telecom) only reads a ringtone's embedded vibration data in vibrate mode if the feature flag `enableRingtoneHapticsCustomization()` is on. On Mutalib's phone it is off, so vibrate mode falls through to `vibrateIfNeeded()` and the flat default pattern. Ring mode works fine. **That gap is the entire product.** Full detail and the source snippet are in `PROFILE.md` §11 R1.

## Related projects

`pixel-routines` (`C:\Users\USER\MyClaudeProjects\pixel-routines`) already solves several problems this app needs: a working `NotificationListenerService`, Material You theming, the permission-grant card pattern, and this exact build setup. Read from it rather than reinventing.
