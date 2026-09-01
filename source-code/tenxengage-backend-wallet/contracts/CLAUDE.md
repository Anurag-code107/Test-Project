# TenXEngage — Shared Contracts

This repo is the **single source of truth** for all data models, API endpoints, and conventions shared between the backend and frontend teams.

## Purpose

Both the `tenxengage-backend` and `tenxengage-frontend` repos consume this as a git submodule at `contracts/`. Any model, type, DTO, entity, or API service created in either repo MUST match what is defined here.

## Structure

```
models/       — Data model definitions (one file per model)
endpoints/    — API endpoint definitions (one file per resource)
enums.md      — Shared enumerations
conventions.md — Naming, formatting, and structural rules
```

## Rules

- ALWAYS update this repo BEFORE implementing in backend or frontend
- NEVER create a model, entity, DTO, type, or endpoint that isn't defined here
- NEVER modify field names, types, or constraints without updating here first
- Every change requires a PR reviewed by at least one person from each team
- Reference `conventions.md` for all naming and formatting rules
- Reference `enums.md` for all shared enumerations — do not hardcode enum values in code

## Adding a New Model

1. Create `models/<model-name>.md` with the field table
2. Add any new enums to `enums.md`
3. Create or update `endpoints/<resource>.yaml` if the model has API endpoints
4. Commit and push — both teams pull the updated submodule

## Adding a New Endpoint

1. Verify the model exists in `models/` (create it first if not)
2. Create or update `endpoints/<resource>.yaml`
3. Follow the clean REST conventions in `conventions.md`
4. Commit and push
