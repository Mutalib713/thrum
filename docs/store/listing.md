# Thrum — Play Store listing

Task 13. The copy below is final except for the assets, which are listed at the
bottom and are the only things still missing.

**The name is settled: Thrum.** It already matches `applicationId`
(`com.mosman.thrum`), which is permanent on Play once published, so nothing has to
move underneath it.

Limits, confirmed against Play's current rules: **title 30 · short description 80 ·
full description 4000.** The counts under each block are real, not estimated.

---

## Title — 28 / 30

```
Thrum: ringtone you can feel
```

Contains "ringtone", which is the word people actually search, and says what the
app does without hype. No "best", no "amazing", no emoji.

## Short description — 75 / 80

```
Hear your ringtone, feel it too. Needs a phone with a good vibration motor.
```

**The hardware warning is in this line on purpose.** The short description is the
only text that appears in search results and the first thing anyone reads, so
putting the requirement here means nobody installs by accident and leaves a
one-star review — which is exactly what `PROFILE.md` R4 is about and what Sacred
Rule 2 forbids. The remaining five characters are not worth spending on keywords
at that price.

## Full description — 3,049 / 4,000

```
Your ringtone, in your hand.

Android has one vibrate pattern, and it is the same for everybody. It says
nothing. Thrum takes a song already on your phone, works out where the beats and
the weight are, and makes your phone vibrate to that instead — so when someone
calls in vibrate mode, you feel the shape of the song you chose.

Nothing comes out of the speaker. This is for vibrate mode.

FIRST, THE HONEST BIT — PLEASE READ IT

Thrum needs a phone whose vibration motor can change strength. Most flagship and
mid-to-high phones can: Pixel, recent Samsung Galaxy, and most others in that
class.

Many budget phones cannot. If yours has a spinning-weight motor it has one speed,
and every rhythm reaches you as the same flat buzz you already get. No setting, no
update, and no future version of this app can change that. It is physics, not
software.

So Thrum checks on first launch and tells you plainly. If your phone cannot do it,
the app says so and stops there — rather than letting you spend an evening on
something that was never going to work.

HOW IT WORKS

1. Pick any song already on your phone.
2. Thrum reads it and finds the rhythm — the bass and the drums, because that is
   what a hand actually feels.
3. Play it with the song to feel it, adjust it if you want, then arm it.

From then on, leave your phone on vibrate. When someone calls, you feel your song.

SET YOUR PHONE TO VIBRATE, NOT SILENT

Silent is not the same thing. Android throws the vibration away in silent mode
before it reaches the motor, and no app can get around that. Vibrate is the
setting this app needs — and the app tells you which one you are on, so you are
never left guessing.

YOUR SONG NEVER LEAVES YOUR PHONE

There is no internet permission in this app. Not "we don't use it" — it is not
there. Nothing is uploaded, nothing is analysed anywhere but on your phone, and
there is no account, no server, and no analytics. Thrum converts files that are
already on your device. It does not download, ship, or share any music.

To notice a call, Thrum uses Android's notification access, which you grant and can
revoke in system settings. It looks at one thing — is this a call? — and ignores
everything else. It never asks for access to your phone calls, and it does not
replace your dialer.

WHAT THRUM DOES NOT DO

- It does not add haptics to music apps. Spotify, YouTube Music, TikTok and
  WhatsApp calls are permanently out of reach: Android gives no app access to
  another app's audio. Anything claiming otherwise is selling you something.
- It does not do notification sounds, alarms, or messaging apps.
- It does not work on phones without strength-controllable vibration. See above.
- It does not collect anything, anywhere, ever.

FOR THE PEOPLE WHO WANT THE NUMBERS

The rhythm is an amplitude envelope over time, taken from the low-frequency energy
of the track and stored as a score of a few thousand steps at 20 ms each. One
screen, one active score. Kotlin and Jetpack Compose. No ads, no in-app purchases,
no paid tier, no subscription.
```

---

## Play Console — what to answer

These are the forms that get apps rejected if they are answered badly, so the text
is written to paste. **Read the current wording in the Console before submitting** —
Play changes form labels without changing what they mean, and this file cannot
promise what the form looks like on the day.

### Data safety

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all user data encrypted in transit? | Not applicable — no data is transmitted. The app has no `INTERNET` permission. |
| Do you provide a way for users to request data deletion? | Not applicable — no data is collected. |

The absence of the `INTERNET` permission is the proof, not a promise. It is
verifiable in the manifest and worth saying so if asked.

### Notification access declaration

The declaration Play asks for when an app uses a notification listener. This is
the whole justification, and it is short because the reason is genuinely narrow:

