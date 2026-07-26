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

This machine has no working Android Studio and no emulator. Command line only.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
& 'C:\Users\mutal\.gradle\wrapper\dists\gradle-9.4.1-bin\arn2x92ynaizyzdaamcbpbhtj\gradle-9.4.1\bin\gradle.bat' -p C:\Users\mutal\thrum :app:assembleDebug --no-daemon --max-workers=1
```

`JAVA_HOME` must be set in the shell — `gradle.bat` needs it before `gradle.properties` applies.

Full gate before committing code:

```powershell
powershell -File C:\Users\mutal\thrum\check.ps1
```

Install to the phone (USB debugging on):

```powershell
adb install -r C:\Users\mutal\thrum\app\build\outputs\apk\debug\app-debug.apk
```

## Machine gotchas (learned the hard way on pixel-routines)

- **Avast MITMs all HTTPS.** JVMs fail certificate checks and Gradle reports a misleading "plugin not found". Fix: local JKS truststore wired via `systemProp.javax.net.ssl.trustStore*` in `gradle.properties`. `trustStoreType=Windows-ROOT` does NOT work.
- **Avast also locks Gradle transform outputs mid-build** → *"Could not move temporary workspace … to immutable location"*. Build with `--max-workers=1`, or just re-run.
- **AGP 9 has built-in Kotlin.** Do NOT apply `org.jetbrains.kotlin.android` — it collides with "Cannot add extension 'kotlin'". Apply only `com.android.application` + `org.jetbrains.kotlin.plugin.compose`.
- Builds take about 4 minutes. Don't design a task that needs ten build cycles.
- **R8 renames enum constants.** If anything persists an enum by name via JSON + `valueOf()`, add `-keepclassmembers enum com.mosman.thrum.** { *; }` or saved data silently wipes on upgrade. This exact bug bit `pixel-routines`.
- Screenshots are flaky on this machine. Prefer logs, measurements, and Mutalib's hand on the phone.

## Testing reality

- **Vibration cannot be tested without the physical phone.** No emulator here, and emulators don't do haptics.
- So the analysis layer is deliberately pure Kotlin with no Android dependencies — it gets real unit tests that run on the PC in seconds. Put logic there, not in Activities.
- Anything touching the vibrator or the notification listener needs Mutalib's Pixel 6 Pro and a real incoming call.

## Key technical fact worth not re-deriving

Android's call ringer (`Ringer.java`, AOSP Telecom) only reads a ringtone's embedded vibration data in vibrate mode if the feature flag `enableRingtoneHapticsCustomization()` is on. On Mutalib's phone it is off, so vibrate mode falls through to `vibrateIfNeeded()` and the flat default pattern. Ring mode works fine. **That gap is the entire product.** Full detail and the source snippet are in `PROFILE.md` §11 R1.

## Related projects

`pixel-routines` (`C:\Users\mutal\pixel-routines`) already solves several problems this app needs: a working `NotificationListenerService`, Material You theming, the permission-grant card pattern, and this exact build setup. Read from it rather than reinventing.
