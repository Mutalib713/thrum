"""Thrum's analyser, reproduced in Python from the Kotlin source.

Written to answer one question with a measurement instead of an estimate:

    How hard is the phone actually driven, compared with Android's own
    incoming-call vibration?

Everything here is a transcription of `Analyser.kt` and `Score.kt`. The point of
the transcription is that it can be checked: `--validate` rebuilds the score for
the settings stored on the phone and compares it, step for step, against the
score that is actually armed in `shared_prefs`. If those agree, the numbers
printed are the phone's numbers and not a model of them. On 2026-09-21 the
agreement was 99.82 % of steps at the Body the phone turned out to be armed
with.

**To use it.** Put two files in this folder (or point `THRUM_MEASURE_DIR` at
somewhere else):

    track.mp3   the audio being analysed, as the phone has it
    thrum.xml   a copy of the app's prefs, which is where the armed score lives

and pull the second one off the phone with:

    adb exec-out run-as com.mosman.thrum cat shared_prefs/thrum.xml > thrum.xml

then:

    python repro.py --validate     # rebuild, check against the armed score,
                                   # and print the drive table for each Body

Needs `ffmpeg` on PATH and `numpy`. `read_stored.py` measures the armed score
alone, without decoding anything; `sweep.py` finds which Body it was built with.
"""
import array
import math
import os
import re
import subprocess
import sys

import numpy as np

# Where the working files live. Defaults to this folder; override with
# THRUM_MEASURE_DIR when the audio is somewhere more convenient.
WORKDIR = os.environ.get("THRUM_MEASURE_DIR") or os.path.dirname(os.path.abspath(__file__))
TRACK = os.path.join(WORKDIR, "track.mp3")
XML = os.path.join(WORKDIR, "thrum.xml")

SR = 48000
STEP_MS = 20

# --- Analyser.kt constants -------------------------------------------------
CUTOFF_HZ = 200.0
ATTACK_SECONDS = 0.003
RELEASE_SECONDS = 0.060
SUSTAINED_SECONDS = 0.350
HIGH_ATTACK_SECONDS = 0.002
HIGH_RELEASE_SECONDS = 0.040
HIGH_SUSTAINED_SECONDS = 0.250
GATE = 0.10
CURVE = 0.55
MIN_FELT = 185
DETAIL_GATE = 0.08
DETAIL_PULSE_MS = 45
DETAIL_MIN = 130
MIN_HEADROOM = 45
DETAIL_CURVE = 0.6
HIT_DECAY = 0.55
BODY_CEILING = 150
RINGTONE_SECONDS = 45
WINDOW_NORM_GUARD = 0.05
MAX_AMPLITUDE = 255
BODY_MS = 240
BODY_MIN_MS = 100
BODY_MAX_MS = 400


def coef_for(seconds, sample_rate=SR):
    """`ScoreBuilder.coefFor`: one-pole coefficient for a time constant."""
    samples = seconds * sample_rate
    if samples <= 0:
        return 1.0
    return 1.0 - math.exp(-1.0 / samples)


def decode_mono():
    """Decode to the mono stream `Pcm.downmixToMono` would produce.

    Android asks MediaCodec for 16-bit PCM and then collapses the channels by
    taking the one with the greater magnitude, sign preserved, first channel
    winning a tie. Averaging would cancel out-of-phase bass, so the rule matters
    and is reproduced rather than approximated.
    """
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", TRACK, "-f", "s16le", "-acodec", "pcm_s16le",
         "-ac", "2", "-ar", str(SR), "-"],
        capture_output=True, check=True,
    ).stdout
    s = np.frombuffer(raw, dtype="<i2").astype(np.int32).reshape(-1, 2)
    mag = np.abs(s)
    # `if abs(v) > abs(loudest)` with the first channel already in `loudest`:
    # strictly greater, so a tie keeps channel 0.
    mono = np.where(mag[:, 0] >= mag[:, 1], s[:, 0], s[:, 1]).astype("<i2")
    return array.array("h", mono.tobytes())


