#!/usr/bin/env python3
"""Check Thrum's contrast, including through the glass.

Every colour pair in `Theme.kt` was computed rather than eyeballed, and the glass
work in `docs/design/figma-handoff.md` extends that promise: text sits on a
*translucent* panel, so its contrast depends on whatever the score backdrop is
doing underneath at that moment. This script is how that claim is checked.

Run it with any Python 3:

    python tools/contrast.py

It prints three things:

1. The pairs already documented in `Theme.kt`, as a regression check. If one of
   these numbers moves, a colour was changed and the comment beside it is now a
   lie.
2. The glass composites across a whole track — quiet, building, and the loudest
   moment — with the ink and muted-ink ratios on each.
3. The ribbon card at its own, much lower fill, checked against the yellow bars
   rather than against text.

The arithmetic is WCAG 2.x relative luminance, with source-over compositing done
in sRGB space, which is what Android's alpha blending actually does. It is not
linear-light compositing, and that difference matters: getting it wrong makes
translucent panels look safer than they are.
"""

from __future__ import annotations

# --- The palette, from Theme.kt -------------------------------------------

FIELD = "#1C1C1A"
FIELD2 = "#242422"
SURFACE = "#2B2B28"
INK = "#EFEFEA"
INK2 = "#A8A8A1"
RULE = "#3A3A36"
ACCENT = "#D8C513"
WARN = "#E0691C"
SUPPORT = "#59A1D4"

# --- The glass, from docs/design/figma-handoff.md §4.2 --------------------

TEXT_PANEL_FILL = 0.82  # any panel carrying text
CARD_FILL = 0.30        # the ribbon card, which carries no text

# --- The score backdrop, from §4.2 ---------------------------------------
# Capped at the third value: the backdrop must never be brighter than this.

BACKDROPS = [
    ("quiet", "#141410"),
    ("building", "#2C2510"),
    ("the drop", "#54470C"),
]


def _to_linear(channel: int) -> float:
    c = channel / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _parse(hex_colour: str) -> list[int]:
    h = hex_colour.lstrip("#")
    if len(h) != 6:
        raise ValueError(f"expected #RRGGBB, got {hex_colour!r}")
    return [int(h[i:i + 2], 16) for i in (0, 2, 4)]


def luminance(hex_colour: str) -> float:
    r, g, b = _parse(hex_colour)
    return (0.2126 * _to_linear(r)
            + 0.7152 * _to_linear(g)
            + 0.0722 * _to_linear(b))


def contrast(a: str, b: str) -> float:
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def composite(behind: str, fill: str, alpha: float) -> str:
    """`fill` at `alpha` painted over `behind`, source-over, in sRGB space."""
    b, f = _parse(behind), _parse(fill)
    out = [round(alpha * f[i] + (1 - alpha) * b[i]) for i in range(3)]
    return "#%02X%02X%02X" % tuple(out)


def _verdict(ratio: float, large: bool = False) -> str:
    """WCAG AA: 4.5:1 for normal text, 3:1 for large or for graphics."""
    floor = 3.0 if large else 4.5
    return "pass" if ratio >= floor else "FAIL"


def main() -> int:
    failures = 0

    print("Documented pairs — a regression check on Theme.kt")
    print("  If one of these moves, the comment beside it is now wrong.\n")
    for name, fg, bg, expected in [
        ("ink on field", INK, FIELD, 14.80),
        ("ink2 on field", INK2, FIELD, 7.14),
        ("accent on field", ACCENT, FIELD, 9.68),
        ("warn on field", WARN, FIELD, 5.04),
        ("support on field", SUPPORT, FIELD, 6.07),
    ]:
        got = contrast(fg, bg)
        ok = abs(got - expected) < 0.02
        if not ok:
            failures += 1
        print("  %-18s %5.2f:1   (documented %.2f)  %s"
              % (name, got, expected, "ok" if ok else "CHANGED"))

    print("\nText on glass, across a whole track")
    print("  fill = field at %.0f%%, so the composite stays inside the band.\n"
          % (TEXT_PANEL_FILL * 100))
    print("  %-10s %-9s %-9s %-6s %-10s %s"
          % ("moment", "backdrop", "composite", "L", "ink", "muted ink"))
    for name, bg in BACKDROPS:
        c = composite(bg, FIELD, TEXT_PANEL_FILL)
        r_ink, r_ink2 = contrast(INK, c), contrast(INK2, c)
        if r_ink2 < 4.5:
            failures += 1
        print("  %-10s %-9s %-9s %-6.4f %5.1f:1 %-10s %5.1f:1 %s"
              % (name, bg, c, luminance(c),
                 r_ink, _verdict(r_ink), r_ink2, _verdict(r_ink2)))

    print("\nThe ribbon card, at %.0f%% — checked against the bars, not text"
          % (CARD_FILL * 100))
    print("  Bars are graphics, so the floor is 3:1, not 4.5:1.\n")
    for name, bg in BACKDROPS:
        c = composite(bg, FIELD, CARD_FILL)
        r = contrast(ACCENT, c)
        if r < 3.0:
            failures += 1
        print("  %-10s %-9s -> %-9s yellow bars %5.1f:1  %s"
              % (name, bg, c, r, _verdict(r, large=True)))

    print("\nText on the bare backdrop — with no panel at all")
    print("  Informational, and NOT counted as a failure: this is the whole")
    print("  reason the panels exist. Text on the raw backdrop fails at the")
    print("  loud end of a track, so a panel is not decoration — it is what")
    print("  makes the text legible over a moving background.\n")
    for name, bg in BACKDROPS:
        r = contrast(INK2, bg)
        print("  %-10s muted ink %5.2f:1  %s" % (name, r, _verdict(r)))

    print()
    if failures:
        print("%d check(s) FAILED" % failures)
        return 1
    print("All checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
