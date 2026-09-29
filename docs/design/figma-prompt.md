# Thrum — Figma prompt

Copy the block in §1 into **Figma Make** (or hand it to a designer as a brief). §2 is
a shorter version if the tool caps prompt length. §3 is how to iterate once the first
frame comes back.

Full context, copy deck, and component specs: `figma-handoff.md`.

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

THE BACKDROP — this is the idea the whole design rests on
The background is not a photo and not a gradient. It is generated from the song's own
rhythm: a row of very wide vertical bars, one per beat, bar height = how hard that
beat lands, blurred hard (24 dp) so it reads as a soft colour field rather than a
chart. Where the music is quiet it is near-black #141410; as the track builds it warms
through #2C2510; at the loudest moment it reaches #54470C and never brighter. The
glass panels sit on top of this and visibly pick up its colour. The backdrop must
never be bright enough to compete with text.

GLASS RECIPE — apply to every panel, card, and button
  - fill: #1C1C1A at 82% opacity
  - rim: a 1 px hairline outline in white at 16%
  - top edge: a 2 dp line in white at 26% along the inside top of the panel — this is
    what reads as "a pane of something" rather than "a grey rectangle"
  - corner radius: 16 dp
  - no drop shadows. Depth comes from the rim and the backdrop, not from shadow.
Do NOT make the fill more transparent to look more like glass. Text sits on the
glass, and the fill is set so text keeps its contrast ratio. It is deliberately
fairly opaque.

LAYOUT, top to bottom, one scrolling column, 24 dp side margins, 24 dp between blocks

1. Masthead. "Thrum" at 39 sp, bold, in near-white #EFEFEA. Under it, one line:
   "Vibrate mode, playing your own music" at 14 sp in #A8A8A1. A 12 dp filled circle
   in yellow #D8C513 at the far right, aligned with the wordmark.

2. A glass card containing the pulse ribbon. This is the signature element and the
   most important thing on the screen.
   - A row of vertical bars, 3 dp wide, 1 dp apart, 96 dp tall, on a dark inset
     surface. Bar height = that step's strength, so the row shows the rhythm's shape.
   - Bar colour: yellow #D8C513. Silent steps are a 1 dp stub sitting on the
     baseline, not a gap.
   - CORNERS ARE SQUARE. 0 radius. A rounded bar reads as a decorative chart; this is
     a machine part shown at its real shape.
   - One 1 dp vertical playhead line sweeps left to right across the bars while it
     plays.
   - The bars are NOT tinted, NOT blurred, NOT glazed. Flat full-contrast yellow. The
     glass card is the frame around them, never over them.

3. The track name at 20 sp, up to two lines then ellipsis. Real example, use it:
   "Asake, Travis Scott - Active (Official Video)"

4. "0:45 · 79 hits" at 14 sp in #A8A8A1.

5. "Ready" at 25 sp in yellow #D8C513. This is a status verdict, not a button.
   Under it at 16 sp in #EFEFEA: "Your phone is on vibrate, so next time it rings,
   this is what you feel."

6. A glass row, full width: on the left "Also when the ringer is on" at 16 sp, with a
   smaller muted line under it, "Your phone plays its own ringtone and Thrum vibrates
   your track over it. Unless they are the same file, you hear one song and feel
   another." On the right, a switch, switched ON, in yellow.

7. Two glass pill buttons side by side, equal width, 48 dp tall:
   "Play it with the song" and "Feel it on its own".

8. A full-width button: "Play the phone's own buzz". Style it TINTED, not filled —
   fill is yellow at about 12%, rim is yellow at about 40%, label in yellow. It must
   not compete with the primary action. Under it, muted 12 sp:
   "The vibration Android uses for a call on this phone: one second at full strength,
   then one second of silence. Play it, then play yours, and compare the two."

9. A plain text button: "Choose a different song".

10. A muted 12 sp technical row: "2250 steps · 20 ms each · still 10% of the time".

11. Section heading "Tune the feel" at 16 sp. Then three sliders. Each slider has a
    16 sp label, a 12 sp muted help line, then the control. THE SLIDER TRACK IS
    RECESSED — an inset groove, not a raised bar. The thumb is a small glass bead with
    a yellow rim.
      "Punch 210" / "How hard a beat lands. It also sets how far Detail can go, so
      raise this first if Detail runs out of room."
      "Beat length 400 ms" / "How long each beat is driven for. This is what decides
      whether a phone lying on a table moves: a longer drive pushes the table, a
      shorter one you only feel in your hand."
      "Distance from the music 61" / "0 is as close as it gets: the snare and hats
      come through alongside the kick, so you feel the whole pattern."
    Under them, muted 12 sp: "Changes apply straight away. Play it to feel them."

12. Section heading "How fast can this phone tap?" at 16 sp, a muted help line, then a
    full-width glass pill: "Run the tap test".

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

