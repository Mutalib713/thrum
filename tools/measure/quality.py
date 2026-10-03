"""Does a low-quality copy of a song give a worse haptic? Measured, not guessed.

Written 2026-10-03 to settle whether Thrum needs AI audio "enhancement" before it
makes a haptic. It runs this folder's analyser transcription (`repro.py`, which
matched the phone's own score at 99.82 %) on the original track and on 64 kbps
and 32 kbps copies of it, then compares the scores step by step.

Needs `ffmpeg` on PATH and `numpy`, and `track.mp3` in this folder (or in
`THRUM_MEASURE_DIR`). The low-quality copies are made next to it if missing.

    python quality.py

Result on AIZO (256 kbps, 2:57): the 64 kbps copy kept 92.1 % of the beats within
20 ms and 96.5 % of steps within ±10 of the original strength; the 32 kbps copy
kept 88.3 % and 94.7 %. Hit counts and stillness barely moved. So "Clean up the
sound" (PROFILE.md §4, item 16) is a listening feature, and the haptic is made
from the original file.
"""
import os
import subprocess

import repro as r

r.RINGTONE_SECONDS = 10_000          # the whole song, as the Music player plays it
PUNCH, BODY, DISTANCE = 210, 102, 61  # the settings in Mutalib's 3 Oct screenshot


def score(path):
    r.TRACK = path
    mono = r.decode_mono()
    on_raw, de_raw = r.analyse(mono)
    on, de = r.normalise(on_raw), r.normalise(de_raw)
    return r.to_score(on, de, "x", min_felt=PUNCH, body_ms=BODY,
                      body_ceiling=r.ceiling_for(PUNCH, DISTANCE))


def onsets(a):
    return [i for i, v in enumerate(a) if v > 0 and (i == 0 or a[i - 1] == 0)]


def still(a):
    return sum(v == 0 for v in a) / len(a) * 100


def main():
    ref = score(r.TRACK)
    ref_on = onsets(ref)
    print(f"original      : {len(ref)} steps, {len(ref_on)} hits, still {still(ref):.1f}%")
    for kbps in (64, 32):
        copy = os.path.join(r.WORKDIR, f"track-{kbps}k.mp3")
        if not os.path.exists(copy):
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", os.path.join(r.WORKDIR, "track.mp3"),
                            "-b:a", f"{kbps}k", copy], check=True)
        a = score(copy)
        n = min(len(a), len(ref))
        near = sum(abs(a[i] - ref[i]) <= 10 for i in range(n)) / n * 100
        found = set(onsets(a))
        matched = sum(any(o + d in found for d in (-1, 0, 1)) for o in ref_on) / len(ref_on) * 100
        print(f"{kbps:3d} kbps copy : {len(a)} steps, {len(found)} hits, still {still(a):.1f}% | "
              f"steps within ±10 strength {near:.1f}% | beats within 20 ms of the original's {matched:.1f}%")


if __name__ == "__main__":
    main()
