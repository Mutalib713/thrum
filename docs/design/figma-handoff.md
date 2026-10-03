# Thrum — Figma handoff

> **Superseded for layout on 2026-10-03** by the full-app screens in `docs/design/screens/`. The liquid-glass recipe in §4 still stands for when glass is discussed.

Everything needed to redraw Thrum's one screen in Figma. Written 2026-09-29, for a
**liquid glass** pass.

The prompt to paste into Figma Make is in `figma-prompt.md`, beside this file.

---

## 1. What this is, in one paragraph

Thrum turns a song already on your phone into the vibration you feel when someone
calls, in vibrate mode. Android has exactly one vibrate pattern and it is the same
for everybody; Thrum analyses a track you choose, works out where the beats and the
weight are, and drives the motor to that instead. **Nothing comes out of the
speaker.** The user opens the app **once**, picks a track, arms it, and ideally never
opens it again — so every screen is an instrument panel, not a marketing page.

That single fact drives the whole design: there is no onboarding, no hero, no
feature cards, and no reason to persuade anyone of anything after the first thirty
seconds.

---

## 2. Hard constraints — do not design these away

| Constraint | Why it is not negotiable |
|---|---|
| **One screen, seven states** | There is no navigation. No tabs, no drawer, no back stack. A wordmark is not navigation. |
| **411 dp wide reference frame** | Pixel 6 Pro logical width. This is the width the app is actually used at, and the width at which a button label was already clipped once. |
| **Edge to edge, transparent system bars** | `statusBarColor` and `navigationBarColor` are both transparent; content draws behind them and insets are padded. |
| **Dark-first** | The palette is a dark palette with a designed light counterpart. The glass work below is specified for dark. |
| **Must survive `fontScale` 1.3** | Every state. No fixed-height text containers. |
| **Offline, permanently** | The app has no `INTERNET` permission and never will. No fonts may be downloaded at runtime — both faces are bundled. Do not design anything that assumes a network. |
| **Colour is never the only signal** | The armed indicator is a dot *and* a word. The verdict is a sentence *and* a colour. |
| **Touch targets 48 dp minimum** | The visible element may be smaller than the target, never the reverse. |

---

## 3. The visual system as it already exists

### 3.1 Colour — the `sulphur-concrete` world

Wet board-marked concrete in a stairwell, with a sulphur-yellow safety line painted
across it. Chosen because the language of *machinery that moves* is exactly this
product: a motor throwing mass around inside a phone.

| Role | Hex | Contrast |
|---|---|---|
| field | `#1C1C1A` | the concrete — the base everything sits on |
| field2 | `#242422` | raised |
| surface | `#2B2B28` | cards |
| ink | `#EFEFEA` | **14.80:1** on field |
| ink2 | `#A8A8A1` | **7.14:1** on field |
| rule | `#3A3A36` | hairlines |
| **accent** | `#D8C513` | **9.68:1** on field — the safety line |
| warn | `#E0691C` | **5.04:1** on field |
| support | `#59A1D4` | **6.07:1** on field |

Light-mode counterparts exist and are designed rather than inverted: field
`#EFEFEA`, ink `#1C1C1A`, and **`#6B6109`** as the accent-as-text (the raw yellow is
1.53:1 on the light field and can never be text there).

Every pair above was computed by a palette engine, not eyeballed. If you add a
colour, compute it.

### 3.2 Type

Two bundled faces, because a single system font is how an Android app announces
that nobody chose anything.

- **Space Grotesk** — identity. Headings and the wordmark. Weights 500 and 700.
- **IBM Plex Sans** — reading. Body and labels. Weights 400, 500, 600.

Scale, ratio ~1.25: **12 · 14 · 16 · 20 · 25 · 31 · 39 sp**. Body never below 16 sp.

| Token | Face | Size / line | Weight | Used for |
|---|---|---|---|---|
| displayMedium | Space Grotesk | 39 / 44 | 700 | the wordmark |
| headlineMedium | Space Grotesk | 25 / 32 | 500 | state headlines — "Ready", "Won't work yet" |
| titleLarge | Space Grotesk | 20 / 26 | 500 | the track name |
| titleMedium | IBM Plex | 16 / 22 | 600 | section headings — "Tune the feel" |
| bodyLarge | IBM Plex | 16 / 24 | 400 | the sentences that matter |
| bodyMedium | IBM Plex | 14 / 21 | 400 | metadata |
| bodySmall | IBM Plex | 12 / 18 | 400 | help text under a control |
| labelLarge | IBM Plex | 14 / 20 | 600 | button labels |
| labelSmall | IBM Plex | 12 / 16 | 500 | the technical row |

