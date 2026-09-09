# ADR-030: Frontend Component Primitives — Hand-Built + Radix, Not shadcn/ui

## Status
Accepted — ratified by the product owner 2026-09-09. Supersedes the "shadcn/ui"
line in [ADR-006](ADR-006-nextjs-frontend-framework.md) §Decision and in
Tech Arch §4 / Blueprint §"Frontend Stack" for the component-library choice only
(Next.js, Tailwind, TanStack Query, Recharts are unchanged). The Radix pass
(`Dialog` / `Tabs` / `Tooltip` / `DropdownMenu`) shipped in `0689bdd` and was
accessibility-audited.

## Date
2026-09-09

## Context

Tech Arch §4, the Engineering Blueprint frontend stack, and
[ADR-006](ADR-006-nextjs-frontend-framework.md) (Accepted) all name **shadcn/ui**
as the component library. The `README.md` stack table repeats it.

**shadcn/ui was never adopted.** From the Milestone-0 bootstrap onward the web
app has used a hand-built primitive set in `apps/web/src/components/ui/`
(`Button`, `Card`, `Dialog`, `Badge`, `Field`, `ProgressRing`, `CapabilityBar`,
`States`, `WhyThis`, `SkillWeightPicker`, `PlaceholderPage`), extended in M2–M6.
No `components.json`, no `@radix-ui/*`, no `class-variance-authority`. This was a
deliberate M0 choice that was never revisited or written down — it contradicts
an Accepted ADR, so it needs one of its own.

Why the hand-built route made sense and still does:

- The UX Spec §3 design system ("Deep Space" palette, "Orbital Instrument"
  motif — radial progress arcs not bars, hairline borders, dot-grid backgrounds,
  mono data labels, a two-colour cyan/violet identity — [ADR-007](ADR-007-deep-space-design-tokens.md))
  is **bespoke**. Restyling every shadcn component to match it means fighting
  Radix's DOM structure and shadcn's class conventions on every primitive.
- Several required primitives (UX §39) have **no shadcn equivalent** and must be
  custom anyway: `RadialProgress` (`ProgressRing`), `CapabilityBar`, the
  Priority-Engine explainer (`WhyThis`), `Metric`, chart containers, the OSCAR
  panel.
- The custom primitives are small (`Button` ~40 lines, `Card` ~40, `Dialog`
  ~55), token-driven (`var(--accent-cyan)` etc.), theme-aware, and cover
  loading/disabled/error states. Two have unit tests.

Where the hand-built route has **real gaps** against UX §31 (WCAG AA) and §39
("support keyboard navigation", "expose accessible labels"):

1. **`Dialog`** — its own comment says *"focus trap-lite"*. It adds an Escape
   listener and focuses the first control, but there is **no focus trap** (Tab
   escapes to the page behind the modal), **no scroll lock**, **no focus
   restore** to the trigger on close, no portal, and it uses `aria-label`
   duplicating the visible heading instead of `aria-labelledby`. Tab escaping an
   open modal is a genuine WCAG 2.4.3 / 2.1.2 failure.
2. UX §39's `Tabs`, `Tooltip`, `Select`, `Dropdown`, `Table`, `Switch`,
   `Skeleton`, `CommandPalette` are absent — reinvented ad hoc where needed (a
   hand-rolled tab switcher on `/analytics`, native `<select>` everywhere).

M7 (OSCAR) adds a chat UI whose **confirmation-gated mutations** (Blueprint §12,
PRD §10) will lean on a correct confirm dialog, plus a model/tool dropdown and
tool-call tooltips. The weak primitives are exactly the ones M7 needs.

## Decision

### 1. The primitive layer is hand-built + Radix, not shadcn/ui

- **Keep hand-built, token-driven** primitives for everything that carries the
  visual identity or has no library equivalent: `Button`, `Card`, `Badge`,
  `Field` (Input/Textarea), `ProgressRing`, `CapabilityBar`, `WhyThis`,
  `States` (Empty/Error/Loading), `SkillWeightPicker`, chart containers, the
  eventual `Metric` / OSCAR panel.
