# CLAUDE.md — tenxengage-backend

## Purpose

Java 21 / Spring Boot 3.2.5 backend service for TenXEngage. PostgreSQL persistence, Kafka eventing, multi-tenant.

## How to build / test / run

```bash
# Start infrastructure
docker compose up -d

# Run the application
./gradlew bootRun

# Run tests
./gradlew test

# Build production JAR
./gradlew build

# Lint (checkstyle if configured)
./gradlew check
```

## Sibling repos (relative paths)

- `../tenxengage-blueprint/` — feature specs and contracts hub
- `../tenxengage-frontend/` — React frontend
- `../tenxengage-contracts/` — shared OpenAPI contracts
- `../tenxengage-admin-backend/` — admin backend service
- `../tenxengage-admin-frontend/` — admin frontend

## Critical session rule

Operate within this repo and its siblings only. Never read from or write to directories outside the parent of this repo.

## At session start

Read `PROJECT-CONTEXT.md` in full before responding to any request.

## Where to find skill input

Skills must read `PROJECT-CONTEXT.md` (backend-specific standards) and `../tenxengage-blueprint/docs/patterns/` for implementation patterns. Skills MUST NOT treat this CLAUDE.md as the source of conventions or architecture rules.

**Domain registry** (load-bearing for all builder-shaped feature work) lives at
`../tenxengage-blueprint/docs/patterns/domains/`. Read `INDEX.md` first; then the
relevant `{domain}.md` when working a slot-filling feature. Slot fillers that
differ from the registry must be flagged interactively by skills, not silently
accepted.
