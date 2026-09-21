"""Which Body was the armed score actually built with?

The pref can say one thing and the score can be another — that is exactly the
bug this found on 2026-09-21, where `body=400` was stored against a score that
reproduced at `body=100`. This sweeps every Body the dial can reach and reports,
for each, how closely the rebuild matches the score armed on the phone, so the
answer is the setting that reproduces it rather than the setting the screen
claims. The best-matching row is the one the phone is really playing.

Run `repro.py` first if `levels.pkl` is not there; this caches it.
"""
import os
import pickle
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import repro  # noqa: E402

CACHE = os.path.join(os.environ.get("THRUM_MEASURE_DIR") or HERE, "levels.pkl")

if os.path.exists(CACHE):
    with open(CACHE, "rb") as fh:
        onsets, detail = pickle.load(fh)
    print("levels loaded from cache")
else:
    print("analysing (one time, ~15 s)…")
    mono = repro.decode_mono()
    o, d = repro.analyse(mono)
    onsets = repro.normalise(o)
    detail = repro.normalise(d)
    with open(CACHE, "wb") as fh:
        pickle.dump((onsets, detail), fh)

armed, name = repro.stored()
text = open(repro.XML, encoding="utf-8").read()
vals = dict(re.findall(r'<(?:int|boolean) name="([a-z_]+)" value="([^"]*)"', text))
print(f"stored settings: punch={vals['punch']} distance={vals['distance']} body={vals['body']}")
print(f"armed score: {len(armed)} steps, {name}")
print()

print(f"{'body':>6} {'identical':>10} {'zero-pattern':>13} {'duty':>7} {'mean-on':>8} {'drive':>7}")
print("-" * 58)

best = None
for body in range(100, 401, 10):
    amps = repro.to_score(onsets, detail, name, min_felt=int(vals["punch"]),
                          body_ms=body,
                          body_ceiling=repro.ceiling_for(int(vals["punch"]), int(vals["distance"])))
    n = min(len(amps), len(armed))
    same = sum(1 for i in range(n) if amps[i] == armed[i]) / n
    zero_ok = sum(1 for i in range(n) if (amps[i] == 0) == (armed[i] == 0)) / n
    driving = [v for v in amps if v > 0]
    duty = len(driving) / len(amps)
    mean_on = sum(driving) / len(driving)
    drive = duty * mean_on / 255
    mark = ""
    if best is None or same > best[1]:
        best = (body, same)
    print(f"{body:6d} {same * 100:9.2f}% {zero_ok * 100:12.2f}% {duty * 100:6.1f}% "
          f"{mean_on:8.1f} {drive:7.3f}{mark}")

print()
print(f"best match: body = {best[0]} ms  ({best[1] * 100:.2f} % of steps identical)")

# The armed score's own aggregate, for comparison with the row above.
driving = [v for v in armed if v > 0]
duty = len(driving) / len(armed)
mean_on = sum(driving) / len(driving)
print(f"armed score itself: duty {duty * 100:.1f} %  mean-on {mean_on:.1f}  "
      f"drive {duty * mean_on / 255:.3f}")

print()
print("--- events recorded by the app ---")
ev = re.search(r'<string name="events">(.*?)</string>', text, re.S)
print(ev.group(1) if ev else "(none)")
