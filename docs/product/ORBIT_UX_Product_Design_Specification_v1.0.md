# ORBIT — UX & Product Design Specification v1.0

**Product:** ORBIT — Personal Intelligence & Execution Platform  
**AI Assistant:** OSCAR — Operational Strategy & Career Assistant  
**Version:** 1.0  
**Status:** Canonical UX specification for MVP implementation  
**Design foundation:** Deep Space + Geist Sans/Mono + Orbital Instrument

---

## 1. Purpose

This specification translates the ORBIT product vision, technical architecture, engineering blueprint, and finalized visual direction into an implementation-ready UX system.

It defines:

- information architecture
- navigation
- screen inventory
- user journeys
- layout rules
- visual and interaction behavior
- component usage
- responsive behavior
- accessibility
- loading/empty/error states
- motion
- OSCAR interaction patterns
- MVP UX acceptance criteria

This document is intended to be consumed by designers, engineers, QA, and AI coding agents.

It is not a visual mood board. Where this document specifies behavior, layout, hierarchy, or interaction, implementation should follow it unless an approved ADR or product decision supersedes it.

---

# 2. Product UX North Star

## 2.1 Core principle

> **Don't track your life. Operate it.**

ORBIT should help the user answer:

> **Given where I am and where I want to go, what should I do next?**

The primary UX loop is:

**Objective → Roadmap → Skill Gap → Project → Task → Execution → Evidence → Measurement → Recommendation**

The interface must continuously make this loop understandable.

## 2.2 UX principles

### 1. Action over tracking

ORBIT should not become a passive data-entry application.

Every major view should expose an actionable next step.

### 2. Evidence over claims

Progress should increasingly be backed by evidence: completed work, projects, assessments, metrics, or integrations.

### 3. Dependencies matter

A skill, task, or objective should be understood in relation to what it unlocks or blocks.

### 4. One skill, multiple outcomes

The same skill may contribute to several roadmaps, projects, and career targets.

### 5. Explain recommendations

When ORBIT or OSCAR recommends an action, the user should be able to understand why.

### 6. User remains in control

AI recommendations do not silently change objectives, priorities, plans, permissions, or external systems.

### 7. Progressive disclosure

Show the information needed for the current decision first. Detailed evidence, dependencies, calculations, and history remain one level deeper.

### 8. Calm precision

The UI should feel precise, technical, and confident without becoming visually noisy or "sci-fi."

---

# 3. Canonical Design System

## 3.1 Colour

### Dark theme — Deep Space

| Token | Value | Meaning |
|---|---|---|
| Background | `#0B0C10` | Application canvas |
| Surface | `#14151B` | Cards and panels |
| Surface Raised | `#1B1D25` | Dialogs, popovers, elevated surfaces |
| Border | `#2A2C36` | Hairline separation |
| Text Primary | `#E9E9EE` | Primary content |
| Text Secondary | `#8B8D98` | Supporting information |
| Accent Violet | `#8B7CF6` | Context, categories, selected states |
| Accent Cyan | `#4FD1E5` | Primary action, live/active state |
| Success | `#3DD68C` | Healthy/completed/positive |
| Warning | `#F5A623` | Attention/caution |
| Danger | `#F2545B` | Error/critical |

### Light theme

| Token | Value |
|---|---|
| Background | `#F5F5F7` |
| Surface | `#FFFFFF` |
| Surface Raised | `#FBFBFC` |
| Border | `#DEDFE3` |
| Text Primary | `#16171C` |
| Text Secondary | `#6B6C76` |
| Accent Violet | `#6D5FE0` |
| Accent Cyan | `#0EA5B7` |
| Success | `#1E9E63` |
| Warning | `#C97A0A` |
| Danger | `#D93B44` |

### Semantic color rules

- Cyan means **now/live/action**.
- Violet means **context/category/structure**.
- Green means **success/healthy**.
- Amber means **attention**.
- Red means **failure/critical**.
- Color must never be the only signal for state.

All colors must be represented as semantic design tokens. Components must not hard-code palette values.

---

# 4. Typography

## 4.1 Font families

**Primary:** Geist Sans  
**Technical/data:** Geist Mono

### Geist Sans

Use for:

- navigation
- headings
- body copy
- buttons
- forms
- labels
- cards

### Geist Mono

Use selectively for:

