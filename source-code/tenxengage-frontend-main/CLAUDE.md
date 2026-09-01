# CLAUDE.md — tenxengage-frontend

## Purpose

React 18 / TypeScript / Vite frontend for TenXEngage. Tailwind CSS + shadcn/ui components. TanStack Query for server state.

## How to build / test / run

```bash
npm run dev        # Dev server on port 3000
npm run build      # TypeScript check + Vite production build
npm run preview    # Preview production build
npm run lint       # ESLint
npm run lint:fix   # ESLint with auto-fix
npm run test       # Vitest run
npm run test:watch # Vitest watch mode
npm run format     # Prettier on src/**/*.{ts,tsx}
```

## Sibling repos (relative paths)

- `../tenxengage-blueprint/` — feature specs hub
- `../tenxengage-backend/` — Java backend
- `../tenxengage-contracts/` — shared types and contracts
- `../tenxengage-admin-backend/` — Java admin backend
- `../tenxengage-admin-frontend/` — admin UI

## Critical session rule

Operate within this repo and its siblings only. Never read from or write to directories outside the parent of this repo.

## At session start

Read `PROJECT-CONTEXT.md` in full before responding to any request.

## Where to find skill input

Skills must read this repo's `PROJECT-CONTEXT.md`, `../tenxengage-blueprint/PROJECT-CONTEXT.md`, and `../tenxengage-blueprint/docs/patterns/INDEX.md`. Skills MUST NOT read this CLAUDE.md for conventions or standards.

**Domain registry** (load-bearing for all builder-shaped feature work) lives at
`../tenxengage-blueprint/docs/patterns/domains/`. Read `INDEX.md` first; then the
relevant `{domain}.md` when working a slot-filling feature. Slot fillers that
differ from the registry must be flagged interactively by skills, not silently
accepted.
