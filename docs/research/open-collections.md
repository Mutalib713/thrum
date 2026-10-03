# Open-licence sources for the built-in collection

Researched 2026-10-03, the day Mutalib decided the catalog stays **offline**
(`PROFILE.md` §4 item 15, §6). The app will ship a searchable, browsable
collection of open-licence sounds and ringtones, each with a ready-made haptic,
and will never stream or download anything. This file is the research he asked
for: what exists, what its licence really says, and what is still unverified.

**Nothing here is shipped yet, and no source is final until its own licence
page has been read and the specific files' licences recorded in the ledger**
(see the checklist at the bottom). Search summaries are evidence of where to
look, not proof of what a licence allows.

---

## The decision this serves

| Old plan | Now |
|---|---|
| **Thrum Originals** (Task 27): a few pieces Thrum commissions and owns | **Merged** into one built-in collection |
| **The online catalog** (Task 28): search and stream over the internet | Dead as a networked feature — Rule 3 stands. Its search/browse screens become the UI over the built-in collection |

The two were never the same thing before; they are one thing now. Every track
in the collection ships inside the APK with a haptic made by the same analyser
on the PC, so the Music tab is never empty and a song for calls exists on day
one — the Originals' three jobs, at collection scale.

---

## Sources, with what their licence actually says

### Clean — CC0 / public domain (the preferred tier)

**CC0** means the author gave up every copyright claim: anyone may use, modify
and redistribute the file for any purpose, including commercial, with no
attribution. This is the only tier that satisfies Sacred Rule 4 ("never ship
audio Thrum doesn't own outright") without a rule amendment.

| Source | What it holds | Licence | Notes |
|---|---|---|---|
| [OpenGameArt.org](https://opengameart.org) | Full-length game music, per-asset licence, **CC0 filter exists** | Per asset; CC0 packs verified in search (e.g. a 15-song, 155 MB CC0 pack) | The richest source of full songs. Skews electronic, chiptune, orchestral, ambient |
| [Kenney.nl](https://kenney.nl/assets) | Game audio packs: jingles, UI, RPG, sci-fi, impact sounds | **Creative Commons CC0, confirmed** — stated on the site per pack | Short jingles can suit ringtones; most packs are SFX, not songs. Zero licence risk |
| [itch.io](https://itch.io) game-asset packs (CC0 tag) | Large curated bundles (one offers 200+ loops; "Public Domain Trash Music"; the Open Game Art Bundle) | Per pack — **verify each pack's page before download** | Bundles are the fastest way to get a lot of candidate music at once |
| [Freesound](https://freesound.org) | Hundreds of thousands of individual clips | Per clip; **CC0 filter exists** | Mostly sound effects rather than music, so curation is heavy. Needs an account (and their API for bulk download) |
| [Wikimedia Commons](https://commons.wikimedia.org) | Mixed audio, including ringtone-length pieces | **Per file** — CC0 / public domain / CC-BY mixed | Every file's licence must be recorded in the ledger; never assume from the category |
| [Musopen](https://musopen.org) | Public-domain classical recordings | Public domain for the older catalogue (some newer files CC-BY) | Clean licence, poor genre fit for this audience |

### Usable only with a rule change — attribution licences

**CC-BY** allows redistribution **if the author is credited**. Shipping CC-BY
audio is legal with a credit in About — but it is not "owned outright", so it
needs a one-line amendment to Sacred Rule 4 with Mutalib's explicit approval,
plus a permanent credit. **CC-BY-NC and CC-BY-ND are ruled out entirely**: NC
(no commercial) is unsafe for any Play Store app, and ND (no derivatives) sits
badly with a collection that re-contextualises the audio as a haptic carrier.

| Source | Licence | What it would cost |
|---|---|---|
| [Kevin MacLeod / incompetech](https://incompetech.com) | CC-BY 3.0/4.0 | Huge, well-produced catalogue that fits ringtones far better than game audio; costs the Rule 4 amendment and a visible credit |
| [Free Music Archive](https://freemusicarchive.org) | Per-track CC licences | Real music by real artists; every track checked and credited individually |
| AOSP's own ringtones (`frameworks/base/data/sounds`) | Apache 2.0 | Legal with a licence notice, but they are the stock sounds users already have — no reason to ship them |

### Ruled out, and why

| Source | Why |
|---|---|
| **Pixabay** | The licence **forbids redistributing the audio "on its own or as part of a template, media library, or collection product"** — and a browsable built-in collection is exactly that. Also flagged: content must not be "the primary value of an audio-only production". A collection app is both. Out, despite being free and attribution-free |
| Zedge-style ringtone sites, XDA "firmware rip" packs | Not licensed at all — user-uploaded copyrighted clips and manufacturers' firmware sounds. Shipping these is how apps get pulled |
| Any "royalty-free" pack whose licence wasn't read to the end | Many such licences allow use in *your media* but forbid handing the file itself to users — which is what a collection does. Sacred Rule 4 exists for exactly this trap |

---

## The honest genre problem

Open-licence collections skew **electronic, chiptune, orchestral, ambient and
game audio**, because that is who publishes under CC0. Afrobeats, Amapiano,
Highlife, Gospel and Hip-hop — the genres named for the catalog screens — are
essentially absent from open licences, and they are the ones the original
catalog wanted. The options, which are Mutalib's to weigh later, not now:

1. **Launch with what the open world has**, curated for feel rather than genre.
2. **Commission a small number of signature pieces** — the original
   Originals path (R14) — and build the collection around them. A Ghanaian
   Afrobeats producer under a written agreement remains the way to get genres
   the open world cannot supply.

There is also no single famous "CC0 ringtones" repository — the search found
only small projects. The practical path is curating from CC0 **music** packs
and building the ringtone set ourselves.

---

## How the collection gets curated — by measurement, not by ear alone

The collection has one criterion that matters more than genre: **made to be
felt**. A track with a clear kick and real gaps between beats becomes a rhythm;
a wall of sound becomes the hum R9 describes. That criterion is measurable on
the PC, with no phone:

- Run every candidate through the analyser (the QA suite's fixtures already do
  exactly this for synthetic audio).
- Score it with `Feel.of()` — the metrics added on 2026-10-03.
- **Keep only tracks whose haptic reads as a rhythm**: stillness at or above
  the stock buzz's 50 %, sustained drive at or under its 0.500, no run that
  outlasts a beat.

A track that fails the line can still ship as *music to hear*, but it should
not headline the collection, and it must not become the default song for
calls. The presets work in Task 15 will set the exact thresholds; the
measurement harness already exists.

---

## Before anything ships — the checklist

1. **Read the actual licence page of every pack and every file** — the source's
   own terms, not a summary (including this file's summaries).
2. **No NC, no ND, ever.** CC0 preferred; CC-BY only after Mutalib amends Rule
   4 and the credit exists in About.
3. **Start a licence ledger** in the repo — one line per shipped file: title,
   author, source URL, licence, date checked. The
   [OpenAlarm project's SOURCES file](https://github.com/open-alarm/OpenAlarm)
   is the pattern: an attribution ledger for every shipped ringtone asset.
4. **Keep a copy of each licence text** alongside the ledger, so a takedown
   question can be answered in minutes five years later.
5. **Measure every candidate** through the analyser before it enters the
   collection (see above).
6. **Store listing and data-safety answers** must mention bundled third-party
   audio only if the licences require attribution — CC0 needs nothing public.