- timestamps
- percentages
- counters
- system status
- IDs
- logs
- sync information
- SLO/error-budget values
- technical metadata
- AI tool/activity metadata

Do not use monospace for large blocks of ordinary product copy.

## 4.2 Type scale

| Role | Size | Weight |
|---|---:|---|
| Display | 48–56px | Medium |
| Page H1 | 24–28px | Semibold |
| H2 | 18–20px | Semibold |
| Body | 14px | Regular |
| Small | 13px | Regular |
| Metadata | 12px minimum | Mono where appropriate |

Display typography may scale down to approximately 36px on mobile.

---

# 5. Spacing & Layout System

## 5.1 Base spacing grid

ORBIT uses a 4px base unit.

Canonical spacing:

`4 / 8 / 12 / 16 / 24 / 32 / 48 / 64`

Nothing meaningful should use arbitrary spacing values.

## 5.2 Component spacing

- Icon ↔ text: 8px
- Dense card padding: 16px
- Primary dashboard card padding: 24px
- Component internal spacing: 8–16px
- Section spacing: 32–48px
- Major page separation: 48–64px

A clear 2–3× visual jump should exist between internal component spacing and section spacing.

## 5.3 Grid

Desktop:

- 12-column grid
- 24px gutter
- responsive outer margins
- maximum readable content width on wide displays

Dashboards should use a small number of clearly differentiated zones instead of a uniform collection of equal tiles.

Canonical hierarchy:

1. Hero / primary metric
2. Secondary metrics
3. Detail / chart / action area

---

# 6. Breakpoints & Responsive Behavior

Canonical breakpoints:

- ~640px: mobile
- ~1024px: tablet
- ~1440px: desktop
- >1440px: wide desktop treatment

Wide displays should use a max-content-width container so content does not stretch unnecessarily.

## Mobile

- Single vertical content stack
- Hero metric first
- Primary action visible without excessive scrolling
- Radial progress remains supported
- Dense charts simplify rather than shrink
- Display numbers scale down
- Primary navigation becomes a bottom navigation bar
- Secondary navigation moves into a drawer

## Tablet

- Sidebar remains available where space permits
- Reduce simultaneous dashboard columns
- Preserve hierarchy rather than compressing every component

## Desktop

- Persistent sidebar
- Multi-zone dashboards
- Dense but readable information presentation

---

# 7. Navigation Architecture

## 7.1 Primary navigation

Desktop/tablet:

```text
ORBIT
────────────────
Command Center
Objectives
Career
Roadmaps
Skills
Projects
Evidence

────────────────
Today
Analytics
OSCAR

────────────────
Settings
```

The active route uses the violet selected-state treatment with restrained emphasis.

## 7.2 Mobile navigation

Bottom navigation prioritizes the most frequently accessed views:

`Today | Career | Projects | More`

The exact four destinations may be adjusted after MVP usability testing, but Today must remain immediately accessible.

Secondary navigation is provided through the More/drawer experience.

## 7.3 Global controls

Top application chrome may contain:

- global search / command palette
- notifications or attention indicator
- OSCAR entry point
- user/profile menu

Do not overload the top bar with persistent controls.

---

# 8. Global UX Patterns

## 8.1 Buttons

**Primary**
- Solid cyan
- Dark text
- 8px radius

Used for the primary action of a context.

**Secondary**
- Transparent
- 1px border
- Primary text
- Solid/lightened treatment on hover

**Ghost**
- Text only
- Cyan hover state

There should normally be one visually dominant primary action per major surface.

## 8.2 Cards

Default:

- Surface fill
- 1px border
- 12px radius
- No resting shadow

Hover/focus:

- Border shifts subtly toward cyan
- No layout movement
- No exaggerated glow

## 8.3 Inputs

- Surface fill
- 1px border
- 8px radius
- Visible cyan focus treatment
- Validation feedback adjacent to the relevant field

## 8.4 Tables

Use tables when comparison across multiple records is more useful than cards.

Requirements:

- sticky header where useful
- readable row height
- clear hover state
- keyboard accessibility
- responsive transformation on mobile

## 8.5 Dialogs

Use dialogs for:

- confirmation
- focused editing
- destructive actions
- detailed secondary information

Do not use dialogs for ordinary navigation.

---

# 9. Status & Feedback

Every important state should be represented through at least two channels.

Examples:

