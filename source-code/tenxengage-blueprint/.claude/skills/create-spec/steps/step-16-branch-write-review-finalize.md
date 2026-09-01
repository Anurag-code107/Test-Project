# Step 16: branch-write-review-finalize

## Goal
Branch setup, write spec.md and technical.md verbatim from the plan, auto-invoke /review-spec, finalize.

## Inputs (from prior steps)
- Approved plan file at the plan-mode-provided path
- Locked slug (step 12)
- Input mode flag (step 01)

## Loads (just-in-time)
- The plan file (read it; do NOT regenerate)

## Procedure

### 1. Branch setup

**Determine base branch:**
- Mode 1 (BRD identifier): base is `roadmaps/{slug}` (the same slug as the BRD, NOT the feature). Verify it exists:
  ```bash
  git ls-remote --exit-code origin roadmaps/{slug} || git show-ref --verify refs/heads/roadmaps/{slug}
  ```
  - Neither exists → ABORT. Tell user: "Roadmap branch `roadmaps/{slug}` not found locally or on origin. Run `/decompose-brd` first."
  - Found remotely but not locally: `git fetch origin roadmaps/{slug}:roadmaps/{slug}`
  - Store: `_b="roadmaps/{slug}"`
- Otherwise (Mode 2/3/4): `_b="${FEATURE_BASE_BRANCH:-main}"`

**Ensure feature branch exists** in blueprint (single compound command — do not split):
```bash
git checkout features/{feature-slug} 2>/dev/null || git fetch origin features/{feature-slug}:features/{feature-slug} 2>/dev/null && git checkout features/{feature-slug} || (echo "Creating from: $_b" && git checkout -b features/{feature-slug} "$_b")
```

**Sync from remote only if all guards pass:**
- Guard 1 (remote exists): `git ls-remote --exit-code origin features/{feature-slug}` non-zero exit → skip
- Guard 2 (no uncommitted changes): `git status --porcelain` non-empty → skip
- Guard 3 (no unpushed commits): `git log origin/features/{feature-slug}..HEAD --oneline` non-empty → skip
- All guards pass: `git pull --rebase origin features/{feature-slug}`

### 2. Read plan and write files

1. **Read the plan file** from step 15's path. Do NOT regenerate content.

2. **Create directory:** `features/{feature-slug}/`

3. **Write `features/{feature-slug}/spec.md`** — extract verbatim from the plan's `### File: .../spec.md` section.

4. **Write `features/{feature-slug}/technical.md`** — extract verbatim from `### File: .../technical.md` section.

5. **Update plan frontmatter:** set `filesWritten: ["spec.md", "technical.md"]`.

### 3. Auto-invoke /review-spec

Run `/review-spec {feature-slug}` to validate the spec across architectural dimensions.

### 4. After review completes

1. **Apply registry edits.** If the plan file includes a `## Registry edits` section, write each listed change to `docs/patterns/domains/...` files. Stage them so they ship in the same commit as `features/{slug}/spec.md`.

2. Update the spec frontmatter status:
   - APPROVED → `status: reviewed`
   - Unresolved issues → `status: draft`

3. **Do NOT auto-run /generate-contracts.** That's a separate manual step.

4. **Ask the user about commit and push.**

   ```bash
   # Mark start of human wait
   date +%s%3N > /tmp/create_spec_wait_started
   ```

   Show two options:
   - YES: `git add features/{feature-slug}/ docs/patterns/domains/ && git commit -m "feat: add spec for {feature-slug}" && git push`
   - NO: print the commands for them to run later.

   ```bash
   echo $(( $(cat /tmp/create_spec_wait) + ($(date +%s%3N) - $(cat /tmp/create_spec_wait_started)) )) > /tmp/create_spec_wait
   ```

   **On resume from any user-gate in this step, FIRST run the wait-accumulation command above before doing anything else.**

5. **Show next steps:**
   ```
   Spec created and reviewed. Next steps:

     1. /create-stories {feature-slug}   (in this repo, decomposes spec into stories)
     2. cd ../tenxengage-contracts && /generate-contracts {feature-slug}   (after stories)
     3. cd ../tenxengage-backend && /load-spec {feature-slug}   (foundation tasks)
     4. cd ../tenxengage-frontend && /load-spec {feature-slug}   (after contracts)
   ```

6. **Print wall time:**
   ```bash
   wall=$(( $(date +%s) - $(cat /tmp/create_spec_start) ))
   wait_s=$(( $(cat /tmp/create_spec_wait) / 1000 ))
   exec_s=$(( wall - wait_s ))
   echo "create-spec execution time: ${exec_s}s (wall=${wall}s, human-wait=${wait_s}s)"
   ```

## Rules (scoped to this step)
- File contents come from the PLAN — verbatim. Do not regenerate spec.md or technical.md.
- Auto-review fires automatically (no user gate). The review gate is the linchpin of the spec → stories pipeline.
- The contracts branch is NOT created here — `/generate-contracts` handles that.
- Do not push without explicit user approval.

## User interaction
- Commit/push prompt at the end.

## Output for downstream steps
None — terminal state.

## Boundary
Files written, review complete, user shown next steps → terminal state. Skill complete.