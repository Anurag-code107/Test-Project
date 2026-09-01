---
name: "load-spec"
description: "Set up the feature branch, sync the contracts submodule, and load the spec into context. Auto-detects from branch name or accepts a feature ID as argument."
argument-hint: "Optional: feature slug (e.g., quiz-engine). Auto-detects from branch if omitted."
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

---

## Steps

1. **Determine feature ID**:
   - If user provided one, use it
   - Otherwise: run `git branch --show-current`
     - If branch matches `features/*`: extract the slug (e.g., `features/quiz-engine` → `quiz-engine`)
     - Otherwise: ask the user for the feature ID

2. **Branch setup**:
   - Resolve base branch: run `echo "${FEATURE_BASE_BRANCH:-main}"` → store as `$BASE_BRANCH`
   - Check current branch: `git branch --show-current`
   - If NOT already on `features/{feature-id}`:
     - If branch exists locally (`git branch --list features/{feature-id}`): `git checkout features/{feature-id}`
     - If not locally: `git fetch origin features/{feature-id}:features/{feature-id} 2>/dev/null && git checkout features/{feature-id}` — if fetch fails (remote branch also doesn't exist): `git checkout -b features/{feature-id} $BASE_BRANCH`
   - Sync from remote only if all three guards pass:
     - Guard 1 (remote exists): `git ls-remote --exit-code origin features/{feature-id}` → non-zero exit: skip entirely
     - Guard 2 (no uncommitted changes): `git status --porcelain` → non-empty: skip, print "Skipping remote sync — uncommitted changes present"
     - Guard 3 (no unpushed commits): `git log origin/features/{feature-id}..HEAD --oneline` → non-empty: skip, print "Skipping remote sync — unpushed commits present"
     - All guards pass: `git pull --rebase origin features/{feature-id}`
   - Sync contracts repo to feature branch:
     - `git -C ../tenxengage-contracts checkout features/{feature-id} 2>/dev/null || (git -C ../tenxengage-contracts fetch origin && git -C ../tenxengage-contracts checkout features/{feature-id} 2>/dev/null) || true`
     - (try local branch first; fetch from origin only if not found locally; fall back silently — step 4 will note if not found)
   - Report: "On branch features/{feature-id}, contracts repo synced"

2b. **Blueprint branch guard**:
   - If `../tenxengage-blueprint/features/{feature-id}/` already exists → blueprint is on the right branch; skip to step 3.
   - `git -C ../tenxengage-blueprint branch --show-current` → note `{blueprint-branch}`
   - Check for uncommitted changes: `git -C ../tenxengage-blueprint status --porcelain`
     - If changes exist, ask:
       ```
       Blueprint repo has uncommitted changes on {blueprint-branch}.
       A) Stash them and switch
       B) Abort — I'll switch branches manually
       ```
       - On A: `git -C ../tenxengage-blueprint stash`, then continue
       - On B: **abort**
   - Try local branch: `git -C ../tenxengage-blueprint checkout features/{feature-id}`
     - If not found locally: `git -C ../tenxengage-blueprint fetch origin features/{feature-id}:features/{feature-id} && git -C ../tenxengage-blueprint checkout features/{feature-id}`
     - If still not found: note "Blueprint feature branch not found — spec may not exist yet." Continue to step 3.
   - Check if behind remote: `git -C ../tenxengage-blueprint log HEAD..origin/features/{feature-id} --oneline 2>/dev/null`
     - If behind: `git -C ../tenxengage-blueprint pull --rebase origin features/{feature-id}`

3. **Read the spec**: `../tenxengage-blueprint/features/{feature-id}/spec.md`
   - If not found: error "No spec at ../tenxengage-blueprint/features/<slug>/spec.md — pass a valid slug or run /create-spec first."

3a. **Read domain registry** (only if the spec frontmatter has `domain:` non-null):
   - Read `../tenxengage-blueprint/docs/patterns/domains/INDEX.md` (slot list + drift policy).
   - Read `../tenxengage-blueprint/docs/patterns/domains/{domain}.md` (slot fillers + section keys).
   - If `builder_type:` is set AND `../tenxengage-blueprint/docs/patterns/domains/{domain}/{builder-type}.md` exists, read it too — it overrides slot fillers from the domain file.
   - These slot fillers (entity names, hook names, type names) are load-bearing for any code this session writes. Use them verbatim — do not invent or rename.

3b. **Read the technical reference**: `../tenxengage-blueprint/features/{feature-id}/technical.md`
   - Contains: FE file paths (as a directory tree), hook specs (query keys, staleTime, mutation invalidation), BE file paths for cross-reference
   - If not found: note this — `technical.md` is generated alongside `spec.md` by `/create-spec`

4. **Read the contract** (if it exists): `../tenxengage-contracts/endpoints/{resource}.yaml` and `../tenxengage-contracts/models/{model-name}.md`
   - If no contract file exists: note this — contracts may not have been generated yet

5. **Output a summary** of what was loaded:
   ```
   Spec loaded: {feature-id}
     Title: {spec title from first heading}
     Status: {draft/reviewed}
     Sections: {list of section headings}
     Technical: {found / not found}
     Contract: {found / not found}

   The spec, technical reference, and contract are now in context. You can ask me to implement
   any part of it, or reference specific sections.
   ```

---

## Rules

- This skill sets up the feature branch and loads context. It does not create or modify application code.
- After loading, all subsequent implementation work in this conversation should reference the spec (`spec.md` for page intent, user flows, and component decisions) and technical reference (`technical.md` for file paths and hook specs)
- If the developer asks to implement something that contradicts the spec, flag it and ask for clarification