- Success: green + check
- Warning: amber + alert icon
- Critical: red + critical icon
- Positive delta: ▲ + green
- Negative delta: ▼ + red
- Live: cyan + pulsing/static dot

Never rely solely on color.

---

# 10. Motion System

## Durations

- Hover/focus/state change: 120–180ms
- Panel/modal transitions: 200–300ms
- Data/progress transitions: 400–600ms

## Easing

- Enter: ease-out
- Exit: ease-in
- No bounce or spring easing

## Signature motion

**ORBIT mark**

The incomplete ring may complete during initialization or synchronization.

**OSCAR**

The ring may rotate gently while processing and pulse once when a response becomes available.

**Live state**

A small cyan dot may softly pulse.

**Progress**

Radial progress animates to the new value over approximately 400–600ms.

## Reduced motion

When `prefers-reduced-motion` is enabled:

- looping animations become static
- ring rotation stops
- live pulse becomes a static indicator
- transitions may use a short fade or no motion
- data changes remain understandable without animation

---

# 11. Iconography

Use one coherent icon family throughout the application.

Icons should be:

- simple
- outline-oriented
- visually restrained
- consistent in stroke weight
- never used as decoration without semantic purpose

Icons should support labels rather than replace important text on desktop.

---

# 12. Brand Identity

## 12.1 ORBIT mark

Canonical mark: **Incomplete orbital ring**

The mark is an incomplete ring that can visually complete during synchronization or load.

It should be:

- outline based
- simple at favicon size
- compatible with both themes
- visually related to radial progress components

## 12.2 Wordmark

**O R B I T**

Use Geist Sans with generous tracking.

## 12.3 OSCAR identity

OSCAR does not have a cartoon mascot.

OSCAR uses the same orbital ring language.

States:

- Idle: static ring
- Thinking: gentle rotation
- Responding: single pulse
- Action requiring confirmation: highlighted but static
- Error/unavailable: danger state + explanatory text

---

# 13. Information Architecture

```text
ORBIT
│
├── Command Center
│
├── Objectives
│   ├── Objective detail
│   ├── Milestones
│   └── Tasks
│
├── Career
│   ├── Career overview
│   ├── Career target
│   ├── Skill gaps
│   └── Readiness
│
├── Roadmaps
│   ├── Roadmap library
│   ├── Roadmap detail
│   └── Roadmap Intersection
│
├── Skills
│   ├── Skill graph
│   ├── Skill detail
│   ├── Assessments
│   └── Evidence
│
├── Projects
│   ├── Project list
│   ├── Project detail
│   └── Project evidence
│
├── Evidence
│   ├── Evidence library
│   └── Evidence detail
│
├── Today
│   ├── Daily plan
│   ├── Tasks
│   └── Execution
│
├── Analytics
│   ├── Execution
│   ├── Career
│   ├── Skills
│   └── Balance
│
├── OSCAR
│   ├── Conversation
│   ├── Recommendations
│   └── Activity / actions
│
├── Balance
│
├── SRE
│
└── Settings
```

Balance and SRE can remain secondary/post-MVP navigation items until their MVP surfaces are defined.

---

# 14. Screen Inventory

## MVP screens

- Authentication / onboarding
- Command Center
- Today
- Objectives
- Objective detail
- Career overview
- Career target detail
- Roadmap library
- Roadmap detail
- Roadmap Intersection
- Skills / Skill Graph
- Skill detail
- Projects
- Project detail
- Evidence library
- Analytics
- OSCAR
- Settings

The MVP should avoid creating dozens of separate screens when a focused detail panel can accomplish the same task.

---

# 15. Authentication & Onboarding

## 15.1 Authentication

Preferred UX:

- OIDC/OAuth sign-in
- clear authentication state
- no unnecessary credential collection
- secure session behavior handled by the application architecture

## 15.2 First-run onboarding

Onboarding should establish the minimum viable context required for ORBIT to become useful.

Sequence:

```text
Welcome
↓
Primary career direction
↓
Career target
↓
Initial objectives
↓
Relevant roadmaps
↓
Current skill confidence
↓
Available weekly time
↓
Command Center
```

Do not force users to populate every possible module before seeing value.

## 15.3 Onboarding principle

The user should reach a meaningful Command Center quickly.

---

# 16. Command Center

The Command Center is the primary ORBIT surface.

Its job is not to show everything.

Its job is to answer:

**Where am I, what matters, and what should I do next?**

## Layout