def analyse(mono):
    """`ScoreBuilder.feed` plus `levels()`: one peak per step, per band."""
    c_lp = coef_for(1.0 / (2 * math.pi * CUTOFF_HZ))
    c_atk = coef_for(ATTACK_SECONDS)
    c_rel = coef_for(RELEASE_SECONDS)
    c_sus = coef_for(SUSTAINED_SECONDS)
    c_hatk = coef_for(HIGH_ATTACK_SECONDS)
    c_hrel = coef_for(HIGH_RELEASE_SECONDS)
    c_hsus = coef_for(HIGH_SUSTAINED_SECONDS)

    per_step = max(1, SR * STEP_MS // 1000)
    onsets = []
    detail = []
    ao = onsets.append
    ad = detail.append

    lp1 = lp2 = env = sus = 0.0
    henv = hsus = 0.0
    peak = det = 0.0
    in_step = 0
    primed = False

    for v in mono:
        x = float(v)
        if not primed:
            # Start the followers where the music is, not at zero.
            lp1 = lp2 = env = sus = x
            env = abs(x)
            sus = env
            henv = hsus = 0.0
            primed = True

        lp1 += c_lp * (x - lp1)
        lp2 += c_lp * (lp1 - lp2)

        level = lp2 if lp2 >= 0 else -lp2
        env += (c_atk if level > env else c_rel) * (level - env)
        sus += c_sus * (env - sus)
        onset = env - sus
        if onset > peak:
            peak = onset

        high = x - lp2
        hl = high if high >= 0 else -high
        henv += (c_hatk if hl > henv else c_hrel) * (hl - henv)
        hsus += c_hsus * (henv - hsus)
        ho = henv - hsus
        if ho > det:
            det = ho

        in_step += 1
        if in_step >= per_step:
            ao(peak)
            ad(det)
            peak = det = 0.0
            in_step = 0

    if in_step > 0:
        ao(peak)
        ad(det)
    return onsets, detail


def normalise(band, window_steps=RINGTONE_SECONDS * 1000 // STEP_MS):
    """`ScoreBuilder.normalise`, against the window that plays."""
    global_max = max(band)
    if global_max <= 0:
        return [0.0] * len(band)
    end = min(max(window_steps, 1), len(band))
    window_max = max(band[:end])
    base = window_max if window_max >= WINDOW_NORM_GUARD * global_max else global_max
    if base <= 0:
        return [0.0] * len(band)
    return [min(v / base, 1.0) for v in band]


def round_half_up(x):
    """Kotlin's `Float.roundToInt`: floor(x + 0.5), not banker's rounding."""
    return int(math.floor(x + 0.5))


def steps_for(ms, step_ms=STEP_MS):
    """`ScoreBuilder.stepsFor`: ceiling division."""
    if ms <= 0:
        return 1
    return max(1, (ms + step_ms - 1) // step_ms)


def ceiling_for(punch, distance):
    """`ScoreBuilder.ceilingFor`: how far up the detail layer may reach."""
    floor = min(max(punch, 0), MAX_AMPLITUDE)
    d = min(max(distance, 0), 100)
    if d >= 100:
        return 0
    if floor <= DETAIL_MIN:
        return 0
    span = floor - DETAIL_MIN
    return DETAIL_MIN + max(1, span * (99 - d) // 99)


def hold(amps, min_steps, decay_to, floor):
    """`Score.holdPulsesAtLeast`, transcribed line for line."""
    if min_steps == 1 or not amps:
        return list(amps)
    out = list(amps)
    i = 0
    n = len(out)
    while i < n:
        if out[i] == 0:
            i += 1
            continue
        end = i
        pk = 0
        while end < n and out[end] > 0:
            pk = max(pk, out[end])
            end += 1
        to_add = min_steps - (end - i)
        added = 0
        at = end
        while added < to_add and at < n and out[at] == 0:
            through = (added + 1) / (to_add + 1)
            scale = 1.0 - (1.0 - decay_to) * through
            scaled = int(pk * scale)  # Kotlin Float.toInt() truncates toward zero
            landed = max(scaled, floor) if pk >= floor else scaled
            out[at] = min(max(landed, 0), MAX_AMPLITUDE)
            at += 1
            added += 1
        i = at
    return out


def to_score(onsets, detail, name="", min_felt=MIN_FELT, curve=CURVE, gate=GATE,
             body_ms=BODY_MS, body_ceiling=BODY_CEILING):
    """`ScoreBuilder.toScore`, then `firstSeconds(RINGTONE_SECONDS)`."""
    if not any(v > 0 for v in onsets):
        return [0] * len(onsets)

    floor = min(max(min_felt, 0), MAX_AMPLITUDE - MIN_HEADROOM)

    hits = []
    for level in onsets:
        if level < gate:
            hits.append(0)
        else:
            above = min(max((level - gate) / (1.0 - gate), 0.0), 1.0)
            curved = above ** curve
            hits.append(min(max(round_half_up(floor + curved * (MAX_AMPLITUDE - floor)), 0),
                            MAX_AMPLITUDE))

    ceiling = min(body_ceiling, floor)
    if ceiling <= DETAIL_MIN:
        det = [0] * len(detail)
    else:
        det = []
        for level in detail:
            if level < DETAIL_GATE:
                det.append(0)
            else:
                above = min(max((level - DETAIL_GATE) / (1.0 - DETAIL_GATE), 0.0), 1.0)
                curved = above ** DETAIL_CURVE
                det.append(min(max(round_half_up(DETAIL_MIN + curved * (ceiling - DETAIL_MIN)), 0),
                               MAX_AMPLITUDE))

    min_steps = steps_for(body_ms)
    detail_steps = steps_for(DETAIL_PULSE_MS)
    held = hold(hits, max(min_steps, 1), HIT_DECAY, floor)
    held_det = hold(det, max(detail_steps, 1), HIT_DECAY, DETAIL_MIN)
    combined = [max(a, b) for a, b in zip(held, held_det)]

    keep = RINGTONE_SECONDS * 1000 // STEP_MS
    return combined[:keep] if len(combined) > keep else combined


def metrics(amps, label):
    """The numbers that decide whether a table moves."""
    driving = [v for v in amps if v > 0]
    duty = len(driving) / len(amps) if amps else 0.0
    mean_on = sum(driving) / len(driving) if driving else 0.0

    def longest(pred):
        best = cur = 0
        for v in amps:
            if pred(v):
                cur += 1
                best = max(best, cur)
            else:
                cur = 0
        return best * STEP_MS

    print(f"{label}")
    print(f"    duty            {duty * 100:5.1f} %   ({len(driving)}/{len(amps)} steps on)")
    print(f"    mean while on   {mean_on:5.1f} / 255")
    print(f"    SUSTAINED DRIVE {duty * mean_on / 255:.3f}")
    print(f"    longest >=185   {longest(lambda v: v >= MIN_FELT):5.0f} ms")
    print(f"    longest =255    {longest(lambda v: v >= 255):5.0f} ms")
    print(f"    longest >0      {longest(lambda v: v > 0):5.0f} ms")
    print(f"    still           {sum(1 for v in amps if v == 0) / len(amps) * 100:5.1f} %")
    return duty * mean_on / 255


def stored():
    text = open(XML, encoding="utf-8").read()
    raw = re.search(r'<string name="armed_score">(.*?)</string>', text, re.S).group(1)
    version, step_ms, name, csv = raw.split("|")
    return [int(x) for x in csv.split(",")] if csv else [], name


def main():
    validate = "--validate" in sys.argv
    print("decoding…")
    mono = decode_mono()
    print(f"  {len(mono)} mono samples at {SR} Hz = {len(mono) / SR:.1f} s")

    print("analysing (this is the slow part)…")
    onsets_raw, detail_raw = analyse(mono)
    print(f"  {len(onsets_raw)} steps = {len(onsets_raw) * STEP_MS / 1000:.1f} s")
    onsets = normalise(onsets_raw)
    detail = normalise(detail_raw)
    print(f"  onsets 0..{max(onsets):.3f}   detail 0..{max(detail):.3f}")

    armed, name = stored()
    print(f"\narmed on the phone: {name}")

    if validate:
        # The settings the phone has stored. If the rebuilt score matches the
        # armed one, this harness is the analyser and its numbers are the
        # phone's, not a model of them.
        text = open(XML, encoding="utf-8").read()
        vals = dict(re.findall(r'<(?:int|boolean) name="([a-z_]+)" value="([^"]*)"', text))
        punch, distance, body = int(vals["punch"]), int(vals["distance"]), int(vals["body"])
        print(f"  settings stored: punch={punch} distance={distance} body={body}")
        print(f"  ceilingFor     : {ceiling_for(punch, distance)}")

        rebuilt = to_score(onsets, detail, name, min_felt=punch,
                           body_ms=body, body_ceiling=ceiling_for(punch, distance))
        n = min(len(rebuilt), len(armed))
        exact = sum(1 for i in range(n) if rebuilt[i] == armed[i])
        near = sum(1 for i in range(n) if abs(rebuilt[i] - armed[i]) <= 2)
        print(f"\n--- validation against the armed score ({n} steps) ---")
        print(f"  identical steps : {exact}/{n} = {exact / n * 100:.2f} %")
        print(f"  within +/-2     : {near}/{n} = {near / n * 100:.2f} %")
        print(f"  rebuilt length  : {len(rebuilt)}   armed length: {len(armed)}")
        if exact < n:
            diffs = [(i, rebuilt[i], armed[i]) for i in range(n) if rebuilt[i] != armed[i]]
            print(f"  first 10 differences (step, rebuilt, armed): {diffs[:10]}")

    print("\n--- what each Body setting actually drives ---")
    for body_ms in (BODY_MIN_MS, BODY_MS, BODY_MAX_MS):
        amps = to_score(onsets, detail, name, min_felt=208,
                        body_ms=body_ms, body_ceiling=ceiling_for(208, 61))
        metrics(amps, f"Body {body_ms} ms")
        print()

    metrics(armed, "AS ARMED ON THE PHONE (ground truth)")
    print()
    print("Android's own call vibration, for comparison:")
    print("    duty            50.0 %   (1000 ms on, 1000 ms off)")
    print("    mean while on   255.0 / 255")
    print("    SUSTAINED DRIVE 0.500")
    print("    longest >=185   1000 ms")


if __name__ == "__main__":
    main()
