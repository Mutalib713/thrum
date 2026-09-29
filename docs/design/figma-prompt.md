# Thrum — Figma prompt

Copy the block in §1 into **Figma Make** (or hand it to a designer as a brief). §2 is
a shorter version if the tool caps prompt length. §3 is how to iterate once the first
frame comes back.

Full context, copy deck, and component specs: `figma-handoff.md`.

> **Revised 2026-09-29.** The first version of this prompt described the screen as it
> shipped — which was a test bench: preview, feel, compare, three dials, a tap-rate
> test and a technical row, all stacked down one column. That screen is wrong. The
> prompt now describes the **production** screen, and the test bench has been moved
> to a Tune sheet and to diagnostics.

---

## 1. The prompt

```
Design a single-screen Android app called Thrum, in a liquid glass style. Dark theme.
Frame: 411 × 891 dp, edge to edge, no top app bar, no navigation. Design at 1x.

WHAT IT IS
Thrum turns a song already on your phone into the vibration you feel when someone
calls, in vibrate mode. The user opens it once, picks a track, arms it, and never
opens it again. So this is an instrument panel, not a landing page: no hero, no
feature cards, no onboarding, no persuasion. The tone is a well-made piece of audio
equipment — dark, precise, unhurried, confident enough to state a number without
decorating it. Never playful, never "music app fun".

THE SCREEN IS SHORT. Six blocks, and that is the whole thing. An earlier version of
this app put a tuning bench on the product screen — three sliders, a tap-rate test, a
comparison button, a technical readout — and it read as a diagnostics panel because
it was one. All of that is gone from this screen. Do not add controls. The restraint
is the design.

THE BACKDROP — this is the idea the whole design rests on
The background is not a photo and not a gradient. It is generated from the song's own
rhythm: a row of very wide vertical bars, one per beat, bar height = how hard that
beat lands, blurred hard (24 dp) so it reads as a soft colour field rather than a
chart. Where the music is quiet it is near-black #141410; as the track builds it warms
through #2C2510; at the loudest moment it reaches #54470C and never brighter. The
glass panels sit on top of it and visibly pick up its colour. The backdrop must never
be brighter than that, because text sits over it.

GLASS RECIPE — apply to every panel and button
  - fill: #1C1C1A at 82% opacity
  - rim: a 1 px hairline outline in white at 16%
  - top edge: a 2 dp line in white at 26% along the inside top of the panel — this is
    what reads as "a pane of something" rather than "a grey rectangle"
  - corner radius: 16 dp
  - no drop shadows. Depth comes from the rim and the backdrop, not from shadow.
Do NOT make the fill more transparent to look more like glass. Text sits on these
panels and the opacity is what keeps it legible. Where the glass gets to be genuinely
see-through is the one card with no text on it — see below.

LAYOUT, top to bottom, single scrolling column, 24 dp side margins, 24 dp between
blocks. THIS IS THE ARMED STATE — the app is set up and waiting for a call.

1. Masthead. "Thrum" at 39 sp, bold, in near-white #EFEFEA. Under it, one line:
   "Vibrate mode, playing your own music" at 14 sp in #A8A8A1. A 12 dp filled circle
   in yellow #D8C513 at the far right, aligned with the wordmark.

2. A glass card containing the pulse ribbon. This is the signature element and the
   most important thing on the screen, and it is the ONE place the glass is allowed
   to be genuinely transparent.
   - Use the fill at 30% opacity instead of 82%, because nothing but the ribbon sits
     on it. The blurred colour field must be clearly visible through this card.
   - Inside it: a row of vertical bars, 3 dp wide, 1 dp apart, 150 dp tall. Bar
     height = that step's strength, so the row shows the rhythm's shape.
   - Bar colour: yellow #D8C513. Silent steps are a 1 dp stub sitting on the
     baseline, not a gap.
   - CORNERS ARE SQUARE. 0 radius. A rounded bar reads as a decorative chart; this is
     a machine part shown at its real shape.
   - One 1 dp vertical playhead line sweeps left to right across the bars while it
     plays.
   - The bars themselves are NOT tinted, NOT blurred, NOT glazed. Flat full-contrast
     yellow. The glass card is the frame around them, never over them.

3. The track name at 20 sp, up to two lines then ellipsis. Real example, use it:
   "Asake, Travis Scott - Active"

4. "0:45 · 79 hits" at 14 sp in #A8A8A1.

5. "Ready" at 25 sp in yellow #D8C513. This is a status verdict, not a button.
   Under it at 16 sp in #EFEFEA: "Your phone is on vibrate, so next time it rings,
   this is what you feel."

6. A glass row, full width, at 82%: on the left "Also when the ringer is on" at 16 sp,
   with a smaller muted line under it, "Your ringtone and your track". On the right, a
   switch, switched ON, in yellow.

7. Two quiet text rows, in muted grey #A8A8A1, not buttons:
   "Tune it" with a small chevron at the right edge, and under it
   "Choose a different song".

THERE IS NO FILLED BUTTON ON THIS SCREEN. Once the app is armed there is genuinely
nothing to do, and a calm screen is the honest one — the product's promise is that you
open it once and never again. The single filled button appears only before arming, and
it reads "Use this when someone calls".

COLOUR — use these exact values
  field #1C1C1A · raised #242422 · surface #2B2B28
  ink #EFEFEA (text) · ink2 #A8A8A1 (muted text) · rule #3A3A36 (hairlines)
  accent #D8C513 (the yellow) · warn #E0691C · support #59A1D4
The world is wet board-marked concrete in a stairwell with a sulphur-yellow safety
line painted across it. That is why the yellow is the only saturated colour.

TYPE
  Space Grotesk for the wordmark, the track name, and state headlines.
  IBM Plex Sans for body text, labels, and help lines.
  Scale: 12 / 14 / 16 / 20 / 25 / 31 / 39. Body never below 16.

ALSO PRODUCE THESE as separate frames, same width

  EMPTY — the real first impression, and only four blocks:
    "Thrum" and the tagline; the ribbon card showing a FLAT LINE of 1 dp stubs, which
    is literally what Android vibrates today; "Pick the song you want to feel" at
    25 sp; the sentence "Any track on your phone. Thrum follows the bass and drums,
    and makes your phone vibrate to them when someone calls."; and one full-width
    filled yellow button, "Choose a song". The flat line is doing the selling.

  PERMISSION — "Thrum has to see your call notifications" at 25 sp, the paragraph
    "It is how the app knows a call has started. Thrum checks one thing: is this a
    call? The rest it ignores. Nothing leaves your phone. There is no internet
    permission in this app and there never will be.", and one filled button "Turn on
    notification access".

  BLOCKED — "This phone can't do it" at 25 sp in orange #E0691C, the paragraph
    "Thrum needs a motor that can change strength. Yours has one speed. Every rhythm
    would reach you as the same flat buzz your phone already makes, and no setting,
    update, or future version of this app will change that.", and NO BUTTON AT ALL.
    This state is terminal and honest. Do not add a "try anyway".

  PLAYING — the same as the armed frame, with the ribbon's playhead mid-travel and
    nothing else changed. No new controls appear.

  THE TUNE SHEET — a bottom sheet, half height, glass, that slides up over the armed
    screen. This is where the tuning bench went, and it is reachable only by tapping
    "Tune it". It contains three sliders, each with a 16 sp label and a 12 sp muted
    help line under it, and the slider track is a RECESSED inset groove, not a raised
    bar, with a small glass-bead thumb with a yellow rim:
      "Punch 210" / "How hard a beat lands. It also sets how far Detail can go, so
      raise this first if Detail runs out of room."
      "Beat length 400 ms" / "How long each beat is driven for. This is what decides
      whether a phone lying on a table moves: a longer drive pushes the table, a
      shorter one you only feel in your hand."
      "Distance from the music 61" / "0 is as close as it gets: the snare and hats
      come through alongside the kick, so you feel the whole pattern."
    At the top of the sheet, two glass pills side by side: "Play it with the song"
    and "Feel it on its own". At the bottom, muted 12 sp: "Changes apply straight
    away. Play it to feel them."

DO NOT
  - No purple or indigo anywhere. This is the single most important prohibition.
  - No gradients, no mesh backgrounds, no noise textures, no glow, no neon.
  - No emoji. No stock photography. No illustration. No icon set.
  - No shadows stacked for elevation.
  - No "Get Started". No three-feature-card row. There is one feature.
  - No centred hero with two buttons.
  - No rounded ribbon bars.
  - No tuning sliders, no tap-rate test, no comparison button and no technical
    readout on the main screen. Those belong to the Tune sheet and to diagnostics.
  - No filled button on the armed screen.

Deliver the armed frame first, then Empty, Permission, Blocked, Playing, and the
Tune sheet.
```

