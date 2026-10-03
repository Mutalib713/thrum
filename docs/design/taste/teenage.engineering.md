# Design Map

Measured 2026-10-03 from https://teenage.engineering/products/ep-133 at 1440×900 (viewport, mid and footer captures; page 18,009 px tall). Phase 1.5 input for Thrum's product-screen redesign. **Ratios and rhythm transfer to Thrum; values and colours do not.**

## Spacing Scale
- base unit 4.408px (fluid, viewport-derived)
- 4.41 · 7.35 · 8.82 · 14.69 · 22.04 · 33.06 · 44.07 · 66.12 px (= 1 · 1.67 · 2 · 3.33 · 5 · 7.5 · 10 · 15 units)
- ~200px empty field between content bands (~approx, from the mid capture)

## Font Hierarchy
- 52.898px — statement (te-20)
- 26.449px — nav titles, weight 100 (te-20)
- 19.102px — intermediate (te-20)
- 16px — text-only buttons, weight 100
- 13.2245px — sublinks, captions, uppercase spec list (te-20 / TechnoType)
- 9px — legal micro text
- ratio: ×2.0 between the three main steps, ×1.444 for the single intermediate

## Color Palette
- #F9FAF9 page field · #E5E5E5 panel (30% of bg area) · #F5F7F6 (13.1%)
- #989FA5 photo/diagram grey (24.1%) · #000000 dark band (12.7%)
- text: #000000 on light, #E5E5E5 on dark, #ABB5BA muted
- accent #FF5000 — 1.6% of bg area, on the product and one label only

## Image Ratios
- 1:1 full-bleed hero and feature photos (1440×1440)
- 2.02:1 line diagrams (SVG 743×367, rendered 1087px)
- 1.14:1 secondary photo

## Component Tokens
- radius: 4.41 · 7.35 · 8.82 · 14.69 · 22.04 px
- shadows: none
- buttons: 16px, weight 100, radius 0, padding 0 (words, not boxes)
- grid: 12 columns × 107.7px, row gap 14.69px, container 1425px, no max-width
- motion: `left 0.35s`, `transform 0.2s ease-in`, opacity, colour
- :focus-visible present · prefers-reduced-motion present

---

# Taste DNA

### Jumps, not ladders
- **Trigger**: When separating labels, titles and statements on a page with very little text
- **Decision**: A ×2 doubling scale (13.22 → 26.45 → 52.90) with one intermediate over a 5–7 step 1.25× ladder
- **Reason**: With so few words, each one has to announce its rank instantly. Sizes 25% apart read as an accident; sizes 100% apart read as a decision.
- **Evidence**: 13.2245px ×70, 26.449px ×18, 52.898px ×7; only 19.102px sits between (×1.444)

### The accent belongs to the hardware
- **Trigger**: When choosing where the brand orange could appear on a page selling an orange-keyed instrument
- **Decision**: Orange kept to the product and one label (#FF5000, 1.6% of background area) over tinting buttons, headings or nav
- **Reason**: An accent spread across chrome becomes wallpaper. Held back, the eye reads orange as "this is the thing you press".
- **Evidence**: neutrals hold #E5E5E5 30%, #989FA5 24.1%, #F5F7F6 13.1%, #000 12.7%; buttons are text-only in #ABB5BA / #E5E5E5

### No containers
- **Trigger**: When grouping specs, captions, diagrams and photos down an 18,000px page
- **Decision**: Full-bleed background bands and ~200px of empty field over cards, borders or drop shadows
- **Reason**: Boxes make content feel like inventory to compare. Bands make it a sequence you walk through, which suits one product told in scenes.
- **Evidence**: 0 card components, 0 shadows in DOM or captures, container 1425px with 0 padding, light → photo → black transitions

### Labels vs readouts
- **Trigger**: When the page had to carry both navigation and hard specifications
- **Decision**: A thin lowercase house face for places and an all-caps technical face for facts, over one family in several weights
- **Reason**: A front panel prints its labels and its display differently so a glance tells you which is which. Specs read as measurements, not marketing.
- **Evidence**: te-20 on 884 nodes with weight 100 ×51 on nav titles; TechnoType on 130 nodes including "128 MB SAMPLER COMPOSER" and the 9-line uppercase spec list
