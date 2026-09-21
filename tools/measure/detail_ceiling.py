"""How much of the dial's travel can actually reach the dead zone?

`QaSuiteTest.analyse` pins `detailCeiling` to `ScoreBuilder.BODY_CEILING`, so the
guards `nothing lands in the dead zone` and `hits still stand clear of the
texture underneath them` are asserted against a ceiling of 150 — and a ceiling of
150 makes 151-184 unreachable by construction. Neither test can fail.

The app does not use that ceiling. It passes `ceilingFor(punch, distance)`, which
at Punch 208 runs from 208 at distance 0 down to 131 at distance 99. This measures
the real track at each setting, so the size of the gap is a number rather than a
worry.
"""
import os
import pickle
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import repro  # noqa: E402

# Same working directory as the audio and the prefs, so the cached levels are
# shared with repro.py and sweep.py rather than recomputed.
WORKDIR = os.environ.get("THRUM_MEASURE_DIR") or HERE

with open(os.path.join(WORKDIR, "levels.pkl"), "rb") as fh:
    onsets, detail = pickle.load(fh)

armed, name = repro.stored()
text = open(repro.XML, encoding="utf-8").read()
vals = dict(re.findall(r'<(?:int|boolean) name="([a-z_]+)" value="([^"]*)"', text))
punch, distance = int(vals["punch"]), int(vals["distance"])

BODY_CEILING = 150
MIN_FELT = 185

print(f"punch={punch}, the phone's distance={distance}")
print(f"dead zone = {BODY_CEILING + 1}..{MIN_FELT - 1}\n")
print(f"{'dist':>5} {'ceiling':>8} {'dead':>6} {'>=MIN_FELT':>11} "
      f"{'max detail':>11}  what the guards would see")
print("-" * 76)

for d in (0, 10, 25, 40, 50, 61, 70, 75, 85, 95, 99):
    ceiling = repro.ceiling_for(punch, d)
    amps = repro.to_score(onsets, detail, name, min_felt=punch, body_ms=400,
                          body_ceiling=ceiling)
    dead = sum(1 for a in amps if BODY_CEILING < a < MIN_FELT)
    at_beat = sum(1 for a in amps if a >= MIN_FELT)
    detail_only = [a for a in amps if 0 < a < MIN_FELT]
    note = ""
    if dead:
        note = f"dead-zone guard would FAIL ({dead} steps)"
    elif ceiling > BODY_CEILING:
        note = "guard passes, but only because nothing reached the ceiling"
    else:
        note = "guard cannot fail at this ceiling"
    print(f"{d:5d} {ceiling:8d} {dead:6d} {at_beat:11d} "
          f"{(max(detail_only) if detail_only else 0):11d}  {note}")

print()
print("At the phone's own setting the dead zone is entered "
      f"{sum(1 for a in repro.to_score(onsets, detail, name, min_felt=punch, body_ms=400, body_ceiling=repro.ceiling_for(punch, distance)) if BODY_CEILING < a < MIN_FELT)}"
      " times in 2250 steps.")