---

## 2. Short version

If the tool caps prompt length, this keeps the things that actually matter — the
short screen, the backdrop idea, and the glass numbers:

```
Design a single-screen Android app called Thrum in a liquid glass style. Dark theme.
Frame 411 × 891 dp, edge to edge, no navigation, no top app bar. One scrolling column,
24 dp margins, 24 dp between blocks.

Thrum turns a song on your phone into the vibration you feel when someone calls in
vibrate mode. It is an instrument panel, not a landing page — no hero, no feature
cards. Tone: precision audio equipment. Dark, unhurried, no decoration.

KEEP THE SCREEN SHORT — six blocks. Do not add controls. An earlier version stacked a
tuning bench on it (three sliders, a tap-rate test, a comparison button, a technical
readout) and it read as a diagnostics panel. All of that is gone.

The backdrop is generated from the song: a row of very wide vertical bars, one per
beat, blurred 24 dp into a soft colour field. Near-black #141410 when quiet, warming
to #2C2510 as it builds, peaking at #54470C and never brighter. The glass picks this
colour up.

Glass recipe for every panel: fill #1C1C1A at 82%, a 1 px rim in white at 16%, and a
2 dp top edge in white at 26% along the inside top. Radius 16 dp. No drop shadows. Do
not make the fill more transparent — text sits on it and keeps its contrast because of
that opacity. The ONE exception is the ribbon card below, which carries no text and
drops to 30% so the colour field shows clearly through it.

Colours: field #1C1C1A, ink #EFEFEA, muted #A8A8A1, accent yellow #D8C513, warn
#E0691C. The world is wet concrete in a stairwell with a sulphur-yellow safety line.

The ARMED state, top to bottom:
1. "Thrum" at 39 sp bold, a 14 sp muted tagline under it, a 12 dp yellow dot at right.
2. A glass card at 30% opacity holding the pulse ribbon: vertical bars 3 dp wide, 1 dp
   apart, 150 dp tall, SQUARE corners (0 radius), yellow, one 1 dp playhead line
   sweeping across. The blurred field must be clearly visible through this card. The
   bars are flat and full-contrast — the glass frames them, never covers them.
3. Track name at 20 sp, two lines then ellipsis: "Asake, Travis Scott - Active".
4. "0:45 · 79 hits" at 14 sp muted.
5. "Ready" at 25 sp in yellow. Under it: "Your phone is on vibrate, so next time it
   rings, this is what you feel."
6. A glass row at 82%: "Also when the ringer is on" with a muted sub-line "Your
   ringtone and your track", and a switch ON at the right.
7. Two quiet muted text rows: "Tune it" with a chevron at the right edge, and
   "Choose a different song".

NO FILLED BUTTON on this screen — once armed there is nothing to do. The filled button
appears only before arming: "Use this when someone calls".

Type: Space Grotesk for headings, IBM Plex Sans for body. Scale 12/14/16/20/25/31/39.

Also produce: an Empty state (four blocks, ribbon showing a flat line of 1 dp stubs,
one filled button "Choose a song"), a Permission state, a Blocked state in orange with
NO button at all, and a half-height glass Tune sheet containing three sliders with
RECESSED tracks, plus two pills "Play it with the song" and "Feel it on its own".

No purple or indigo anywhere. No gradients, no glow, no emoji, no stock imagery, no
icon set, no drop shadows, no rounded ribbon bars. No tuning controls on the main
screen. No filled button on the armed screen.
```

