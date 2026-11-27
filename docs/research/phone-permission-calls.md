# Can Thrum catch calls with the Phone permission instead? (Task 32)

Started 2026-11-27. Research first; the answers that need real calls are left open below and filled
in from the test build's probe.

## Why this matters, in plain words

Thrum learns that the phone is ringing by reading the call notification. That needs **notification
access**, and Android treats any app asking for it as a possible scam app (the same access reads
one-time codes). Two locks follow when Thrum is sent as a file instead of installed from the Play
Store:

1. **Restricted settings.** The call-access switch is greyed out until the user opens App info,
   taps ⋮ and *Allow restricted settings*. Seen on Mutalib's Pixel 6 Pro (Android 17) on
   2026-10-06 and again on 2026-11-27: the hidden flag (`ACCESS_RESTRICTED_SETTINGS`) read
   `ignore`, and the ⋮ menu offered "Allow restricted settings".
2. **Play Protect's enhanced fraud protection** refuses the install outright on many phones.
   Friends' phones were blocked on 2026-10-04.

Android has a second way to say "ringing": the **Phone permission** (`READ_PHONE_STATE`). It is an
ordinary pop-up permission, not one of the locked ones. If it is good enough, Thrum can drop
notification access, and both locks go away. A third wall, developer verification, is separate
and is described at the end.

## What the documents say

| Claim | Source | Date |
|---|---|---|
| `ACTION_PHONE_STATE_CHANGED` is on the short list of signals Android still delivers to apps that declare a listener in their manifest, even when they aren't running | [Implicit broadcast exceptions](https://developer.android.com/guide/components/broadcast-exceptions) | page undated, read 2026-11-27 |
| A background app may vibrate only with a ringtone, notification or alarm usage. Thrum's call vibration already uses the ringtone usage (`Haptics.kt`) | [VibratorManager.vibrate](https://developer.android.com/reference/android/os/VibratorManager) | read 2026-11-27 |
| From Android 12, an app in the background can't start a foreground service except in listed cases. No source found that lists "ringing" as one of those cases | [Foreground service restrictions](https://developer.android.com/about/versions/12/foreground-services) | read 2026-11-27 |
| Enhanced fraud protection covers 185 markets; no list is published, so Ghana is likely, not confirmed | [Google security blog](https://security.googleblog.com/2026/02/keeping-google-play-android-app-ecosystem-safe-2025.html?m=1) | Feb 2026 |
| It looks for SMS reading and one-time-code interception | [Google Malaysia announcement](https://blog.google/intl/en-my/company-news/technology/google-launches-enhanced-protection-in-google/) | 2026 |
| It also blocks notification listeners and accessibility services (secondary source) | [HomeLauncher docs PR](https://github.com/p-prakhar/HomeLauncher/pull/69) | 2026 |
| No source found that puts the Phone permission on that list | (absence, not proof) | 2026-11-27 |

## What people said

- **Stack Overflow, 2023-02-08:** [Broadcast receiver with PHONE_STATE stops after the app is killed](https://stackoverflow.com/questions/75381913/broadcast-receiver-with-action-phone-state-is-not-working-after-app-gets-killed).
  The asker says it works while the app is open or in recents, and not after swiping it away. No
  answer. The phone isn't named, and some makers force-stop an app when it is swiped away, which
  Android itself doesn't do. **This is the doubt the probe has to settle on a Pixel.**
- **X, 2026-11-21:** another developer telling their users the same unlock, App info, three dots,
  [Allow restricted settings](https://x.com/leviackermanft/status/2124146487026483211). The lock is
  a common, current problem, not something Thrum caused.
- **Hacker News, 2022-05-05:** [the restricted-settings design was discussed when Android 13 brought it in](https://news.ycombinator.com/item?id=31268787):
  the lock is tied to how the app was installed, not to what it does.
- **Reddit: blocked.** Chrome refuses reddit.com; not read.

## The five questions, and how the probe answers them

The probe is in the test build only (`app/src/debug/`), so the real app never asks for the Phone
permission. Developer tools → *Phone permission test*.

| # | Question | How | Answer |
|---|---|---|---|
| 1 | Does "ringing" reach Thrum when it is closed, swiped away, and after a restart, on Android 17? | The probe writes how long Thrum had been running. A few hundred ms means Android started Thrum just to deliver it | [NEEDS: real calls] |
| 2 | Earlier or later than the call notification (0.3–0.7 s late today)? | Both lines carry the time to the millisecond | [NEEDS: real calls] |
| 3 | What does Android's permission box say? | The button shows the real box; screenshot it | [NEEDS: his screenshot] |
| 4 | Can the vibration last the whole ring from this signal alone? | "Probe vibrates" on and call access off; then `adb shell dumpsys vibrator_manager` gives how long it really ran | [NEEDS: real calls] |
| 5 | Does Play Protect let such an app install from WhatsApp on a friend's phone? | A separate tiny test app with only this permission | Not built yet |

## The third wall: developer verification (2027)

Google's [developer verification](https://developer.android.com/developer-verification) has been
enforced since 2026-09-30, but only in Brazil, Indonesia, Singapore and Thailand. It goes "globally in
2027"; Ghana isn't named. Installs over USB (`adb`) are exempt. A free "limited distribution"
account (no ID) shares an app with a capped number of devices: 20 in the
[blog of 2026-03-19](https://android-developers.googleblog.com/2026/03/android-developer-verification.html)
and the FAQ, 50 on an undated guide page. Without one, each friend goes through an "advanced flow"
with a one-day wait. So even with the Phone permission, sending Thrum as a file gets harder in 2027.