### 3.3 Space, radius, motion

- **Space** — 4-pt base: 4 · 8 · 12 · 16 · 24 · 32 · 48 · 64.
- **Radius** — small 6 · medium 10 · large 16. **The ribbon's bars are 0.** A moving
  machine part is not rounded.
- **Motion** — 120 ms micro (a colour or value settling), 240 ms structural
  (something entering or leaving). The playhead is frame-rate, 16 ms, because it
  must not lag the vibration it marks.

---

## 4. The change being made: liquid glass

### 4.1 This overrides the brief, deliberately

`design-brief.md` Phase 2 lists, under **"What we refuse"**:

> No purple or indigo anything. No `Get Started`. No three-feature-card row — there
> is one feature. **No glass.** No emoji anywhere.

That refusal was written before the app had been seen running on a phone. It is
being overridden on 2026-09-29 at Mutalib's request. The reason it can be overridden
safely is in §4.3 — the usual argument against glass (it destroys text contrast) is
soluble here, because Thrum generates its own backdrop and can therefore *compute*
what the glass will composite to.

**This is a recorded decision, not an oversight.** If the glass pass is abandoned,
restore the line above.

### 4.2 The recipe

Four layers, front to back:

| # | Layer | Value |
|---|---|---|
| 1 | **Content** | Text and the ribbon. Never tinted, never blurred. |
| 2 | **Rim** | One hairline outline: white at **16%**. Plus a brighter **top edge**, white at **26%**, ~2 dp, along the inside top of the panel — this is what reads as "a pane of something" rather than "a grey rectangle". |
| 3 | **Fill** | `field #1C1C1A` at **82%**. This is the whole pane. |
| 4 | **Backdrop** | The score itself, drawn as very wide bars, blurred **24 dp**. The only blur on screen. |

**Blur is applied once, to the backdrop, not per panel.** Compose cannot sample what
is behind a composable, so a per-panel frosted effect would need a nested render
layer per panel — expensive on the low-end hardware this app targets. Blurring the
backdrop once and letting translucent panels sit over it gets the same read for the
cost of a single `RenderEffect`.

**Real blur is available on every device this app supports.** `minSdk = 31`, and
`Modifier.blur` is backed by `RenderEffect`, which landed in API 31 exactly. There
is no fallback path to design.

### 4.3 The one rule that keeps it honest

The existing palette's whole value is its computed contrast. Translucency is the
thing that normally destroys that, because text ends up on an unknown composite.

Thrum escapes this because **it owns the backdrop**. Two rules:

1. **The backdrop's brightness is capped.** At its loudest the backdrop reaches
   `#54470C`. It never goes brighter.
2. **The fill is a single fixed value.** `field` at 82%.

Together those bound the composite. Worked through the track:

| Moment | Backdrop | Composite | Ink | Muted ink |
|---|---|---|---|---|
| Quiet passage | `#141410` | `#1B1B18` (L 0.011) | 15.0:1 | 7.2:1 |
| Building | `#2C2510` | `#1F1E18` (L 0.013) | 14.5:1 | 7.0:1 |
| The drop | `#54470C` | `#262417` (L 0.017) | 13.5:1 | 6.5:1 |

The composite stays inside **L 0.011 – 0.017** for the entire track, so ink holds
**13.5:1 or better** and muted ink **6.5:1 or better** at every point in every song.
That is not a judgement call; it falls out of the two rules above.

**Design consequence, and it is the important one:** a panel that carries text
cannot be very transparent. The glass reads from its **rim**, its **top edge**, and
from the **backdrop showing at full strength in the gutters between panels** — not
from seeing through the panel. Do not make the fill more transparent to make it look
more like glass. That trades a guarantee for a decoration.

#### The trap, with the numbers

The first sketch of this used a **white fill at 6%** — the obvious way to draw glass,
and wrong here, because white *raises* the composite's luminance and eats contrast.
Measured at the loudest point of a track:

| Fill | Composite at the drop | Ink | Muted ink |
|---|---|---|---|
| white @ 6% | `#5E521B` (L 0.085) | 6.7:1 | **3.3:1 — fails AA** |
| **field @ 82%** | `#262417` (L 0.017) | 13.5:1 | **6.5:1 — passes** |