```text
┌─────────────────────────────────────────────────────────────┐
│ Greeting / current trajectory                    OSCAR      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ PRIMARY TRAJECTORY                                          │
│                                                             │
│        [ Career Readiness Radial ]                          │
│                                                             │
├──────────────────────┬──────────────────────┬───────────────┤
│ Objective Health     │ Skill Health         │ Execution      │
│      metric          │      metric          │     metric     │
├──────────────────────┴──────────────────────┴───────────────┤
│                                                             │
│ NEXT BEST ACTION                                            │
│                                                             │
│  Complete Kubernetes networking lab                         │
│  Why: closes a dependency affecting 3 target skills         │
│                                                             │
│  [ Start Task ]                         [ Why this? ]        │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│ Progress / trends / attention items                         │
└─────────────────────────────────────────────────────────────┘
```

## Command Center content hierarchy

1. Current trajectory
2. Next best action
3. Objective health
4. Skill health
5. Execution health
6. Important trends
7. Attention items

Do not surface every available metric.

---

# 17. Today / Daily Execution

Today is the most action-oriented view.

**Primary question:** What should I execute today?

## Layout

```text
TODAY
──────────────────────────────────────

DATE / DAILY STATUS

NEXT BEST ACTION
[ Large primary task ]

TODAY'S PLAN
□ Task
□ Task
□ Task
□ Task

EXECUTION
Focused time
Completed
Remaining

OSCAR
"Your highest-impact action is..."
```

## Task interaction

Task states:

- Not started
- In progress
- Blocked
- Completed
- Skipped

Completion should be fast and require minimal navigation.

## Task detail

Task detail may expose:

- objective
- milestone
- skill(s)
- project
- evidence expected
- priority rationale
- estimated effort
- dependencies

---

# 18. Objectives

Objectives represent outcomes, not merely activities.

## Objective list

Each objective should show:

- name
- status
- progress
- deadline if applicable
- associated career target
- milestone health
- next action

## Objective detail

Hierarchy:

```text
Objective
│
├── Outcome
├── Progress
├── Health
├── Milestones
│   └── Tasks
├── Related skills
├── Related projects
└── Evidence
```

The objective detail page should make blockers visible.

---

# 19. Career Engine

The Career surface answers:

**How prepared am I for my target career, and what is missing?**

## Career overview

Primary components:

- target role
- readiness score
- top skill gaps
- highest-leverage skills
- evidence coverage
- active projects
- next career action

## Career readiness

A score is an indicator, not the objective.

Show:

- overall readiness
- component breakdown
- evidence confidence
- trend
- explanation

Never imply false precision.

Example:

```text
CAREER READINESS

      72
   /──────\
  /        \
  \        /
   \──────/

+8 vs last month

Primary gaps:
1. Kubernetes networking
2. Distributed systems
3. Production observability
```

---

# 20. Roadmaps

Roadmaps should not behave like separate courses.

ORBIT normalizes:

**Roadmap → Topic → Skill → Dependency → Evidence**

## Roadmap library

Show:

- roadmap name
- coverage
- relevant skills
- overlap with other roadmaps
- completion/progress

## Roadmap detail

Show:

- topics
- skills
- dependencies
- current proficiency
- evidence
- related projects
- recommended next actions

## Roadmap Intersection

This is a signature ORBIT experience.

It answers:

**Which skills appear across my selected roadmaps?**

Example:

```text
                 MLOps
                  │
          ┌───────┼────────┐
          │       │        │
       Docker   Linux   Kubernetes
          │       │        │
          └───────┼────────┘
                  │
              DevOps
                  │
              SRE Path
```

The UX should make overlap visually obvious.

Avoid presenting large copied blocks of third-party roadmap prose.

---

# 21. Skill Intelligence

## Skill Graph

The Skill Graph is a central ORBIT visualization.

A skill may connect to:

- career targets
- roadmaps
- projects
- evidence
- dependencies
- assessments

## Skill detail

Show:

- current score
- confidence
- knowledge
- practical ability
- interview readiness
- production evidence
- dependencies
- dependents
- related projects
- evidence
- recommended action

## Skill score

Use the established scoring model:

`0.20 knowledge + 0.25 practical + 0.20 evidence + 0.15 interview + 0.20 production`

The UI must show the dimensions behind the score.

Do not present the aggregate score as objective truth.

---

# 22. Projects

Projects are execution vehicles for building evidence.

