# ORBIT — Design Direction

A personal tracking app with a mission-control personality: quiet, precise, a little cold, deliberately confident. Every option below is picked to reinforce that — nothing decorative, nothing "friendly SaaS."

---

## 1. Colour Palette

Three directions, all built around the graphite/violet/cyan idea you're already leaning toward — differing in how loud the accent gets.

### Option A — "Deep Space" (recommended default)
The most restrained. Violet is a whisper, cyan does the talking, and only for the things that matter (live status, active tracking, alerts).

| Role | Hex | Use |
|---|---|---|
| Background (base) | `#0B0C10` | App canvas |
| Surface / card | `#14151B` | Cards, panels |
| Surface raised | `#1B1D25` | Modals, popovers |
| Border / hairline | `#2A2C36` | Dividers, card edges |
| Text primary | `#E9E9EE` | Headlines, body |
| Text secondary | `#8B8D98` | Labels, metadata |
| Accent — violet | `#8B7CF6` | Secondary accents, selected states |
| Accent — cyan | `#4FD1E5` | Primary accent, live/active data, key CTAs |
| Success | `#3DD68C` | Positive deltas |
| Warning/Alert | `#F5A623` | Caution states |
| Danger | `#F2545B` | Negative deltas, errors |

### Option B — "Nebula" (more saturated, more expressive)
Violet gets a real presence as a gradient partner to cyan — good if ORBIT will have a hero/landing surface or data visualizations you want to feel alive rather than clinical.

| Role | Hex |
|---|---|
| Background | `#0A0A0F` |
| Surface | `#16151F` |
| Border | `#2D2A3D` |
| Text primary | `#F1F0F6` |
| Text secondary | `#9691AD` |
| Accent — violet | `#A78BFA` |
| Accent — cyan | `#22D3EE` |
| Gradient accent | `linear-gradient(135deg, #8B7CF6 0%, #4FD1E5 100%)` — reserved for logo, hero numbers, onboarding only |

### Option C — "Lab Grey" (lightest-touch, near-monochrome)
Graphite does almost all the work; violet and cyan appear only as single-pixel accents (active tab underline, focus ring, one chart line). Best if you want ORBIT to feel like an instrument panel rather than a "themed" app.

| Role | Hex |
|---|---|
| Background | `#0D0E12` |
| Surface | `#17181D` |
| Border | `#26272E` |
| Text primary | `#EAEAEC` |
| Text secondary | `#7C7D85` |
| Accent — cyan (only accent) | `#5CE1E6` |
| Violet | reserved for a single "focus" data series or the logo mark only |

**My pick:** Option A. It gives you a real two-colour identity (violet + cyan) without either fighting for attention, and it scales well once you add charts — cyan reads as "now/live," violet reads as "categories/context."

---

## 2. Typography

Both Inter and Geist will look good here — the difference is personality, not quality.

- **Geist (Vercel)** — slightly more geometric, tighter numerals, feels more "engineered." Geist Mono is genuinely excellent and gives you a built-in monospace for stats/timestamps/IDs without hunting for a pairing. **Recommended** if ORBIT's data-density (numbers, timestamps, logs) is central to the experience.
- **Inter** — marginally warmer, extremely battle-tested, huge glyph coverage, slightly better at small sizes in dense UI. Safer choice, near-invisible personality.

**Recommendation: Geist Sans + Geist Mono.**
- Geist Sans for all UI text, headings, labels.
- Geist Mono for anything numeric/live: timestamps, counters, coordinates-style metadata, log lines. This one pairing does a lot of the "mission control" work by itself — mixed sans/mono is a classic technical-instrument signal (think flight computers, terminal dashboards).

**Type scale (suggested):**
- Display / big number: 40–56px, Geist Sans Medium, tight tracking (-2%)
- H1: 24px Semibold
- H2: 18px Semibold
- Body: 14px Regular
- Caption/meta: 12px, Geist Mono, uppercase, +4% tracking (this is the detail that reads "instrument panel" — small mono caption labels like `LAST SYNC · 04:12:08`)

---

## 3. Visual Language

Three ways to interpret "premium, technical, restrained, mission-control" — pick the one that matches how much *presence* you want ORBIT to have.

**A. Control Room** — dark canvas, hairline grid, thin 1px borders everywhere, data feels like it's being *monitored* rather than *decorated*. Sparse use of glow (a soft cyan box-shadow only on the currently-active/live element). No gradients except in the logo.