A light fill is the right move only when the backdrop is dark; at the drop it is the
worst possible choice. The fixed dark fill is what makes the guarantee hold for the
whole track rather than for the quiet parts of it.

For completeness, text on the **bare backdrop** — with no panel at all — also fails
at the drop, at 3.85:1. Which is the real reason the panels exist: they are not
decoration, they are what makes the text legible over a moving background.

These figures were verified numerically, not estimated. The same script reproduces
the three contrast ratios already documented in the brief (14.80 / 7.14 / 9.68)
exactly, which is how we know the method is right.

### 4.4 What is never glazed

**The pulse ribbon.** It is the one thing on screen that must be read exactly, and
it is a machine part shown at its real shape. It sits *on* a glass card; the bars
themselves are flat, full-contrast accent yellow on a dark inset. Blurring or tinting
the bars would turn the app's signature move into a decorative chart.

Also never glazed: any text, the armed dot, the slider thumbs.

### 4.5 How transparent each surface may be

The rule is one line: **only a surface with no text on it may be transparent.**

| Tier | Surface | Fill | Why |
|---|---|---|---|
| — | The ribbon bars | no panel | They are content. Flat, full contrast, never under glass. |
| A | **Any panel carrying text** | field at **82%** | The composite stays in band, so ink holds 13.5:1 and ink2 6.5:1 at every point in every track. |
| B | **The ribbon card** | field at **30%** | Nothing but the ribbon sits on it, and the bars are bright yellow with large contrast headroom. Verified: the bars still hold **6.4:1** at the loudest point of a track. |
| C | **The gutters** | no panel | The score runs at full strength between and around panels. This is where the glass gets its colour. |

Tier B is the one worth pushing, and it is why clearing the screen out makes the app
glassier rather than merely emptier: **the fewer panels that carry text, the more
surface there is that can be genuinely see-through.** Even at 20% the bars hold
6.0:1, so if the build looks better that way, it is safe.

**The liquid part is that the backdrop moves.** It is not a static image — it is the
score, so while a preview plays, the field's colour flows and the panels' tint
changes with the rhythm. That is the difference between glass and *liquid* glass, and
it costs nothing, because the data is already there.

---

## 5. Screen inventory — seven states, one screen

| State | Reached when | Headline | The one action |
|---|---|---|---|
| **Blocked** | the motor cannot vary strength | "This phone can't do it" | **none.** No "try anyway" button — there is nothing this app can offer that phone, and a button would be a lie with a tap target. |
| **Permission** | notification listener not bound | "Thrum has to see your call notifications" | "Turn on notification access" |
| **Empty** | capable, permitted, no score yet | "Pick the song you want to feel" | "Choose a song" |
| **Loading** | decoding and analysing | "Reading *<track name>*" | (spinner; takes a few seconds) |
| **Error** | decode failed, or the file is silent | "That one didn't work" | "Choose a different song" |
| **Ready** | score built, not armed | the track name, then "Use this when someone calls" | **Arm it** |
| **Armed** | score saved and live | "Ready" | "Choose a different song" |

### Sub-states inside Ready / Armed

- **Playing** — the two buttons collapse to a single full-width "Stop".
- **Stock buzz playing** — the compare button's label becomes "Stop the buzz". The
  ribbon deliberately does *not* animate, because a different vibration is playing.
- **Verdict variants** — the armed verdict is read live from the phone's ringer mode
  and has five forms, two of which add a remedy line and one of which adds a button.
  See §8.
- **Rebuild problem** — an error line appears beside the dials when the source file
  can no longer be read. The dials stay on screen; the screen is not replaced.

---

## 6. The production screen, in order

The screen that shipped was a test bench: preview, feel, compare, three dials, a
tap-rate test and a technical row, all stacked down one column. It read as a
diagnostics panel because it *was* one — every control on it existed to answer a
question during development.

The product screen keeps six things and moves the rest. Single scrolling column,
**24 dp** side margins, **24 dp** between blocks.

