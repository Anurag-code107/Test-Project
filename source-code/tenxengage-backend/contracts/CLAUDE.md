# CLAUDE.md — tenxengage-contracts

## Purpose

OpenAPI contracts and shared models for TenXEngage. Single source of truth for endpoints, models, and enums consumed by backend and frontend.

## Structure

- `endpoints/*.yaml` — OpenAPI per-feature endpoint specs
- `models/*.md` — shared data model definitions
- `enums.md` — full enum definitions
- `PROJECT-CONTEXT.md` — API conventions (URL naming, error shapes, auth headers, permissions)

## Critical session rule

Operate within this repo and its siblings only.

## At session start

Read `PROJECT-CONTEXT.md` in full before responding to any request.


## Where to find skill input

Skills must read this repo's `PROJECT-CONTEXT.md` before generating or modifying any contracts. Skills MUST NOT rely solely on this CLAUDE.md.

**Domain registry** (load-bearing for all builder-shaped feature work) lives at
`../tenxengage-blueprint/docs/patterns/domains/`. Read `INDEX.md` first; then the
relevant `{domain}.md` when working a slot-filling feature. Slot fillers that
differ from the registry must be flagged interactively by skills, not silently
accepted.