- **Adopt [Radix UI](https://www.radix-ui.com/primitives) primitives**
  (`@radix-ui/react-*`) — the unstyled, accessible behaviour layer that shadcn
  is itself built on — for the interaction-heavy components where a11y
  correctness is hard to hand-roll: **`Dialog`**, **`DropdownMenu`**,
  **`Tooltip`**, **`Tabs`**. Each gets a **thin Deep-Space-styled wrapper** in
  `components/ui/` exposing the same small API the rest of the app already uses;
  Radix ships unstyled, so the visual system is unchanged.
- This is **not** shadcn/ui: no `components.json`, no code generation, no
  shadcn styling opinions, no `class-variance-authority`. Just the four Radix
  behaviour packages plus our own CSS.

### 2. What "not shadcn/ui" costs, accepted

- We own the styling and the small wrapper APIs (already true).
- Future primitives (`Table`, `Switch`, `Command`) are hand-built or add another
  Radix package when first needed — decided at that point, no blanket import.

### 3. The Radix pass is a scoped pre-M7 task

Its own task, ahead of M7: add the four packages, write the four wrappers,
migrate every `Dialog` call site and the `/analytics` tab switcher, add
`DropdownMenu` / `Tooltip` as available primitives. **Acceptance is an
accessibility check on the `Dialog`**, not a visual smoke test — focus is
trapped within the modal, the trigger regains focus on close, background scroll
is locked, `Escape` and the backdrop close it, an initial element is focused,
and it is labelled by its visible heading.

### 4. Docs

`ADR-006` gets a History note pointing here; Tech Arch §4 and the Blueprint are
authoritative on *Next.js / Tailwind / TanStack Query / Recharts* but this ADR
overrides them on the component library. `README.md` is corrected to the actual
stack.

## Alternatives Considered

1. **Migrate to shadcn/ui now.** Rejected. ~13 working (partly tested)
   primitives and ~25 call-site pages would churn; then every generated
   component needs restyling to the Orbital-Instrument system, fighting Radix's
   DOM and shadcn's class conventions. shadcn's value is (a) its styling
   opinions — which we don't want — and (b) Radix underneath — which we take
   directly. Days of work to end up with a worse fit for the design system.
2. **Stay fully hand-built, fix `Dialog`'s focus trap as a one-off.** Rejected
   as the primary path — a correct focus trap + scroll lock + focus restore +
   inert background is most of what `@radix-ui/react-dialog` *is*, and the same
   argument recurs for `DropdownMenu` / `Tooltip` / `Tabs`. Reinventing the
   accessible-interaction layer four times is the wrong trade.
3. **Headless UI instead of Radix.** Comparable, but Radix has the wider
   primitive set (DropdownMenu, Tooltip, Toast, Popover) M7+ will want, better
   TypeScript, and is the de-facto standard shadcn/Tailwind ecosystems build on.
4. **Full shadcn/ui as a post-MVP cleanup.** Left open — nothing here blocks a
   later migration, but the design-identity components would stay custom
   regardless, so the payoff stays small.

## Consequences

- **Positive.** The a11y-critical components (starting with the one M7 depends
  on) become WCAG-correct with a ~4-package, no-styling-change addition. The
  design system stays intact. The spec/ADR/README contradiction is resolved
  honestly. M7 builds its chat UI on solid Dialog/DropdownMenu/Tooltip/Tabs.
- **Negative.** Four new runtime dependencies (~small, tree-shakeable, widely
  used). The "which primitive is Radix vs hand-built" split is a thing
  contributors must learn — mitigated by the rule: *identity / no-equivalent →
  hand-built; focus-management / overlay / menu → Radix*.
- **Neutral.** `README.md` and an `ADR-006` History note change; no runtime or
  schema impact from the doc edits.

## Sign-off items

1. **Component library = hand-built token-driven primitives + Radix for
   `Dialog` / `DropdownMenu` / `Tooltip` / `Tabs`**, explicitly not shadcn/ui —
   overriding ADR-006 / Tech Arch §4 on this point only.
2. **The Radix pass is a scoped pre-M7 task**, with an accessibility-audit
   acceptance bar on the `Dialog` swap.