```
  Thrum                                            ( • )   ← armed dot, only when armed
  Vibrate mode, playing your own music

  ┌──────────────────────────────────────────────────┐   ← RIBBON CARD, 30% — see §4.5
  │  ▁▃▅█▅▃▁▃▅█▅▃▁  the pulse ribbon, 150 dp tall    │     the field shows through it
  │  3 dp bars, 1 dp apart, square corners,           │
  │  accent yellow, 1 dp playhead sweeping            │
  └──────────────────────────────────────────────────┘

  Asake, Travis Scott - Active                         ← 20 sp, 2 lines then ellipsis
  0:45 · 79 hits                                       ← 14 sp, muted

  Ready                                                ← 25 sp, accent yellow
  Your phone is on vibrate, so next time it rings,     ← 16 sp
  this is what you feel.

  Also when the ringer is on                    ( ●——)  ← GLASS ROW, 82%, switch on
  Your ringtone and your track

  Tune it                                        ›     ← quiet row, opens the Tune sheet
  Choose a different song                              ← quiet row
```

That is the whole screen. The armed state is the one the user will actually live in,
and it now has **no filled button at all** — because once the app is armed there is
genuinely nothing to do. The single filled button exists only *before* arming, as
"Use this when someone calls".

A calm screen is the honest one here. The product's promise is that you open it once
and never again, so the resting state should look settled rather than busy.

### What moved, and where

| Off the product screen | Went to | Why |
|---|---|---|
| The three tuning dials | the **Tune sheet** | Taste still belongs to the person holding the phone — but one tap away, not in the way. |
| "Play it with the song" | the **Tune sheet** | It is how you judge a tuning, not how you use the app. |
| "Feel it on its own" | the **Tune sheet** | Same. |
| "Play the phone's own buzz" | **diagnostics** | It exists to answer "is Thrum strong enough?", which is a question about the instrument. |
| "Run the tap test" | **diagnostics** | Measures the hardware, not the product. |
| The technical row | **diagnostics** | For the person who wants the numbers. They will find them. |

This reverses an earlier decision, deliberately. The tuning was put on the product
screen on the grounds that taste belongs to the user — which is still true. What
changed is the recognition that *reachable* and *in the way* are different things,
and the test bench was making the app look unfinished.

### The Empty state, which is the real first impression

```
  Thrum
  Vibrate mode, playing your own music

  ┌──────────────────────────────────────────────────┐
  │  ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁ ▁  a flat line of 1 dp    │  ← what Android gives you today
  └──────────────────────────────────────────────────┘

  Pick the song you want to feel                        ← 25 sp
  Any track on your phone. Thrum follows the bass and   ← 16 sp
  drums, and makes your phone vibrate to them when
  someone calls.

  [              Choose a song              ]           ← the only filled button
```

Four blocks. The flat line in the ribbon is doing the selling, and it is honest: it
is literally the vibration the phone makes today.

**One filled button per screen. In the armed state, none.**

---
### The other states, in order

**Permission** — masthead, then:
- "Thrum has to see your call notifications" at 25 sp, wrapping to two lines
- the paragraph at 16 sp
- one filled button, "Turn on notification access"

No ribbon. This is a gate, and a ribbon would be a promise the app cannot keep yet.

**Blocked** — masthead, then:
- "This phone can't do it" at 25 sp in `warn`
- the paragraph at 16 sp
- the hardware line at 12 sp, muted: `Motor: yes. Strength control: no.`
- **and nothing else.** No button, no way forward.

**Error** — masthead, then:
- "That one didn't work" at 25 sp in `warn`
- the decoder's own sentence at 16 sp
- one filled button, "Choose a different song"

**Playing** — the Armed screen with exactly two changes: the ribbon's playhead is
drawn, and the two quiet rows are replaced by a single full-width glass pill reading
"Stop". No other control appears. A Stop button that exists when nothing is playing is
how people learn to distrust the live ones.

**The Tune sheet** — a bottom sheet, radius 16 on the top corners, fill `field` at 96%,
sliding over the Armed screen, which is dimmed behind it by a 45% black scrim.
Contents, top to bottom: a 40 dp grip bar; "Tune the feel" at 16 sp; the three dials;
"Changes apply straight away. Play it to feel them." at 12 sp muted.

**The slider track is a recessed groove, not a raised bar**, with a glass bead thumb
carrying a 2 dp accent rim. That is the one control where the glass metaphor does real
work: a groove reads as something you push *into*, which is what a dial is.