```
Thrum needs notification access for one reason: to know when a call is arriving.

Android posts an incoming call as a notification. Reading that notification is how
Thrum starts its vibration without asking for READ_PHONE_STATE and without
replacing the user's dialer — both of which are far heavier asks for an app whose
only job is to vibrate.

Thrum inspects only the notification's category. It acts on notifications whose
category is CATEGORY_CALL and ignores every other notification entirely. It does
not read, store, display, or transmit notification content, and it has no INTERNET
permission, so nothing it reads can leave the device.

The permission is explained in plain language inside the app before it is
requested, and the user grants and revokes it in system settings. Thrum never
changes a setting on the user's behalf.
```

### Everything else

- **Ads:** no.
- **In-app purchases:** no.
- **Content rating:** everyone. Nothing in the app shows or produces content —
  it plays the user's own file through a vibration motor.
- **Target audience:** 18+ is not required, but the app is aimed at adults who
  own a capable phone. No children's categories.
- **News app:** no. **Government app:** no. **Financial features:** no.
- **Privacy policy:** required by the Console even with nothing collected.
  Written, in `privacy-policy.html`. **Still needs a public URL** — see below.

---

## Review against the Sacred Rules

**Rule 2 — never let a user believe the app works when their hardware cannot.**
The hardware requirement is in the short description, which is the only text
visible in search results, and again in the first section of the full description
*before* any feature is described. It names which phones can and cannot, says it
is physics rather than software, and says the app checks on first launch and stops.
Nobody can install this without having been told.

**Rule 4 — never ship, host, or redistribute audio.** The listing states that Thrum
converts files already on the device, and nowhere claims to provide, download, or
share music. The privacy section says it explicitly.

**Rule 3 — everything runs on the phone.** Stated with the missing `INTERNET`
permission as evidence rather than as an assurance.

**Rule 5 — never claim system-wide audio haptics.** There is a whole section headed
"What Thrum does not do" that names Spotify, YouTube Music, TikTok and WhatsApp
calls as permanently out of reach. This is the single most common way an app like
this gets a one-star review, so it is answered before anyone can be disappointed.

**Rule 8 — plain language first.** Reviewed line by line. No "haptics", no
"amplitude envelope" outside the section explicitly addressed to people who want
the numbers, and "vibration motor" is the only technical term used before that.

---

## Assets — the only things still missing

Everything below is in `docs/store/assets/` unless stated otherwise.

| Asset | Spec | Status |
|---|---|---|
| Store icon | 512×512, 32-bit PNG | **Done** — `icon-512.png`, RGBA, generated from the same five bars as the vector |
| Feature graphic | 1024×500 PNG/JPG | **Done** — `feature-graphic.png` |
| Privacy policy | A hosted URL | Written as `privacy-policy.html`; **needs hosting** |
| Phone screenshots | 2–8, min 320 px, 16:9 or 9:16 | **Blocked on the phone** |
| Short promo video | Optional | Skipped deliberately — nothing to show that a screenshot does not |

Both images are rendered from HTML by headless Chrome rather than drawn by hand,
so they are reproducible: edit the HTML, re-run the render, and the geometry
matches the app's own vector instead of resembling it.

Screenshots are the last blocker and they need the app running on the Pixel. The
most useful ones are of the armed screen and the pulse ribbon mid-play. Take them
in the same sitting as the soak test.

### Rendering the images again

Chrome will not write to a relative path here — pass an absolute one, or the
command fails with `Access is denied` and no file:

```
"/c/Program Files/Google/Chrome/Application/chrome.exe" \
  --headless=new --disable-gpu --hide-scrollbars \
  --force-device-scale-factor=1 --window-size=512,512 \
  --screenshot="C:\...\docs\store\assets\icon-512.png" \
  "file:///C:/.../docs/store/assets/icon-512.html"
```

Chrome emits 24-bit RGB. Play's spec says 32-bit, so the icon is converted to
RGBA afterwards — see `icon-512.html` for the note. Re-convert if you re-render.

---

## One thing the listing forced: backups are now off

Writing the privacy policy meant checking that its central claim was true rather
than aspirational, and it was not. The manifest had `android:allowBackup="true"`,
which is the Android default and means Auto Backup can copy app data into the
user's Google account. What it would have copied: the chosen track's `content://`
reference, the derived score, the event log, and the three dial values.

The app now declares `allowBackup="false"` **and** ships
`res/xml/data_extraction_rules.xml`, which excludes every domain from both cloud
backup and device-to-device transfer. The second part is not belt-and-braces: on
Android 12+, `allowBackup="false"` stops cloud backup but can still permit a D2D
transfer on phones from some manufacturers. `minSdk` is 31, so every device Thrum
can install on is affected — there is no older device to account for, and
therefore no `fullBackupContent` companion file is needed.

The user gives up nothing that matters. None of that data is personal, and all of
it is reproducible by picking a song again, so excluding it costs a re-pick after
a phone migration and buys a privacy claim that holds up under inspection.

`files/last-score.txt` was also being written on every decode **in release
builds**, for a file nothing in the shipped app reads. It is now behind
`BuildConfig.DEBUG`, so the diagnostics probe still works on a dev build and the
release build stops doing work for nobody.

