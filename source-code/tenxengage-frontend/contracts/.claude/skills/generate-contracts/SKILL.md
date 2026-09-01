---
name: "generate-contracts"
description: "Generate contracts from a reviewed feature spec. Reads the spec from the blueprint repo, writes models and endpoint YAMLs directly into this repo on the feature branch, and commits."
argument-hint: "Feature ID (e.g., 001-enablement-courses). Auto-detects from branch if omitted."
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

---

## Prerequisites

1. **Determine feature ID**:
   - If user provided one, use it
   - Otherwise: run `git branch --show-current`
     - If branch matches `features/*`: extract feature ID (e.g., `features/001-enablement-courses` → `001-enablement-courses`)
     - Otherwise: ask the user for the feature ID

1b. **Ensure blueprint is on the feature branch:**
   - Check if `../tenxengage-blueprint/features/{feature-id}/` exists — if YES, blueprint is on the right branch; skip to step 2.
   - `git -C ../tenxengage-blueprint branch --show-current` → note `{current-branch}`
   - Check for uncommitted changes: `git -C ../tenxengage-blueprint status --porcelain`
     - If changes exist, ask the user:
       ```
       Blueprint repo has uncommitted changes on branch {current-branch}.
       A) Commit them to {current-branch} (supply a commit message)
       B) Stash them
       C) Abort — I'll switch branches manually
       ```
       - On A: commit with the user-supplied message, then proceed to checkout
       - On B: `git -C ../tenxengage-blueprint stash`, then proceed to checkout
       - On C: **abort**
   - Checkout the feature branch:
     - Try local: `git -C ../tenxengage-blueprint checkout features/{feature-id}`
       - If successful: check if behind remote:
         `git -C ../tenxengage-blueprint log HEAD..origin/features/{feature-id} --oneline 2>/dev/null`
         - If behind: ask user: "Blueprint branch is behind origin by N commits. A) Pull with rebase  B) Continue with local version"
           - On A: `git -C ../tenxengage-blueprint pull --rebase origin features/{feature-id}`
           - On B: proceed
       - If local branch not found: `git -C ../tenxengage-blueprint fetch origin features/{feature-id}:features/{feature-id} && git -C ../tenxengage-blueprint checkout features/{feature-id}`
       - If remote also not found: **abort** — "Blueprint feature branch not found locally or on origin. Run `/create-spec` from the blueprint repo first."

2. **Read the spec**: `../tenxengage-blueprint/features/{feature-slug}/spec.md`
   - If not found: error "No spec at ../tenxengage-blueprint/features/<slug>/spec.md — run /create-spec first."

   **Read domain registry for type naming** (only if `spec.md` frontmatter has `domain:` non-null):
   - Read `../tenxengage-blueprint/docs/patterns/domains/{domain}.md`.
   - Use the slot fillers for type naming in generated contracts. Examples:
     - `domain: incentive` → audience-rule contract type is `IncentiveAudienceRule`.
     - `domain: enablement` → audience-rule contract type is `AudienceRule` (from platform primitives — polymorphic over `owner_type`/`owner_id`).
   - If the spec proposes a contract type that conflicts with the registry, flag it before writing.

3. **Verify spec status is `reviewed`**:
   - Check the frontmatter status field
   - If status is `draft`: abort with — "Spec is still in draft. Run `/review-spec` from the blueprint repo first."

---

## Branch Setup

Before reading or writing any contracts:

- **Determine the base branch:**
  - Read the YAML frontmatter of `../tenxengage-blueprint/features/{feature-slug}/spec.md` — extract the `roadmap` field as `$ROADMAP_SLUG` (null if absent).
  - If `$ROADMAP_SLUG` is non-null and non-empty (feature is BRD-derived):
    - Ensure `roadmaps/{ROADMAP_SLUG}` exists locally. Run this single compound command verbatim — do not substitute any value for `${FEATURE_BASE_BRANCH:-main}` yourself:
      ```bash
      git checkout roadmaps/{ROADMAP_SLUG} 2>/dev/null || git fetch origin roadmaps/{ROADMAP_SLUG}:roadmaps/{ROADMAP_SLUG} 2>/dev/null && git checkout roadmaps/{ROADMAP_SLUG} || _b="${FEATURE_BASE_BRANCH:-main}" && echo "Creating roadmap branch from: $_b" && git checkout -b roadmaps/{ROADMAP_SLUG} "$_b"
      ```
    - Store: `BASE_BRANCH="roadmaps/{ROADMAP_SLUG}"`
  - Otherwise (`$ROADMAP_SLUG` is null or empty — standalone feature): `BASE_BRANCH="${FEATURE_BASE_BRANCH:-main}"`
- Check current branch: `git branch --show-current` → if already on `features/{feature-id}`, skip to "Sync from remote" below.
- **Ensure the feature branch exists locally.** Run this **single compound command verbatim** — do not split it:
  ```bash
  git checkout features/{feature-id} 2>/dev/null || git fetch origin features/{feature-id}:features/{feature-id} 2>/dev/null && git checkout features/{feature-id} || echo "Creating branch from: $BASE_BRANCH" && git checkout -b features/{feature-id} "$BASE_BRANCH"
  ```
