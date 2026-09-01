### 2. Validate story + layer

**Blueprint branch guard (before reading any files):**
a. Check if `../tenxengage-blueprint/features/{feature-id}/` exists — if YES, blueprint is on the right branch; skip to (e).
b. `git -C ../tenxengage-blueprint branch --show-current` → note `{current-branch}`
c. Check for uncommitted changes: `git -C ../tenxengage-blueprint status --porcelain`
   - If changes exist, ask the user:
     ```
     Blueprint repo has uncommitted changes on branch {current-branch}.
     A) Commit them to {current-branch} (supply a commit message)
     B) Stash them
     C) Abort — I'll switch branches manually
     ```
     - On A: commit with the user-supplied message, then proceed to (d)
     - On B: `git -C ../tenxengage-blueprint stash`, then proceed to (d)
     - On C: **abort**
d. Checkout the feature branch:
   - Try local: `git -C ../tenxengage-blueprint checkout features/{feature-id}`
     - If successful: check if behind remote:
       `git -C ../tenxengage-blueprint log HEAD..origin/features/{feature-id} --oneline 2>/dev/null`
       - If behind: ask user: "Blueprint branch is behind origin by N commits. A) Pull with rebase  B) Continue with local version"
         - On A: `git -C ../tenxengage-blueprint pull --rebase origin features/{feature-id}`, then proceed to (e)
         - On B: branch already checked out — proceed to (e)
       - If not behind: proceed to (e)
     - If local branch not found: `git -C ../tenxengage-blueprint fetch origin features/{feature-id}:features/{feature-id} && git -C ../tenxengage-blueprint checkout features/{feature-id}`, then proceed to (e)
     - If remote also not found: **abort** — "Blueprint feature branch not found locally or on origin. Run `/create-spec` from the blueprint repo first."
e. Blueprint repo is now on `features/{feature-id}`. Continue reading story files.

1. Read `../tenxengage-blueprint/features/{feature-id}/stories/US-{NN}-*.md`
2. Parse frontmatter `layers` field
3. If `layers` does NOT include `"BE"` → **abort** with: "Story {US-NN} is FE-only (`layers: {value}`). Run this in the frontend repo: `cd ../tenxengage-frontend && /load-story {feature-id} {US-NN}`."
4. Capture: `id`, `title`, `touches_entities`, `depends_on_stories`

## Next step

Read `steps/step-03-claim-tracker.md`.
