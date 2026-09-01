# Step 1 — parse

### Inputs
- `$ARGUMENTS`

### Actions

1. **Extract feature slug.** Parse the first positional argument from `$ARGUMENTS`. If missing →
   STOP: `"Usage: /qa-explore <feature-slug> [--story=US-NN] [--page=/route] [--role=...] [--reuse-stack] [--cleanup] [--dry-run]"`

2. **Validate slug.** Check that `features/<slug>/` directory exists in the blueprint repo. If not →
   STOP: `"Feature directory not found: features/<slug>/. Run /create-stories first."`

3. **Parse flags.**
   - `--story=<id>`: extract story ID. Validate format matches `US-\d+`. Look for
     `features/<slug>/stories/<id>-*.md`. If file not found → STOP: `"Story file not found for <id> in features/<slug>/stories/"`.
   - `--page=<route>`: extract route. Validate it starts with `/`. If both `--story` and `--page`
     present → STOP: `"--story and --page are mutually exclusive."`.
   - `--role=<value>`: must be one of `admin`, `learner`, `seller`. Default: `admin`. Anything else →
     STOP: `"--role must be one of: admin, learner, seller"`.
   - `--reuse-stack`: boolean flag. Default: false.
   - `--cleanup`: boolean flag. Default: false. If set together with `--reuse-stack`, log a warning (`"--cleanup ignored: --reuse-stack means we started nothing"`) and treat as false.
   - `--dry-run`: boolean flag. Default: false.
   - `--from=<step>`: if set, record the re-entry step name and skip steps before it.

4. **Determine scope and read story files.**
   - Full feature: glob `features/<slug>/stories/US-*-*.md`, sorted by US number (US-01 first)
   - `--story=US-NN`: only `features/<slug>/stories/US-NN-*.md`
   - `--page=/route`: all story files (full glob) for test world extraction; route manifest will
     be restricted to `[PAGE_ROUTE]` in Step 3

   Read each scoped story file into memory.

5. **Read supporting context files.**
   - `features/<slug>/spec.md` — specifically the `## Data Model` section (for test world entity shapes
     and API endpoints)
   - `features/<slug>/test-plan.md` — to identify scenarios already covered by T1 (avoid exact duplication)

   If `spec.md` is missing → STOP: `"features/<slug>/spec.md not found. This skill requires a reviewed spec."`.

6. **Set scope variables in memory:**
   ```
   SLUG         = <feature-slug>
   SCOPE_MODE   = "full" | "story" | "page"
   STORY_ID     = <US-NN | null>
   PAGE_ROUTE   = </route | null>
   ROLE         = admin | learner | seller   (default: admin)
   REUSE_STACK  = true | false
   DRY_RUN      = true | false
   REENTRY_STEP = <step-name | null>
   ```

### Output of Step 1
- Scope variables set in memory
- Story files loaded in memory
- `spec.md` and `test-plan.md` loaded in memory
- No files written to disk in this step

---

## Boundary

Outputs of this step:
- Resolved `<slug>`
- Parsed flags: `--story`, `--page`, `--role`, `--reuse-stack`, `--cleanup`, `--dry-run`, `--from`
- Loaded story files list

Route to step 02: read `steps/step-02-health-check.md`.