- **Sync from remote** only if all three guards pass:
  - Guard 1 (remote exists): `git ls-remote --exit-code origin features/{feature-id}` → non-zero exit: skip entirely
  - Guard 2 (no uncommitted changes): `git status --porcelain` → non-empty: skip, print "Skipping remote sync — uncommitted changes present"
  - Guard 3 (no unpushed commits): `git log origin/features/{feature-id}..HEAD --oneline` → non-empty: skip, print "Skipping remote sync — unpushed commits present"
  - All guards pass: `git pull --rebase origin features/{feature-id}`

---

## Required Reading

### Existing Contract Patterns (all paths local)

1. Identify which models and endpoint groups are related to this feature (e.g., a feature adding course progress would relate to `models/lms-course.md` and `endpoints/lms-courses.yaml`).
2. Read relevant `models/*.md` to understand field names, types, and constraints for models this feature builds on or extends.
3. Read relevant `endpoints/*.yaml` to understand path and parameter format, security (`bearerAuth`) and `x-permission` annotation patterns, and response shapes for similar endpoints.

### Conventions

4. Read `conventions.md` — REST conventions, naming rules, pagination shape, error response format
5. Read `enums.md` — canonical enum values; inline enums in OpenAPI must match these exactly

---

## Generate the Contract

Using the spec's API Endpoints, DTOs, and schemas sections, generate **OpenAPI 3.0.3 YAML endpoint files and model markdown files** matching the format of existing files in this repo.

### Rules

1. **Follow existing patterns exactly** — security scheme, pagination shape, error responses, and parameter format must match `conventions.md` and the endpoint YAMLs read above
2. **All IDs are UUIDs** — `"type": "string", "format": "uuid"`
3. **`client_id` is never in the API** — tenant isolation is handled by the server's Hibernate filter
4. **Tags match the spec** — use the tag name defined in the spec
5. **Schemas use camelCase** — matching existing convention
6. **Pagination uses the shared contracts format** (from `conventions.md`) — `data`, `page`, `pageSize`, `totalElements`, `totalPages`, `hasNext`, `hasPrevious`
7. **Enums are inline** — using `"enum": ["VALUE_1", "VALUE_2"]` in the schema
8. **Request schemas have `required` arrays** — listing mandatory fields
9. **Response schemas include `id`, `createdAt`, `updatedAt`** — standard audit fields
10. **Inline enum values MUST match `enums.md`** — do not invent, reorder, or omit enum values. If the spec introduces a new enum not yet in `enums.md`, use the spec's values and append them to `enums.md` in the correct section, flagging it for the user to review.

### Modified Existing Endpoints

If the spec has a "Modified Existing Endpoints" section:
- Include those endpoints in the contract
- Add `"x-modified": true` to each modified path operation
- Include the FULL updated schema (not just the diff)

---

## Output

1. **Write** `models/{model-name}.md` for each new model the feature introduces (skip if model already exists and is unchanged)
2. **Write** `endpoints/{resource}.yaml` for each new endpoint group, using the YAML format of existing files
3. **Append** new enum sections to `enums.md` if the spec introduces new enums not already present (never overwrite existing values)

---

## Validation

After generating, verify:
- The YAML is valid (parseable)
- Every endpoint in the spec's API Endpoints table has a corresponding path in the contract
- Every DTO in the spec has a corresponding model in `models/`
- The security scheme and `x-permission` annotations match existing endpoint YAMLs

---

## Commit

```
git add endpoints/ models/ enums.md
git commit -m "feat({feature-id}): generate contracts from spec"
```

---

## Update Blueprint Tracker

After the contracts commit:

1. Check if `../tenxengage-blueprint/features/{feature-id}/tracker.md` exists — if not, skip this section silently.
2. In `tracker.md`, replace the line:
   ```
   Contracts generated: **no**
   ```
   with:
   ```
   Contracts generated: **yes**
   ```
3. Update the `**Last updated:**` line at the top to today's date (ISO 8601) and session `generate-contracts`.
4. Commit and push the tracker change in the blueprint repo:
   ```
   git -C ../tenxengage-blueprint add features/{feature-id}/tracker.md
   git -C ../tenxengage-blueprint commit -m "tracker({feature-id}): mark contracts generated"
   git -C ../tenxengage-blueprint push origin features/{feature-id}
   ```

---

## Next Steps (shown to user)

```
Contracts generated (branch: features/{feature-id}, based off: {BASE_BRANCH})

Written:
  endpoints/ → {list of files}
  models/    → {list of files}
  enums.md   → {appended N new sections / no new enums}

Committed on: features/{feature-id}

Next steps:
  1. Review the generated files
  2. Both frontend and backend teams can now start implementation referencing the spec and contracts.
  {If BRD-derived}: When this feature is done, merge features/{feature-id} → roadmaps/{ROADMAP_SLUG} (not main).
```
