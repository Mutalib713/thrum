# Thrum — design brief

One brief per project. Each phase writes a block; the next phase reads it. Tasks 6 and 7.

---

## Phase 0 — Product truth

**Who.** Someone with an amplitude-capable flagship — Pixel, Samsung, recent mid-to-high Android. One-handed, on their own phone, indoors. They open this app **once**, set it up, and ideally never open it again. Ghana floor still applies (sunlight-readable contrast, thumb-reachable actions, no reliance on a fast connection) but the app is **entirely offline**: no `INTERNET` permission, now or ever (Sacred Rule 3).

**Problem.** Vibrate mode is one flat, anonymous buzz. It carries no information and no pleasure. This app turns the user's own music into what they feel when the phone rings.

**Emotional state.** Arriving **skeptical** — "phones don't do this, is this real?" Leaving **convinced**, having felt their own song in their hand. Skepticism is the honest starting point: this app makes a claim most people's phones have never made good on.

**Primary action.** *Pick a track and arm it.* One thing. Preview exists to serve it; everything else is subordinate.

**What they must remember.** The moment the phone played *their* song as a rhythm. That is the signature move's job.

**What the product needs.** No money changes hands. What it needs is **trust**: an honest verdict on hardware, plain words about the notification permission, and never claiming a vibration happened when it didn't — the failure mode this project has now hit three separate times (R2 silent mode, R8 oversized waveforms, Task 5 audio start).

**Surface mode:** **Operate** (impeccable §8) — the user completes a task. Not a landing page, not persuasion. Every screen is judged on whether it gets them to "armed" without confusion.

---

## Phase 1 — Experience

### Flow

```
launch
  ├─ motor can't vary strength ──► BLOCKED (terminal, honest, no "try anyway")
  └─ capable
       ├─ no notification access ──► PERMISSION (why, in plain words, one button)
       └─ granted
            ├─ no track yet ──────► EMPTY (teaches + one action)
            └─ track chosen
                 ├─ reading ──────► LOADING (named track, real progress)
                 ├─ failed ───────► ERROR (what happened, what to do)
                 └─ built ────────► READY  ──preview──► same screen, playing
                                      └──arm──► ARMED (this is what a call feels like)
```

**Entry is always the launcher icon.** Nobody arrives from a link; there is no link. The app has no network.

### The states matrix

One screen, seven states. All designed in Phase 4, none "remembered later".

| State | Reached when | Teaches | The one action |
|---|---|---|---|
| **Blocked** | `!Haptics.capability().usable` | this phone's motor has one speed, so nothing here would be felt | none — and no "try anyway" button. Sacred Rule 2 |
| **Permission** | listener not bound | Thrum watches for the *call notification*; that's how it knows to start, and it never reads anything else | Turn on notification access |
| **Empty** | capable, permitted, no score | any song on the phone becomes the rhythm | Choose a song |
| **Loading** | decoding + analysing | the track's name, and that this takes a few seconds | Cancel |
| **Error** | decode failed / too long / silent | exactly what happened and what would work instead | Choose a different song |
| **Ready** | score built, not armed | what the rhythm looks like and that it can be felt now | Arm it |
| **Armed** | score saved and live | what will happen on the next call | Change the song |

### Edge cases, from real data

- **Long names.** Mutalib's own library: `Maher Zain - Antassalam - Official Music Video _ ماهر زين - أنت السلام ( 128kbps ).m4a`. Right-to-left script, 90 characters. Two lines then ellipsis, never a broken layout.
- **Long tracks.** A 3:58 track is 11,922 steps and the vibrator silently drops anything over ~10,500 (R8). Handled in code by coarsening; the UI must not pretend 20 ms resolution when it shipped 40 ms.
- **Silent file.** Decodes fine, produces nothing to feel. That is an *error state with a specific message*, not a blank success.
- **Permission revoked while armed.** The armed state is a lie the moment access is withdrawn. Polled, and the screen falls back to PERMISSION.
- **fontScale 1.3+** must not break any state.

### Wireframe in words — the one screen

```
edge-to-edge, dark field, single scroll column, no top app bar
(a wordmark is not navigation; this app has one screen)

  THRUM                                    [status dot]
  one line of what this is, in the voice

  ┌─ THE PULSE RIBBON ─────────────────┐   ← the signature move
  │  the score, drawn as vertical bars  │     empty state: a flat line
  │  playhead sweeps it while playing   │     it IS the product, made visible
  └─────────────────────────────────────┘
  track name · 3:58 · 509 hits

  [ primary action, full width, thumb-reachable ]
  [ secondary ]  [ tertiary ]

  a quiet row of the technical truth, monospaced
  (steps × ms, strength) — shown because this
  user is the kind who wants to know
```

---

## Phase 2 — Direction