## Project list

Show:

- status
- progress
- linked objectives
- skills developed
- evidence count
- next milestone

## Project detail

```text
PROJECT
│
├── Goal
├── Status
├── Milestones
├── Tasks
├── Skills
├── Evidence
├── Metrics
└── Activity
```

A project should make the relationship between work and career value explicit.

---

# 23. Evidence

Evidence is one of ORBIT's core differentiators.

## Evidence types

Potential examples:

- completed project
- GitHub contribution
- assessment result
- deployed service
- benchmark
- technical document
- certification
- interview performance
- measurable project outcome

## Evidence library

Each evidence item shows:

- type
- linked skill
- linked project
- date
- confidence/verification status
- source

Evidence should be searchable and filterable.

---

# 24. Analytics

Analytics should answer questions, not merely display charts.

## Core analytics views

**Execution**
- planned vs completed
- completion probability
- focused time
- recurring blockers
- execution trend

**Career**
- readiness trend
- skill gap closure
- evidence growth
- target progression

**Skills**
- skill progression
- neglected skills
- high-leverage skills
- dependency bottlenecks

## Chart rules

Use:

- radial charts for "how far along"
- line charts for change over time
- bars for comparisons

Primary series: cyan.

Secondary comparison: violet.

Supporting data: neutral.

Gridlines should be subtle and secondary.

---

# 25. OSCAR

OSCAR is the intelligence layer of ORBIT.

It should feel operational, not conversational for its own sake.

## 25.1 OSCAR entry points

OSCAR can appear:

- in global navigation
- in Command Center
- on Today
- in relevant detail views
- through contextual recommendation cards

## 25.2 OSCAR response structure

Prefer:

```text
RECOMMENDATION

[Action]

Why this?

[Reasoning]

Impact

[Expected outcome]

Evidence

[Relevant supporting data]

[ Start ] [ Explain ] [ Dismiss ]
```

## 25.3 OSCAR recommendations

Recommendations should distinguish:

- observed facts
- calculated metrics
- inference
- recommendation

## 25.4 Consequential actions

Actions that modify state require confirmation by default.

Examples:

- changing objectives
- reprioritizing work
- modifying a daily plan
- changing integrations
- external actions

OSCAR must never execute arbitrary shell commands, database mutations, infrastructure operations, permission changes, or unrestricted external actions.

## 25.5 AI unavailable state

ORBIT must remain functional if the AI provider is unavailable.

Example:

```text
OSCAR IS TEMPORARILY UNAVAILABLE

Your tasks, objectives, roadmaps and analytics remain available.

[ Continue without OSCAR ]
```

---

# 26. Loading States

Loading states should preserve layout.

Use:

- skeletons for content-heavy panels
- inline spinners only for short operations
- no full-page spinner for ordinary navigation

Skeletons should reflect the approximate shape of the eventual content.

Avoid animated skeletons when reduced motion is requested.

---

# 27. Empty States

Empty states must be actionable.

Bad:

```text
No projects.
```

Good:

```text
NO ACTIVE PROJECTS

Projects turn skills into evidence.

Create your first project to start connecting
execution with your career trajectory.

[ Create Project ]
```

Every empty state should explain:

- what is missing
- why it matters
- what the user can do next

---

# 28. Error States

Errors should be:

- specific
- understandable
- actionable
- non-destructive

Example:

```text
COULDN'T LOAD ROADMAPS

ORBIT couldn't retrieve your roadmap data.

[ Retry ]

If this continues, check your connection or
try again later.
```

Never expose raw stack traces to normal users.

Technical details may be available through a diagnostic view/logging system.

---

# 29. Confirmation Patterns

Confirmation should be used for consequential or destructive actions.

Avoid confirmation for:

- checking a task
- changing a harmless filter
- navigating

Use confirmation for:

- deleting evidence
- deleting projects/objectives
- changing significant career configuration
- AI-generated mutations
- external integrations/actions

Confirmation dialogs should state the consequence plainly.

---

# 30. Search & Command Palette

A global command/search interface is recommended.

It should eventually support:

- navigation
- objectives
- tasks
- projects
- skills
- roadmaps
- evidence
- OSCAR prompts/actions

Example:

```text
Search ORBIT...

> Kubernetes
  Skill
  3 related tasks
  2 projects
  4 evidence items

> Create task
> Open Career
> Ask OSCAR
```

