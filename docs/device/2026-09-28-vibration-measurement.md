# Thrum — the vibration measured, 2026-09-28

Mutalib's report, verbatim: *"still i cant feel anything aint strong enough"*.

That is the standing gate answered, and it arrived with Body already at 400 (its
maximum) and Punch at 210 (its ceiling). So the usual lever — turn the dial up —
did not exist. The question was whether the motor was doing anything at all, and
"weak" and "nothing happens" have completely different causes.

It does. **The phone physically shakes.** What is wrong is the *shape* of what it
plays, and this file is the evidence for both halves of that.

---

## How it was measured, and why it is not a guess

`adb shell dumpsys sensorservice` keeps the last 50 accelerometer samples. At the
LSM6DSR's ~111 Hz that is a ~450 ms window, and the windows are **live** — verified
by the wall-clock timestamps advancing between captures (17:35:23.614 → 17:35:26.626
→ 17:35:30.560 → 17:35:35.560), not by assuming it.

So the method is: capture an idle window, tap **Run the tap test** on the phone,
then capture again at 1 s, 4 s and 8 s. Nothing is inferred from what the app says
it did.

The phone was lying flat and untouched throughout — the idle capture reads
`|g| mean = 9.74`, `x sd = 0.0042`, `y sd = 0.0071`, `z sd = 0.0096`. That is
gravity and nothing else.

### The result

| capture | x sd | y sd | z sd | \|g\| sd | max jerk |
|---|---|---|---|---|---|
| **idle** | 0.0042 | 0.0071 | 0.0096 | 0.0096 | 0.030 |
| vib 1 s | 0.0965 | 0.1740 | 0.0183 | 0.0193 | 1.160 |
| vib 4 s | 0.0658 | 0.1629 | 0.0213 | 0.0212 | 1.000 |
| vib 8 s | 0.1885 | 0.5273 | 0.1006 | 0.1110 | 3.240 |

Against idle: **|g| sd ×11.6, max jerk ×108, |g| range ×17.4.** The final samples
read `(-0.23, -0.79, 9.96)`, `(0.39, 1.49, 9.56)`, `(-0.57, -1.75, 10.01)` — up to
**1.75 g sideways**.

A handset that is not moving cannot do that. **The motor, the HAL and the app's
call path are all working, and this is now measured rather than argued.**

---

## What the app is actually asking for

From `dumpsys vibrator_manager`, 42 entries for `com.mosman.thrum`, all
`usage: RINGTONE`:

- **statuses: `cancelled_superseded` ×24, `cancelled_by_user` ×18. Not one
  `finished`.** Expected — Thrum loops, so it is always replaced or stopped by
  something, never allowed to run out.
- durations range from **8 ms** to **37,864 ms**. The 8 ms ones are imperceptible
  on their own; the multi-second ones are the real firings.
- amplitude profile of a firing: **2,313 steps of 20 ms, 89.6% non-zero,
  max 1.00, mean of the non-zero steps 0.84.**

The motor's own report: `id = 0`, capabilities include **`AMPLITUDE_CONTROL`**,
`q-factor = 24.59`, `mResonantFrequency = 149.71 Hz`. Normal Pixel 6 Pro LRA.

And the phone's settings are not the cause: `ring_vibration_intensity = 3`,
`alarm_vibration_intensity = 3`, `haptic_feedback_enabled = 1`,
`vibrate_when_ringing = 1`, `zen_mode = 0`. Nothing is scaled down — the dump says
`scale: NONE (1.00)` on every entry.

---

## The actual defect: it is a buzz, not a rhythm

**89.6% of the time the motor is on, at a near-constant 0.84.** The remaining
10.4% is scattered single 20 ms gaps.

That is not a beat. A hand feels a haptic through its **onset** — the transient —
and a drive that is already on, and stays on, and changes level by a few percent,
has almost no transient to feel. It reads as a soft flat hum, and a soft flat hum
is exactly the thing this app exists to replace. Mutalib's "I can't feel anything"
is a fair description of a continuous hum even though the phone is visibly moving.

**So the paradox resolves.** Turning Punch up made it *worse*, not better: Punch is
the floor (`minFelt`), and with the floor at 210 out of 255 nearly everything
clears it, so almost nothing is silenced and the score saturates into a solid
band. Louder, flatter, less like a beat.

### The number that changed, and why it matters

The armed screen said **`still 32% of the time`** earlier the same day. It now says
**`still 10% of the time`**, and the waveform's step count moved from 2,488 to
2,313. Same file, same dials except Punch 208 → 210.

**That is the wrong direction for that change.** Raising `minFelt` from 208 to 210
can only push *more* steps below the floor, so `still` should have gone up, not
from 32% down to 10%. The score itself must have been rebuilt differently.

This is not proven and is written down as open rather than guessed at. But it is
the most important lead in the file, because it says the same song may not produce
the same score twice — and a rhythm that quietly changes between runs cannot be
tuned by ear at all.

---

## What to do next

1. **The tap test is the discriminator, and it takes ten seconds.** It plays short
   sharp pulses rather than a flat drive, and those pulses are what moved the phone
   by 1.75 g above. If Mutalib can feel the taps but not the armed rhythm, then the
   motor and the app are both fine and the fault is entirely in the score's shape.
   If he cannot feel the taps either, the problem is elsewhere and this file's
   conclusion is wrong.
2. **Find out why the score changed between two re-decodes of one file.** Decode
   `armed_score` from `shared_prefs/thrum.xml` and compare its amplitude histogram
   against a fresh analysis on the PC. Until that is explained, every tuning
   judgement on the phone is being made against a moving target.
3. **Then, and only then, the contrast.** The fix direction is more silence and
   more dynamic range — real gaps between hits — not more amplitude, which is
   already at the ceiling and is what produced the hum.

## Reproducing the measurement

`parse_vib.py` and `parse_sensors.py` in the session workspace do the parsing. Both
were written as files rather than heredocs because the shell silently ate the regex
backslashes, which produced zero matches twice before it was noticed — worth
knowing before trusting an empty result from a shell-embedded script.