**Tone — "instrument, not app".** A well-made piece of audio equipment: dark, precise, unhurried, confident enough to state a number without decorating it. Not playful, not "music app fun". The user is skeptical; showmanship reads as cover for something that doesn't work. Restraint reads as *this thing knows what it's doing*.

**Colour world — `sulphur-concrete`.** Wet board-marked concrete in a stairwell, with a sulphur-yellow safety line painted across it. Chosen because the language of **machinery that moves** is exactly this product: a motor throwing mass around inside a phone. Safety yellow is what humans paint on things that vibrate.

| role | hex | |
|---|---|---|
| field | `#1C1C1A` | the concrete |
| field2 | `#242422` | raised |
| surface | `#2B2B28` | cards |
| ink | `#EFEFEA` | 14.80:1 on field |
| ink2 | `#A8A8A1` | 7.14:1 on field |
| rule | `#3A3A36` | hairlines |
| **accent** | `#D8C513` | the safety line — 9.68:1 on field |
| accent2 | `#E0691C` | warning/error |
| support | `#59A1D4` | informational |

Every pair computed by `palette.py`, not eyeballed. **Rejected:** the data layer's own recommendation of `#1E1B4B` / `#4338CA` — indigo-violet, the strongest AI tell on the banned list — and its Righteous/Poppins pairing, the default "music app" look.

**Material You / dynamic colour — deliberately opt-in, not default.** PLAN Task 7 asks for dynamic colour. Shipping it as the default would hand this app's identity to whatever wallpaper is set, which on a purple wallpaper reproduces exactly the banned look, and would make the semantic states (armed, blocked) wallpaper-derived — those must never move. The brand scheme is the default; wallpaper colour is a switch the user can throw. This follows `compose-m3.md`: dynamic colour always behind a brand fallback, semantic colours fixed.

**Signature move — the pulse ribbon.** The score drawn as vertical bars, with a playhead sweeping it in real time as the rhythm plays. It is memorable, it exists in no other app, it is the one thing a screenshot carries — and it does real work: it is how the user *sees* that the rhythm follows their song, before they ever feel it. Empty state is a flat line, which is precisely what Android gives them today.

**What we refuse.** No purple or indigo anything. No `Get Started`. No three-feature-card row — there is one feature. ~~No glass.~~ **Overridden 2026-09-29 — see Phase 4.** No emoji anywhere. No centered-hero-with-two-buttons. No stock imagery: the only picture in this app is the user's own rhythm. No baseline M3 purple, which is the Android form of the same tell.

**Anti-convergence check** (vs the last project, ACES redesign): different tone (instrument vs editorial energy), different accent (sulphur yellow vs ACES palette), different layout axis (single vertical column, no sections). Passes on three of four.

---

## Phase 3 — System

Tokens live in `ui/Theme.kt`, `ui/Tokens.kt`. No raw values in composables after this point.

- **Spacing** — 4-pt base: 4 · 8 · 12 · 16 · 24 · 32 · 48 · 64. `Arrangement.spacedBy(Space.*)` everywhere.
- **Type scale** — ~1.25 ratio: 12 · 14 · 16 · 20 · 25 · 31 · 39. Body 16sp minimum, line-height 1.5.
- **Radius** — small 6 · medium 10 · large 16. The ribbon's bars are square: a moving machine part is not rounded.
- **Elevation** — tonal, not shadow stacking (dark theme).
- **Touch targets** — 48dp minimum, guarded by `minimumInteractiveComponentSize()`.
- **Motion** — 120ms micro, 240ms structural, `FastOutSlowInEasing`; all gated behind `ANIMATOR_DURATION_SCALE == 0`.

---

## Phase 4–6

Filled in as built. Gate output and critique changes recorded here.

### 2026-09-29 — "No glass" withdrawn, liquid glass adopted

Phase 2 refused glass outright. That refusal was written before the app had been seen
running on a phone, and it is withdrawn at Mutalib's request.

**Why it can be withdrawn safely.** The usual argument against glass is that it
destroys text contrast, because text ends up on an unknown composite. Thrum escapes
that, for one reason: **it generates its own backdrop.** The backdrop is the score
drawn as wide bars and blurred, so its brightest possible value is a constant we
choose. With that capped at `#54470C` and the panel fill fixed at `field` 82 %, the
composite is bounded for the entire length of every track — L stays inside
0.011–0.017, so ink holds 13.5:1 or better and ink2 6.5:1 or better everywhere. That
is arithmetic, not taste.

**The consequence, which is the part that must not be forgotten:** a panel carrying
text cannot be very transparent. The glass reads from its rim, its bright top edge,
and from the backdrop at full strength in the gutters *between* panels. Making the
fill more transparent to look more like glass trades a guarantee for a decoration.

**Also unchanged:** the pulse ribbon is never glazed. It is a machine part shown at
its real shape — flat, full-contrast, square-cornered bars on a glass card, never
under glass.

Full spec, copy deck, and the Figma prompt: `docs/design/`.
