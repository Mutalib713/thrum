# Thrum

Android vibrate mode ignores your ringtone. Whatever track you picked, your pocket gets the same flat buzz.

Thrum reads the track you actually chose, works out where its weight and beats are, and plays that through the vibration motor when a call comes in. Silent, but you can feel which song is ringing.

iPhones have done this for years. Android has the hardware and most of the plumbing — it just leaves the last step switched off.

## Status

Planning. Nothing built yet. See [PROFILE.md](PROFILE.md) for the spec and [PLAN.md](PLAN.md) for the task list.

## Requirements

A phone with a vibration motor that can vary its strength — Pixel, Samsung flagship, and similar. Phones with the older spinning-weight motor can only buzz at one strength, so there is nothing for Thrum to work with. The app checks your hardware on first launch and tells you straight if it can't help.

Android 12 or newer.

## Why it isn't a background service watching your calls

The heavy lifting happens up front. You convert a track once, the app stores the result, and nothing needs to be awake until a call arrives. No account, no server, no network permission. Your audio never leaves the phone.

## What it can't do

Spotify, YouTube Music, WhatsApp calls, TikTok. Android gives no app access to another app's audio, so there is no version of this that makes your music vibrate. That works on iPhone only because Apple owns the whole stack.

## Licence

Not yet decided.
