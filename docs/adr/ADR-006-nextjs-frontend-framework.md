# ADR-006: Next.js as Frontend Framework

## Status
Accepted

## Date
2026-09-05

## Context
The specification documents contain a conflict regarding the frontend framework:
- **PRD v1.0** specifies: React 18 + Vite + Zustand + React Router v6
- **Technical Architecture Specification** specifies: Next.js (App Router, Server Components) + shadcn/ui + TanStack Query
- **Engineering Blueprint** specifies: Next.js (App Router) + TypeScript + Tailwind CSS + shadcn/ui + TanStack Query

Per the Engineering Handoff precedence rules:
- Blueprint governs code structure and technology choices
- Technical Architecture Specification governs infrastructure decisions

Both the Blueprint and Architecture Spec agree on Next.js.

## Decision
We will use **Next.js** with the App Router as the frontend framework, aligning with the Engineering Blueprint and Technical Architecture Specification.

Stack:
- Next.js (App Router, Server Components by default)
- TypeScript
- Tailwind CSS
- shadcn/ui (component library)
- TanStack Query (server state management)
- Recharts (data visualization)
- Zod (runtime validation)
- Lucide React (icons)

The PRD's specification of Vite + Zustand + React Router is superseded by the more detailed and authoritative Blueprint and Architecture Spec.

## Alternatives Considered
1. **React + Vite (PRD spec)**: Not chosen because the Blueprint and Architecture Spec — which govern code structure per the Handoff precedence rules — both specify Next.js.

## Consequences
- **Positive**: Server Components for performance, built-in routing, SSR/SSG capabilities, excellent TypeScript support, shadcn/ui provides accessible, customizable components.
- **Negative**: Slightly heavier framework than Vite for a primarily client-side SPA. App Router is newer with evolving patterns. Next.js build times can be longer than Vite.