**Won't work yet** — the Armed screen with the verdict replaced. This is the most
important state to get right, because it is the only one where the app tells the user
to go and change a system setting.
- "Won't work yet" at 25 sp in `warn`
- the paragraph at 16 sp
- the remedy at 12 sp muted
- a full-width **glass** pill, "Open sound settings" — glass, not filled, because the
  app is sending the user somewhere rather than doing the thing itself
- the quiet row "Choose a different song"

The app never changes the setting itself. It explains, and it sends.

---

## 7. Components

| Component | Spec |
|---|---|
| **Glass card** | radius 16, fill `#1C1C1A` 82%, rim white 16% at 1 px, top edge white 26% at 2 dp, 16 dp internal padding |
| **Glass pill (secondary button)** | radius 16 (fully rounded), height 48 dp, glass fill, label 14 sp / 600 |
| **Tinted pill (the compare button)** | same geometry, fill = accent at ~12%, rim = accent at ~40%, label in accent. Tinted rather than filled, so it does not compete with the primary action |
| **Primary button** | solid accent `#D8C513`, dark ink on it (9.68:1), full width, 48 dp, radius 16 |
| **Pulse ribbon** | see §4.4. 96 dp tall, 3 dp bars, 1 dp gap, **0 radius**, silent step = 1 dp stub at the baseline (not a gap) |
| **Dial / slider** | label 16 sp, help 12 sp muted, then the track. Track is **recessed**, not raised — an inset groove. Thumb is a small glass bead with the accent rim |
| **Switch** | M3 switch, on-state accent |
| **Technical row** | 12 sp, muted, letter-spacing 0.4. Reads `2250 steps · 20 ms each · still 10% of the time` |
| **Armed dot** | 12 dp circle, accent, top right of the masthead. Always paired with the word "Armed" for screen readers |

---

## 8. Copy — verbatim, do not rewrite

The voice is plain, specific, and never oversells. It admits failure. It uses
numbers rather than adjectives. Match that or the design and the words will fight.

**Masthead** — `Thrum` / `Vibrate mode, playing your own music`

**Blocked**
- `This phone can't do it`
- `Thrum needs a motor that can change strength. Yours has one speed. Every rhythm would reach you as the same flat buzz your phone already makes, and no setting, update, or future version of this app will change that.`
- `Motor: %1$s. Strength control: %2$s.`

**Permission**
- `Thrum has to see your call notifications`
- `It is how the app knows a call has started. Thrum checks one thing: is this a call? The rest it ignores. Nothing leaves your phone. There is no internet permission in this app and there never will be.`
- Button: `Turn on notification access`

**Empty**
- `Pick the song you want to feel`
- `Any track on your phone. Thrum follows the bass and drums, and makes your phone vibrate to them when someone calls.`
- Button: `Choose a song`

**Loading**
- `Reading %1$s` / `A few seconds. Longer tracks take longer.`

**Error**
- `That one didn't work`
- `There is no sound in that file, so there is nothing to feel. Pick a track with music in it.`
- Button: `Choose a different song`

**Ready / Armed**
- `%1$s · %2$d hits` → e.g. `0:45 · 79 hits`
- Primary (not yet armed): `Use this when someone calls`
- `Play it with the song` · `Feel it on its own` · `Stop`
- Compare: `Play the phone's own buzz` / when playing: `Stop the buzz`
- Compare help: `The vibration Android uses for a call on this phone: one second at full strength, then one second of silence. Play it, then play yours, and compare the two.`
- Text button: `Choose a different song`

**The armed verdict** — headline is `Ready`, or `Won't work yet` when blocked. Body is one of:
- `Your phone is on vibrate, so next time it rings, this is what you feel.`
- `Your phone is ringing, so you will hear your ringtone and feel this. Unless they are the same file, those are two different songs.`
- `Your phone is on silent, and Android throws the vibration away before it reaches the motor. No app can get around that.` + help `It has to be Vibrate, not Silent. Both look like "no sound", but Silent means nothing vibrates at all.` + button `Open sound settings`
- `Your phone is ringing and "Also when the ringer is on" is switched off below, so nothing vibrates.` + help `Turn that switch on, or put your phone on vibrate.`
- `Thrum can't read this phone's ringer setting, so it can't promise anything either way. It will still try when a call comes in.`

**Ringer row**
- `Also when the ringer is on`
- `Your phone plays its own ringtone and Thrum vibrates your track over it. Unless they are the same file, you hear one song and feel another.`

