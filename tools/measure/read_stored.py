"""Measure the score that is actually armed on the phone.

Read straight out of a pulled copy of the app's `shared_prefs/thrum.xml`, so this
is the motor's own instructions — no re-derivation, no estimates, no decoding.
Whatever this says is what the phone plays on an incoming call.

    adb exec-out run-as com.mosman.thrum cat shared_prefs/thrum.xml > thrum.xml
    python read_stored.py

This is the check to run after any tuning change: it is the only thing that
says what the phone will really do, as opposed to what the screen says it will.
"""
import os
import re
import sys

XML = os.path.join(
    os.environ.get("THRUM_MEASURE_DIR") or os.path.dirname(os.path.abspath(__file__)),
    "thrum.xml",
)

text = open(XML, encoding="utf-8").read()

m = re.search(r'<string name="armed_score">(.*?)</string>', text, re.S)
if not m:
    sys.exit("no armed_score in prefs")

raw = m.group(1)
parts = raw.split("|")
if len(parts) != 4:
    sys.exit(f"armed_score has {len(parts)} fields, expected 4")

version, step_ms, name, amps_csv = parts
step_ms = int(step_ms)
amps = [int(x) for x in amps_csv.split(",")] if amps_csv else []

print(f"source     : {name}")
print(f"stepMs     : {step_ms}")
print(f"steps      : {len(amps)}")
print(f"duration   : {len(amps) * step_ms / 1000:.1f} s")


def scalars(pattern):
    out = {}
    for key, val in re.findall(pattern, text):
        out[key] = val
    return out


print("settings   :", scalars(r'<(?:int|boolean) name="([a-z_]+)" value="([^"]*)"'))

# The ringtone window: only this much is ever played.
WINDOW_S = 45
window = amps[: WINDOW_S * 1000 // step_ms]
print(f"window     : first {len(window) * step_ms / 1000:.0f} s = {len(window)} steps")

MIN_FELT = 185


def longest_run(seq, pred):
    best = cur = 0
    for v in seq:
        if pred(v):
            cur += 1
            if cur > best:
                best = cur
        else:
            cur = 0
    return best


driving = [v for v in window if v > 0]
duty = len(driving) / len(window) if window else 0.0
mean_on = sum(driving) / len(driving) if driving else 0.0

print()
print("--- the armed score, over the window that plays ---")
print(f"duty cycle            : {duty * 100:5.1f} %   ({len(driving)}/{len(window)} steps on)")
print(f"mean while driving    : {mean_on:5.1f} / 255")
print(f"mean over window      : {sum(window) / len(window):5.1f} / 255")
print(f"sustained drive       : {duty * mean_on / 255:.3f}")
print(f"longest run at 255    : {longest_run(window, lambda v: v >= 255) * step_ms:5.0f} ms")
print(f"longest run >= 185    : {longest_run(window, lambda v: v >= MIN_FELT) * step_ms:5.0f} ms")
print(f"longest run > 0       : {longest_run(window, lambda v: v > 0) * step_ms:5.0f} ms")
print(f"steps at 0            : {sum(1 for v in window if v == 0) / len(window) * 100:5.1f} % still")
print(f"steps in dead zone    : {sum(1 for v in window if 0 < v < MIN_FELT) / len(window) * 100:5.1f} %  (0<v<185)")
print(f"distinct amplitudes   : {len(set(window))}")
print(f"min / max amplitude   : {min(window)} / {max(window)}")

# A short trace of the opening, so the shape is visible rather than just summed.
print()
print("--- first 60 steps ---")
for i in range(0, min(60, len(window)), 20):
    chunk = window[i : i + 20]
    print(f"{i * step_ms:6d} ms  " + " ".join(f"{v:3d}" for v in chunk))

# Histogram, so "it is all one loud plateau" and "it is mostly silence" are
# distinguishable at a glance rather than only in the mean.
print()
print("--- distribution ---")
buckets = [(0, 0), (1, 129), (130, 184), (185, 219), (220, 254), (255, 255)]
for lo, hi in buckets:
    n = sum(1 for v in window if lo <= v <= hi)
    bar = "#" * int(n / len(window) * 60)
    print(f"{lo:3d}-{hi:3d} : {n:5d}  {n / len(window) * 100:5.1f} %  {bar}")