---

## 3. Iterating after the first frame

Figma Make will get the composition right and the glass wrong, in two predictable
ways.

**One: it will make the panels too transparent**, because that is what "glass" looks
like in every reference it has seen.

> The text panels are too transparent. They must be #1C1C1A at 82% opacity — quite
> opaque — because text sits on them and has to keep a 13.5:1 contrast ratio. The
> glass should read from the rim, the bright top edge, and the colour of the blurred
> backdrop in the gaps *between* panels, not from seeing through the panel. Make the
> top edge brighter and the panels more opaque.

**Two: it will not make the ribbon card transparent enough**, which is the opposite
error and the one that costs the design its best moment.

> The ribbon card is the exception and it is currently too solid. It carries no text,
> only the yellow bars, so it can drop to 30% opacity. The blurred colour field should
> be clearly visible through it. This card is where the liquid glass actually reads.

Other things worth checking on the first pass:

- **Ribbon bars.** If they came back rounded or given a shadow, that is wrong. Square,
  flat, full-contrast yellow.
- **Filled buttons.** If there is one on the armed screen, remove it. There is nothing
  to do once the app is armed.
- **Extra controls.** If it added sliders, toggles or a technical readout to the main
  screen, delete them — they belong to the Tune sheet. The restraint is the design.
- **Slider tracks** in the Tune sheet should be recessed grooves, not raised bars.
- **The backdrop.** If it came back as a gradient or a photo, that is wrong — it is
  generated from the song's rhythm and must look like a blurred bar chart.
- **The yellow.** It should be `#D8C513`, a slightly olive safety yellow, not a lemon
  or a gold.