**Technical row** — `%1$d steps · %2$d ms each · still %3$d%% of the time`

**Tuning** — heading `Tune the feel`
- `Punch %1$d` / `How hard a beat lands. It also sets how far Detail can go, so raise this first if Detail runs out of room.`
- `Beat length %1$d ms` / `How long each beat is driven for. This is what decides whether a phone lying on a table moves: a longer drive pushes the table, a shorter one you only feel in your hand. Turn it up for a stronger alert, down to keep the beats crisp and separate.`
- `Distance from the music %1$d` / `0 is as close as it gets: the snare and hats come through alongside the kick, so you feel the whole pattern. Turn it up and the detail drops away until only the bare beat is left.`
- `Changes apply straight away. Play it to feel them.`
- Problem line: `The dials can't change this rhythm at the moment. %1$s`

**Tap-rate test**
- `How fast can this phone tap?`
- `Plays the same tap at six speeds, slowest first, with a pause between each. Hold the phone and notice where the taps stop feeling separate and turn into one long buzz. That speed is this phone's limit, and there is no point asking it for anything faster.`
- Button: `Run the tap test` · running: `%1$d taps a second`

---

## 9. Forbidden

- No purple or indigo, anywhere, ever. It is the strongest AI tell on the banned list.
- No gradients, no mesh backgrounds, no noise textures.
- No emoji.
- No stock imagery, no illustration, no icon set. The only picture in this app is the user's own rhythm.
- No "Get Started". No three-feature-card row — there is one feature.
- No centred hero with two buttons.
- No shadows stacked for elevation. Depth comes from the glass rim and the backdrop.
- No rounded ribbon bars.
- No purple baseline M3, which is the Android form of the same tell.

---

## 10. Accessibility

- Every text pair meets WCAG AA, and the glass work is specified so it still does
  (§4.3). Do not introduce a text colour on the glass without computing it.
- Colour is never the only signal — armed is a dot *and* a word; the verdict is a
  sentence *and* a colour.
- Every control is 48 dp minimum.
- The pulse ribbon carries a real content description: *"The rhythm: 79 hits over
  45 seconds"*.
- All states must survive `fontScale` 1.3.

---

## 11. Reference material

| What | Where |
|---|---|
| Real screenshots, 1080×1920 | `docs/store/assets/screenshot-01-armed.png`, `screenshot-02-tuning.png` |
| The brief this overrides | `design-brief.md` |
| Palette + type + shapes | `app/src/main/java/com/mosman/thrum/Theme.kt` |
| Spacing, radius, motion, ribbon | `app/src/main/java/com/mosman/thrum/Tokens.kt` |
| Every state and its layout | `app/src/main/java/com/mosman/thrum/ThrumScreen.kt` |
| The signature move | `app/src/main/java/com/mosman/thrum/PulseRibbon.kt` |
| All copy | `app/src/main/res/values/strings.xml` |
| App icon | `app/src/main/res/drawable/ic_launcher_foreground.xml` on `#1C1C1A` |

The two screenshots are the most useful reference — they are the app actually
running on a Pixel 6 Pro, not a mockup.

---

## 12. Open questions

1. **Light mode.** The app has a designed light scheme and follows the system
   setting. Glass is specified for dark. In daylight a dark pane over a light field
   becomes a grey smudge, so light mode needs either a white-translucency variant or
   the flat treatment left alone. **Undecided.**
2. **Does the Tune sheet need a way in that is visible enough?** The dials moved off
   the product screen, which is right, but "Tune it" is a single muted row. If nobody
   finds it, the tuning work may as well not exist. **Undecided.**
3. **Is the armed screen too empty?** It now has no filled button and a lot of field
   showing below the content. That is deliberate — a settled resting state — but it
   is a judgement call about how much emptiness reads as calm versus unfinished.
   **Undecided.**
4. **Where "Feel it on its own" belongs.** It sits in the Tune sheet for now. It is
   arguably a product feature rather than a tuning aid — the way you check that the
   thing you just armed is what you wanted. **Undecided.**

### Settled this pass

- ~~The ribbon card: card or bare inset.~~ **Card, at 30% fill.** It is the one
  surface with no text on it, so it is where the glass gets to be genuinely
  transparent, and it needs a rim to read as a pane at all.
- ~~Whether the dials are glass.~~ **Yes, but inside the Tune sheet, not on the
  product screen.** They were never the problem; their *location* was.