ALSO PRODUCE THESE STATES as separate frames, same width
  - Empty: "Pick the song you want to feel" at 25 sp, a sentence under it, and one
    full-width filled yellow button "Choose a song". The ribbon is a flat line of
    1 dp stubs — that flat line is exactly what Android gives the user today.
  - Permission: "Thrum has to see your call notifications" at 25 sp, one paragraph,
    one filled button "Turn on notification access".
  - Blocked: "This phone can't do it" at 25 sp in orange #E0691C, an explanatory
    paragraph, and NO button at all. This state is terminal and honest.
  - Playing: same as the main frame, but the two pill buttons are replaced by a single
    full-width glass pill reading "Stop".

DO NOT
  - No purple or indigo anywhere. This is the single most important prohibition.
  - No gradients, no mesh backgrounds, no noise textures, no glow, no neon.
  - No emoji. No stock photography. No illustration. No icon set.
  - No shadows stacked for elevation.
  - No "Get Started". No three-feature-card row. There is one feature.
  - No centred hero with two buttons.
  - No rounded ribbon bars.
  - No second filled button. Exactly one filled button per screen; everything else is
    glass or tinted glass.

Deliver the main armed frame plus the four states above.
```

---

## 2. Short version

If the tool caps prompt length, this keeps the two things that actually matter — the
backdrop idea and the glass numbers:

```
Design a single-screen Android app called Thrum in a liquid glass style. Dark theme.
Frame 411 × 891 dp, edge to edge, no navigation, no top app bar. One scrolling column,
24 dp margins, 24 dp between blocks.

Thrum turns a song on your phone into the vibration you feel when someone calls in
vibrate mode. It is an instrument panel, not a landing page — no hero, no feature
cards. Tone: precision audio equipment. Dark, unhurried, no decoration.

The backdrop is generated from the song: a row of very wide vertical bars, one per
beat, blurred 24 dp into a soft colour field. Near-black #141410 when quiet, warming
to #2C2510 as it builds, peaking at #54470C. The glass picks this colour up.

Glass recipe for every panel and button: fill #1C1C1A at 82%, a 1 px rim in white at
16%, and a 2 dp top edge in white at 26% along the inside top. Radius 16 dp. No drop
shadows. Do not make the fill more transparent — text sits on it and keeps its
contrast because of that opacity.

Colours: field #1C1C1A, ink #EFEFEA, muted #A8A8A1, accent yellow #D8C513, warn
#E0691C. The world is wet concrete in a stairwell with a sulphur-yellow safety line.

Top to bottom:
1. "Thrum" at 39 sp bold, a 14 sp muted tagline under it, a 12 dp yellow dot at right.
2. A glass card holding the pulse ribbon: vertical bars 3 dp wide, 1 dp apart, 96 dp
   tall, SQUARE corners (0 radius), yellow, one 1 dp playhead line sweeping across.
   The bars are flat and full-contrast — the glass frames them, never covers them.
3. Track name at 20 sp, two lines then ellipsis: "Asake, Travis Scott - Active".
4. "0:45 · 79 hits" at 14 sp muted.
5. "Ready" at 25 sp in yellow. Under it: "Your phone is on vibrate, so next time it
   rings, this is what you feel."
6. A glass row: "Also when the ringer is on" with a muted sub-line and a switch ON.
7. Two equal glass pill buttons: "Play it with the song" / "Feel it on its own".
8. A full-width TINTED button (yellow 12% fill, yellow 40% rim, yellow label):
   "Play the phone's own buzz", with a muted 12 sp help line under it.
9. A text button: "Choose a different song".
10. Muted 12 sp: "2250 steps · 20 ms each · still 10% of the time".
11. "Tune the feel" at 16 sp, then three sliders with RECESSED tracks (inset grooves)
    and small glass-bead thumbs: "Punch 210", "Beat length 400 ms",
    "Distance from the music 61", each with a 12 sp muted help line.
12. "How fast can this phone tap?" at 16 sp and a glass pill "Run the tap test".

Type: Space Grotesk for headings, IBM Plex Sans for body. Scale 12/14/16/20/25/31/39.

No purple or indigo anywhere. No gradients, no glow, no emoji, no stock imagery, no
icon set, no drop shadows, no rounded ribbon bars. Exactly one filled button on the
screen.
```

---

## 3. Iterating after the first frame

Figma Make will get the composition right and the glass wrong, in a predictable way:
it will make the panels too transparent, because that is what "glass" looks like in
every reference it has seen. **This is the correction that will be needed:**

> The panels are too transparent. The fill must be #1C1C1A at 82% opacity — quite
> opaque — because text sits on them and has to keep a 13.5:1 contrast ratio. The
> glass should read from the rim, the bright top edge, and the colour of the blurred
> backdrop in the gaps *between* panels, not from seeing through the panel. Make the
> top edge brighter and the panels more opaque.

Other things worth checking on the first pass:

- **Ribbon bars.** If they came back rounded or given a shadow, that is wrong. Square,
  flat, full-contrast yellow.
- **Filled buttons.** If there is more than one, the hierarchy is broken. The compare
  button is tinted, not filled.
- **Slider tracks.** They should be recessed grooves, not raised bars.
- **The backdrop.** If it came back as a gradient or a photo, that is wrong — it is
  generated from the song's rhythm and must look like a blurred bar chart.
- **The yellow.** It should be `#D8C513`, a slightly olive safety yellow, not a lemon
  or a gold.
