# tenxengage-blueprint

Central hub for TenXEngage feature specifications, API contracts, and shared templates. Serves as the **single source of truth** for both frontend and backend teams.

## Structure

```
tenxengage-blueprint/
  templates/              # Spec, story, tracker, test-plan templates
  features/               # Feature specs and story files
    <slug>/
      spec.md             # Feature specification
      stories.md          # Story index + dependency graph
      stories/            # One US-NN-*.md per user story
      tasks/
        foundation.md     # Foundation tasks (enums, migrations, entities, permissions)
      tracker.md          # Session status tracker
      test-plan.md        # Cross-story integration tests
  .claude/skills/         # Claude Code skills for the development workflow
```

Contracts (`endpoints/*.yaml`, `models/*.md`) live in the sibling `../tenxengage-contracts/` repo.

## Workflow

1. **Create Spec**: `/create-spec "feature description"` — generates spec in plan mode
2. **Review Spec**: Auto-runs after spec approval (or `/review-spec <slug>` standalone)
3. **Create Stories**: `/create-stories <slug>` — decompose reviewed spec into foundation tasks, user stories, tracker, and test plan
4. **Generate Contracts**: `cd ../tenxengage-contracts && /generate-contracts <slug>` — run **before** any implementation
5. **Implement**:
   - **Backend foundation** (sequential): `/execute-foundation` in `tenxengage-backend/` — runs F1–F5 (enums → migration → entities → permissions → plumbing)
   - **User stories** (parallel where independent): `/load-story <slug> US-NN` in backend or frontend — one story per session; check `/next-eligible <slug>` in the blueprint repo to see what's unblocked
   - FE story sessions can start after contracts are generated (scaffold + mocks); wire to real BE as foundation completes
6. **Ready Check**: `/ready-check` in each repo before pushing

## Requirements

- All repos (tenxengage-backend, tenxengage-frontend, etc.) must be siblings in the same parent directory
- Feature branches use matching names: `features/<slug>` across repos (any branch name works — the skills will ask for the slug if they can't auto-detect it)

## Feature Naming

Slug-only kebab-case folder names, e.g. `quiz-engine`, `bulk-import`. `/create-spec` checks for slug collisions before creating the folder.