Keyboard access should be supported on desktop.

---

# 31. Accessibility Specification

ORBIT targets WCAG AA.

Requirements:

- 4.5:1 minimum contrast for body text/data labels
- visible focus state on every interactive element
- keyboard navigation
- semantic HTML
- accessible names for controls
- color never as sole state indicator
- reduced motion support
- 40–44px minimum interactive hit area
- meaningful content should not fall below 12px
- layout must tolerate browser zoom and text scaling
- charts require accessible summaries or equivalent textual data
- status changes should be perceivable without relying solely on animation

---

# 32. Mobile UX Rules

Mobile is not a compressed desktop layout.

Priority:

```text
Today
↓
Next Action
↓
Objective
↓
OSCAR
↓
Progress
↓
Secondary detail
```

Mobile should optimize for quick status checks and action completion.

Avoid:

- horizontally scrolling primary content
- tiny chart labels
- oversized navigation
- excessive nested drawers
- multi-column dense dashboards

---

# 33. UX for SRE Personal Operating Layer

The SRE layer should use the same visual language while introducing operational concepts.

Potential surfaces:

- personal SLO
- error budget
- incident
- reliability trend
- recurring failure
- recovery

Do not make SRE terminology dominate ordinary daily use.

The user should be able to understand the personal operating metrics without needing an SRE background.

---

# 34. UX for Balance

Balance eventually connects:

- fitness
- finance
- habits
- cricket
- other personal dimensions

The Balance UX must not turn every personal activity into an optimization score.

Use:

- trends
- status
- context
- goals
- recommendations

Avoid gamification that pressures the user to maximize every metric.

---

# 35. Personalization

ORBIT should personalize:

- recommended actions
- dashboard emphasis
- career relevance
- available-time assumptions
- roadmap relevance
- notification intensity

Personalization must not silently change core goals.

Users should be able to inspect and change important assumptions.

---

# 36. Notifications

Notifications should be attention-worthy, not constant.

Prioritize:

- blockers
- deadlines
- meaningful deviations
- important recommendations
- system/security events

Do not notify the user simply because a metric changed slightly.

---

# 37. Privacy UX

The UI should make data ownership understandable.

Settings should eventually provide:

- connected integrations
- permissions
- AI/data controls
- export
- deletion
- session/security controls

AI should only receive the minimum context necessary for an operation.

---

# 38. Core User Journeys

## Journey A — Morning check

```text
Open ORBIT
↓
Command Center / Today
↓
See trajectory
↓
See next best action
↓
Start task
↓
Execute
↓
Complete
↓
Evidence/metric updated
```

Target: meaningful action within seconds, not minutes.

## Journey B — Identify career gap

```text
Career
↓
Target role
↓
Readiness
↓
Skill gap
↓
Skill detail
↓
Dependency
↓
Recommended project/task
```

## Journey C — Convert learning into evidence

```text
Roadmap
↓
Skill
↓
Gap
↓
Project
↓
Task
↓
Execution
↓
Evidence
↓
Skill confidence increases
```

## Journey D — Ask OSCAR

```text
OSCAR
↓
Question
↓
Context assembled
↓
Recommendation
↓
Why?
↓
Optional confirmation
↓
Action
```

## Journey E — Weekly review

```text
Analytics
↓
Weekly Review
↓
Completed vs planned
↓
Skill progress
↓
Objective health
↓
Blockers
↓
OSCAR recommendations
↓
Next-week plan
```

---

# 39. Design System Implementation Rules

The frontend should expose reusable primitives for:

- Button
- Input
- Select
- Checkbox
- Switch
- Tabs
- Card
- Badge
- Tooltip
- Dialog
- Dropdown
- Table
- Progress
- RadialProgress
- Metric
- StatusIndicator
- EmptyState
- ErrorState
- Skeleton
- Timeline
- CommandPalette
- Navigation
- Chart containers
- OSCAR panel

Components must:

- use semantic tokens
- support keyboard navigation
- support light/dark themes
- support disabled/loading states
- avoid one-off styling
- expose accessible labels
- respect spacing tokens

---

# 40. Screen-Level Acceptance Criteria

A screen is UX-complete only when:

- hierarchy is clear without explanation
- primary action is obvious
- loading state exists
- empty state exists where applicable
- error state exists where applicable
- keyboard/focus behavior is defined
- responsive behavior is defined
- mobile hierarchy is defined
- accessibility requirements are satisfied
- destructive/consequential actions have confirmation
- colors have semantic meaning
- no arbitrary spacing values are introduced
- components use the design system

