# CLAUDE.md — tenxengage-blueprint

## Purpose

Central hub for TenXEngage feature specifications and API contracts. Source of truth for both frontend and backend teams.

## Sibling repos (relative paths)

- `../tenxengage-backend/` — Java 21, Spring Boot, PostgreSQL, Kafka
- `../tenxengage-frontend/` — React 18, TypeScript, Vite, Tailwind, shadcn/ui
- `../tenxengage-admin-backend/` — admin operations backend
- `../tenxengage-admin-frontend/` — admin UI
- `../tenxengage-contracts/` — shared OpenAPI contracts

## Skills

User-invocable skills are listed by Claude Code at session start. See `PROJECT-CONTEXT.md` for full skill descriptions and usage workflow.

## Spec statuses

- `draft` — created, not yet reviewed
- `reviewed` — passed /review-spec validation

## Critical session rule

Operate within this repo and its siblings only. Never read or write outside the parent directory of this repo.

## Where to find skill input

Skills must read `PROJECT-CONTEXT.md` (canonical platform standards), `docs/patterns/INDEX.md` (topic registry), and per-pattern files on demand. Skills MUST NOT read this CLAUDE.md — it is session bootstrap, not skill input.