**B. Orbital Instrument** — same base, but circular/radial motifs recur intentionally: progress shown as arcs not bars, a subtle radial gradient vignette behind key numbers, dot-grid backgrounds instead of line-grids (reads as a star field at low opacity, ~3–4%). More "designed," slightly less austere than A.

**C. Terminal Precision** — leans hardest into the mono/technical read: monospace everywhere for data, sharp corners (0–4px radius) instead of soft ones, borders instead of shadows for elevation, almost no colour except cyan-on-graphite. The most minimal and the most "personality through restraint" — closest to something like a Bloomberg terminal or a spacecraft dashboard.

**My pick: B, Orbital Instrument.** It's the one that actually earns the name ORBIT — the radial/arc motif gives you a signature shape language (dashboards, progress rings, the logo) without adding visual noise, and it still sits comfortably next to Option A's colour palette.

---

## 4. Component Style

Keeping this consistent with Option A (palette) + Orbital Instrument (visual language):

- **Radius:** 10–12px for cards/panels, 8px for buttons/inputs, 999px (full round) for pills/tags/avatars/status dots. Avoid 16px+ — it starts to feel consumer-social rather than instrument-grade.
- **Borders over shadows:** 1px `#2A2C36` hairline borders are the primary way to separate surfaces. Reserve shadow for true overlays (modals, dropdowns) — a soft, low-opacity shadow (`0 8px 24px rgba(0,0,0,0.4)`), never on resting cards.
- **Cards:** flat fill (`#14151B`), 1px border, no shadow at rest. On hover/focus: border shifts to a 20%-opacity cyan, nothing else moves. This is the single cheapest "premium" signal — restraint on hover states.
- **Buttons:**
  - Primary: solid cyan fill, dark text (`#0B0C10`) — cyan is loud enough that it should carry dark text, not white, for contrast and to avoid looking like a generic blue button.
  - Secondary: transparent, 1px border, light text — becomes solid on hover.
  - Ghost/tertiary: text only, cyan on hover.
  - Keep corner radius on buttons slightly tighter than cards (8px vs 12px) — it's a small detail that separates "interactive chrome" from "content containers."
- **Inputs:** dark fill matching card surface, 1px border, focus ring = 1px cyan border + faint cyan glow (2–3px blur only, not a heavy halo).
- **Charts:** this is where Orbital Instrument earns its keep —
  - Progress/completion → radial arcs, not bars.
  - Line charts → thin (1.5px) lines, no fill/area unless showing a single hero metric, cyan for the primary series, violet for a secondary comparison series, everything else drops to grey.
  - Gridlines: near-invisible (`#1E1F26`), dashed rather than solid — reads as reference, not decoration.
  - Data labels in Geist Mono, small, uppercase.
- **Status/live indicators:** a small pulsing dot (cyan, soft glow, gentle opacity animation — not a hard blink) is a good recurring "this is live/tracked right now" signal that ties back to the orbital/mission-control idea without needing new iconography every time.

---

## 5. Brand Identity — ORBIT

**Wordmark:** Set in Geist Sans (or Geist Mono for a more terminal feel), all-caps, generous letter-spacing (+6–8%) — `O R B I T`. Wide tracking on a short word is a reliable way to make a wordmark feel engineered rather than typed.

**Icon/mark — three directions:**

1. **The literal orbit** — a single dot (the "planet") with one or two thin elliptical rings around it, one ring slightly tilted for depth. Rendered in outline only (1.5–2px stroke), no fill except the center dot in cyan. Scales down cleanly to a favicon — even just the tilted ring + dot reads as "orbit" at 16px.
2. **The abstracted ring** — drop the "planet" entirely, keep just one broken/incomplete ring (like a progress ring paused mid-cycle) — ties the logo directly to the "progress arc" component language above, so the icon and the UI literally share a shape vocabulary. Very minimal, a bit more abstract/premium.
3. **Orbit as monogram** — a lowercase "o" built from two overlapping thin circles (like a Venn diagram/eclipse), one graphite, one cyan, overlapping in the middle — implies orbit/convergence without literal ring-and-dot iconography. Most "startup wordmark" of the three, least literal.