---

# 41. MVP UX Acceptance Criteria

ORBIT MVP must allow the user to:

- authenticate
- establish a career target
- create an objective
- create milestones
- create and complete tasks
- see a useful Today view
- see a useful Command Center
- associate skills with career targets
- inspect skill gaps
- ingest and navigate normalized roadmaps
- understand roadmap overlap
- create projects
- connect projects to skills/objectives
- create evidence
- see evidence relationships
- inspect career readiness
- inspect execution analytics
- receive OSCAR recommendations
- understand why a recommendation was made
- confirm consequential AI actions
- continue operating if AI is unavailable
- use the product on mobile and desktop
- switch between dark and light themes
- navigate using keyboard on supported desktop flows

---

# 42. UX Quality Bar

ORBIT should feel:

**Precise**
- clear hierarchy
- deliberate spacing
- consistent alignment

**Technical**
- restrained typography
- useful data density
- strong metadata treatment

**Intelligent**
- context-aware recommendations
- visible reasoning
- relationships between entities

**Calm**
- minimal decorative noise
- restrained motion
- no notification overload

**Operational**
- clear next action
- clear status
- visible blockers
- fast execution

It should not feel:

- gamified
- childish
- overly futuristic
- neon-heavy
- cluttered
- like a generic AI chatbot
- like a collection of disconnected productivity tools

---

# 43. Design Decisions Frozen in v1.0

The following are canonical unless explicitly changed through a product/design decision:

- Deep Space palette
- Cyan = action/live
- Violet = context/category
- Geist Sans + Geist Mono
- Orbital Instrument visual language
- Incomplete-ring ORBIT mark
- OSCAR uses the orbital ring identity
- 4px spacing grid
- 12-column desktop grid
- 24px desktop gutter
- 12px cards
- 8px controls
- borders over resting shadows
- restrained motion
- no bounce/spring/parallax
- WCAG AA target
- 40–44px minimum interactive hit area
- desktop sidebar
- mobile bottom navigation + secondary drawer
- dark mode as canonical visual identity
- semantic design tokens
- action-first dashboard hierarchy
- radial visualization for progress/status
- conventional charts for trends/comparisons
- color never as the sole status signal

---

# 44. Relationship to Engineering Specifications

This UX specification complements:

- ORBIT PRD
- ORBIT Technical Architecture Specification
- ORBIT Engineering Blueprint
- ORBIT Engineering Handoff

The UX specification does not replace backend contracts, database design, security architecture, or infrastructure requirements.

Where UX and technical architecture intersect, implementation must preserve both the user-facing behavior and the underlying architectural constraints.

---

# 45. Implementation Guidance for Antigravity

Before implementing any screen:

1. Read the PRD.
2. Read the Technical Architecture Specification.
3. Read the Engineering Blueprint.
4. Read this UX specification.
5. Identify relevant design tokens/components.
6. Implement using reusable components.
7. Implement loading, empty, error, and success states.
8. Implement responsive behavior.
9. Verify keyboard accessibility.
10. Verify light/dark themes.
11. Test the primary user journey.
12. Do not invent a competing visual language.

When a design decision is ambiguous, prefer:

**clarity → consistency → accessibility → reuse → visual flourish**

Do not introduce new colors, typography, spacing scales, component radii, or animation patterns without an explicit design decision.

---

# 46. Definition of UX Done

A feature is UX-complete when:

- its user goal is defined
- its entry point is defined
- its primary flow works
- its information hierarchy is clear
- its loading state exists
- its empty state exists where relevant
- its error state exists
- its success/completion state exists
- its responsive behavior is defined
- its accessibility behavior is defined
- its motion behavior is defined
- its design tokens are defined or reused
- its components are reusable
- its destructive/consequential interactions are protected
- its analytics/event requirements are understood where applicable
- it has been validated through implementation and testing

---

# 47. Final UX Principle

ORBIT should never make the user ask:

**"What am I supposed to do with all this information?"**

The interface should progressively answer:

- Where am I?
- What matters?
- What's blocking me?
- What should I do next?
- Why does it matter?
- What evidence will prove I did it?

That is the UX expression of ORBIT's core philosophy:

**Don't track your life. Operate it.**