**My pick: #2, the incomplete ring.** It's the most distinctive (nobody else's "orbit" logo looks like this), it's the easiest to animate later (the ring can visually "complete" on load/sync — a nice, subtle motion signature), and it reuses the same arc shape you'll already be drawing in the dashboard's progress components. That repetition — icon and UI sharing one shape — is what makes an identity feel *designed* rather than assembled.

**OSCAR (in-app assistant/companion identity):**
If OSCAR is ORBIT's assistant/voice inside the product, keep it visually quiet rather than mascot-like — no cartoon character. A good pattern: OSCAR is represented only by the same dot-and-ring mark, but *animated* — the ring rotates gently while OSCAR is "thinking," and pulses once when it responds. This keeps one visual language across the whole product: ORBIT the app, OSCAR the assistant, and the progress-tracking components are all speaking the same "ring" dialect, just in different contexts. It's a good way to give OSCAR personality (motion, timing, a sound-off "breathing" idle state) without needing separate character art that would clash with the restrained aesthetic.

---

## 6. Light Mode

ORBIT can absolutely support both — the trick is keeping it feeling like the *same product* in either mode, not two different apps. The move: keep hue and saturation of the accents nearly identical, only shift lightness/contrast of the neutrals. Dark mode stays the "default" personality; light mode should read as the same instrument panel under daylight, not a separate cheerful theme.

### Light mode palette (paired with Option A — Deep Space)

| Role | Hex | Notes |
|---|---|---|
| Background (base) | `#F5F5F7` | Slightly cool, not pure white — keeps the "graphite" feel |
| Surface / card | `#FFFFFF` | Cards lift off the base with a hairline, not a shadow |
| Surface raised | `#FBFBFC` | Modals, popovers |
| Border / hairline | `#DEDFE3` | Slightly darker than dark-mode's border-to-surface ratio, since light mode relies more on borders than glow |
| Text primary | `#16171C` | Near-black, not pure black — softer, matches the graphite identity |
| Text secondary | `#6B6C76` | |
| Accent — violet | `#6D5FE0` | Darkened ~10% from dark-mode violet for AA contrast on white |
| Accent — cyan | `#0EA5B7` | Darkened/desaturated from `#4FD1E5` — the dark-mode cyan is too light to hit contrast on a white background, so this is the single most important recoloring in the whole switch |
| Success | `#1E9E63` | |
| Warning | `#C97A0A` | |
| Danger | `#D93B44` | |

**Key rule:** never just invert the dark palette (light text → dark text, dark bg → light bg) and reuse the same accent hexes — `#4FD1E5` cyan on white fails contrast almost immediately. Accents get *darkened*, not lightened, when moving from dark to light mode.

### What else needs to change, not just recolor

- **Borders replace glow.** Dark mode's soft cyan box-shadow glow on focus/active states reads as murky on white. In light mode, swap glow for a crisper 1.5–2px solid cyan border — same signal, different mechanism.
- **Shadows get real weight.** On dark backgrounds, a shadow barely reads; on light backgrounds it's your main elevation cue. Modals/dropdowns in light mode want a visible, slightly larger shadow (`0 12px 32px rgba(22,23,28,0.12)`) since there's no "glow" crutch to fall back on.
- **The dot-grid / star-field texture (Orbital Instrument's background texture) inverts to a very faint graphite dot on the light background instead of a faint white one** — keep opacity low (~4–6%) so it doesn't read as "dirty" paper.
- **Logo mark:** the incomplete-ring mark stays cyan-on-transparent in both modes — it's one of the few elements that shouldn't need a light/dark variant at all, which is part of why it's a good pick as the primary mark.

### Implementation note
Set this up as CSS custom properties (`--bg`, `--surface`, `--border`, `--text-primary`, `--accent-cyan`, etc.) rather than hardcoding hex values in components, and toggle by swapping a `data-theme="light"` / `data-theme="dark"` attribute on the root. That's the standard pattern and it means every component above (cards, buttons, charts) just references the variable and gets both themes for free.

---

## Summary pick, if you want one coherent system to start building against

- **Palette:** Option A, Deep Space
- **Type:** Geist Sans + Geist Mono
- **Visual language:** Orbital Instrument
- **Components:** hairline borders, 10–12px radius, radial progress, mono data labels
- **Logo:** incomplete-ring mark, animates to complete on sync/load
- **OSCAR:** same ring mark, motion-driven personality instead of a character
