# Pattern files cleanup + domain-awareness redirection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the builder pattern files (blueprint + sibling repos) up to date with the new domain registry by fixing mechanical staleness in the patterns INDEX, renaming the inconsistent builder-widget/wizard file, and adding substantive redirection blocks that label legacy pattern files as incentive-only and route new-domain developers to the platform-primitives layer.

**Architecture:** Six independent commits, each touching one or two files. Five pattern files get a redirection block (different wording per repo for the relative paths). One file rename (blueprint only). One INDEX update. Backend gets two extra concerns batched into its commit (stale data + checklist relabelling).

**Tech Stack:** Markdown only. Git for rename + history preservation. No application code.

---

## File Structure

Files created:
- `tenxengage-blueprint/docs/patterns/builder-wizard.md` — created via `git mv` from `builder-widget.md`

Files modified:
- `tenxengage-blueprint/docs/patterns/INDEX.md` — stale step refs + domains pointer
- `tenxengage-blueprint/docs/patterns/builder-wizard.md` — redirection block (post-rename)
- `tenxengage-blueprint/docs/patterns/builder-config.md` — redirection block
- `tenxengage-backend/docs/patterns/builder-config.md` — redirection block + SPIFF/REBATE fix + checklist relabelling
- `tenxengage-frontend/docs/patterns/builder-config.md` — redirection block
- `tenxengage-frontend/docs/patterns/builder-widget.md` — redirection block

Files deleted (via rename, history preserved):
- `tenxengage-blueprint/docs/patterns/builder-widget.md`

Reference-sweep targets (greps may yield additional files needing one-line updates):
- `tenxengage-blueprint/.claude/skills/` — especially `create-spec/steps/`
- `tenxengage-blueprint/PROJECT-CONTEXT.md`
- All `CLAUDE.md` files across sibling repos
- `~/.claude/projects/-Users-vijayanandkandiraju-WorkWorkWork-VSCode-tenxengage-application-tenxengage-blueprint/memory/`

---

## Task 1: Rename `builder-widget.md` → `builder-wizard.md` + reference sweep

**Files:**
- Rename: `tenxengage-blueprint/docs/patterns/builder-widget.md` → `tenxengage-blueprint/docs/patterns/builder-wizard.md`
- Modify (potentially): any file containing the string `builder-widget.md` in the blueprint repo + sibling repos + auto-memory

- [ ] **Step 1: Rename the file with `git mv` to preserve history**

```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-blueprint
git mv docs/patterns/builder-widget.md docs/patterns/builder-wizard.md
git status --short
```

Expected output includes a `R  docs/patterns/builder-widget.md -> docs/patterns/builder-wizard.md` line (rename detected).

- [ ] **Step 2: Grep for any remaining `builder-widget.md` references across all sibling repos and auto-memory**

```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application
grep -rn "builder-widget\.md" tenxengage-blueprint tenxengage-backend tenxengage-frontend tenxengage-admin-backend tenxengage-admin-frontend tenxengage-contracts 2>/dev/null | grep -v "^Binary" || echo "no remaining references in repos"

grep -rn "builder-widget\.md" /Users/vijayanandkandiraju/.claude/projects/-Users-vijayanandkandiraju-WorkWorkWork-VSCode-tenxengage-application-tenxengage-blueprint/memory/ 2>/dev/null || echo "no remaining references in auto-memory"
```

Important: this is **finding** references to the old filename `builder-widget.md`. References to the frontend repo's own `builder-widget.md` (which is NOT being renamed) are legitimate and must NOT be changed — they're in `tenxengage-frontend/docs/patterns/builder-widget.md` or refer to it by full path. Only references that pointed at the **blueprint** file need updating.

- [ ] **Step 3: For each hit from Step 2, determine whether it referred to the blueprint file (now renamed) or the frontend file (unchanged), and update only the blueprint references**

For each hit found, inspect the surrounding context:
- If the reference path includes `tenxengage-blueprint/` or is a relative path resolving to the blueprint repo → update `builder-widget.md` to `builder-wizard.md`
- If the reference path includes `tenxengage-frontend/` or resolves to the frontend repo → leave unchanged

Use the Edit tool for each file. Example edit pattern (adjust path per actual hit):

```
old_string: builder-widget.md
new_string: builder-wizard.md
```

If a file has multiple references to the blueprint file, use `replace_all: true` only after confirming all instances in that file refer to the blueprint (none to the frontend).

- [ ] **Step 4: Re-grep to verify the blueprint references are gone**

```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application
grep -rn "tenxengage-blueprint.*builder-widget\.md\|/blueprint/.*builder-widget\.md" tenxengage-blueprint tenxengage-backend tenxengage-frontend tenxengage-admin-backend tenxengage-admin-frontend tenxengage-contracts 2>/dev/null || echo "no remaining blueprint references"
```

Expected: no output (or "no remaining blueprint references"). Frontend-internal references like `tenxengage-frontend/docs/patterns/builder-widget.md` may still appear and are correct.

- [ ] **Step 5: Verify the blueprint file's H1 already says `builder-wizard` (no internal edit needed)**

```bash
head -1 tenxengage-blueprint/docs/patterns/builder-wizard.md
```

Expected output: `# Pattern: builder-wizard`

If the output is anything else, edit the H1 to read exactly `# Pattern: builder-wizard` before committing.

- [ ] **Step 6: Commit**

```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-blueprint
git add -A
git commit -m "$(cat <<'EOF'
docs(patterns): rename builder-widget.md → builder-wizard.md

The file's H1 already said "# Pattern: builder-wizard" and the patterns
INDEX.md row pointed at builder-wizard.md, but the file system name was
builder-widget.md — three names, two values, broken pointer. Rename
aligns all three on "builder-wizard". The content describes a multi-step
guided creation flow (wizard), not a small reusable UI component (widget),
so wizard is the correct name.

Frontend's own docs/patterns/builder-widget.md is internally consistent
(file + H1 both say "widget") and stays unchanged — it describes the
frontend implementation, a different concern from the blueprint's pattern.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Mechanical fixes to `tenxengage-blueprint/docs/patterns/INDEX.md`

**Files:**
- Modify: `tenxengage-blueprint/docs/patterns/INDEX.md`

- [ ] **Step 1: Read the current INDEX.md to know its exact content**

```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-blueprint
cat docs/patterns/INDEX.md
```

Note the current line-by-line content. The edits below assume the current state matches what's described.

- [ ] **Step 2: Update stale step references in the pattern registry table**

Use the Edit tool with `replace_all: false`. Apply each of the following replacements in order. For any replacement where the `old_string` is not unique, include enough surrounding context (the full table row) to disambiguate.

Replacement 1 (permissions step number):
```
old_string: | permissions-and-feature-flags | permissions-and-feature-flags.md | ALWAYS | create-spec step 09 |
new_string: | permissions-and-feature-flags | permissions-and-feature-flags.md | ALWAYS | create-spec step 11 |
```

Replacement 2 (package-structure step numbers):
```
old_string: | package-structure | package-structure.md | ALWAYS | create-spec steps 12, 13 |
new_string: | package-structure | package-structure.md | ALWAYS | create-spec steps 13, 14 |
```

Replacement 3 (location-hierarchy step):
```
old_string: | location-hierarchy | location-hierarchy.md | Feature filters/scopes by location (geographic/organizational tree) | create-spec step 12 |
new_string: | location-hierarchy | location-hierarchy.md | Feature filters/scopes by location (geographic/organizational tree) | create-spec step 13 |
```

Replacement 4 (builder-wizard step + the row also acts as the broken-pointer fix from Task 1):
```
old_string: | builder-wizard | builder-wizard.md | Feature has multi-step UI for create/edit | create-spec step 12 |
new_string: | builder-wizard | builder-wizard.md | Feature has multi-step UI for create/edit | create-spec step 13 |
```

Replacement 5 (builder-config step):
```
old_string: | builder-config | builder-config.md | Feature uses dynamic builder configuration | create-spec step 12 |
new_string: | builder-config | builder-config.md | Feature uses dynamic builder configuration | create-spec step 13 |
```

Replacement 6 (ai-copilot step):
```
old_string: | ai-copilot | ai-copilot.md | Feature integrates AI assistance | create-spec step 12 |
new_string: | ai-copilot | ai-copilot.md | Feature integrates AI assistance | create-spec step 13 |
```

Replacement 7 (html-content step):
```
old_string: | html-content | html-content.md | Feature stores user-generated HTML / rich-text | create-spec step 12 |
new_string: | html-content | html-content.md | Feature stores user-generated HTML / rich-text | create-spec step 13 |
```

Replacement 8 (sse-streaming step):
```
old_string: | sse-streaming | sse-streaming.md | Feature uses Server-Sent Events | create-spec step 12 |
new_string: | sse-streaming | sse-streaming.md | Feature uses Server-Sent Events | create-spec step 13 |
```

Replacement 9 (currency-handling step):
```
old_string: | currency-handling | currency-handling.md | Feature involves money / pricing | create-spec step 12 |
new_string: | currency-handling | currency-handling.md | Feature involves money / pricing | create-spec step 13 |
```

Replacement 10 (rate-limit step):
```
old_string: | rate-limit-sensitive | rate-limit-sensitive.md | Feature has expensive or abuse-prone endpoints | create-spec step 07 |
new_string: | rate-limit-sensitive | rate-limit-sensitive.md | Feature has expensive or abuse-prone endpoints | create-spec step 08 |
```

Replacement 11 (event-publishing step):
```
old_string: | event-publishing | event-publishing.md | Feature publishes Kafka events | create-spec step 08 |
new_string: | event-publishing | event-publishing.md | Feature publishes Kafka events | create-spec step 09 |
```

Replacement 12 (event-consuming step):
```
old_string: | event-consuming | event-consuming.md | Feature consumes Kafka events | create-spec step 08 |
new_string: | event-consuming | event-consuming.md | Feature consumes Kafka events | create-spec step 09 |
```

Replacement 13 (load step in bottom paragraph):
```
old_string: **Loading vs consumption:** All matching pattern files are loaded once in `create-spec` step 05 (`load-shape-references`).
new_string: **Loading vs consumption:** All matching pattern files are loaded once in `create-spec` step 06 (`load-shape-references`).
```

- [ ] **Step 3: Add the domains-registry pointer section**

Add a new section between the "How to use" section and the "Registry" section. Use Edit with the following replacement:

```
old_string: ## Registry

| Pattern | File | Gate (when this applies) | Consumed by step |
new_string: ## Domain registry

Pattern files describe feature-shape conventions. For **builder-shaped features**, the [domain registry](domains/INDEX.md) is the structural authority (slot fillers, primitive names, parallel-rails strategy). Read it alongside this index when the feature is slot-filling.

| Registry | File | Gate | Consumed by step |
|---|---|---|---|
| domain-registry | domains/INDEX.md | ALWAYS for slot-filling features | create-spec step 04 |

## Registry

| Pattern | File | Gate (when this applies) | Consumed by step |
```

- [ ] **Step 4: Verify all updates look right**

```bash
cat docs/patterns/INDEX.md
```

Visually scan for:
- All step numbers in the Registry table match the new values
- The "Domain registry" section exists with its pointer row
- The "Loading vs consumption" paragraph references step 06

- [ ] **Step 5: Commit**

```bash
git add docs/patterns/INDEX.md
git commit -m "$(cat <<'EOF'
docs(patterns): refresh INDEX.md — fix stale create-spec step refs, add domains registry pointer

Stale step refs predated this work (e.g., step 09 for permissions when
current permissions-analysis is at step 11; step 12 for spec generation
when current generate-spec-content is at step 13). Skills reading these
as routing hints could misfire. 13 step-number corrections in total.

Adds a "Domain registry" section pointing at docs/patterns/domains/INDEX.md
so the registry is discoverable from the main patterns INDEX. Surfaces
the registry as a first-class artifact alongside (but distinct from) the
feature-shape pattern files.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Blueprint redirection blocks (`builder-wizard.md` + `builder-config.md`)

**Files:**
- Modify: `tenxengage-blueprint/docs/patterns/builder-wizard.md`
- Modify: `tenxengage-blueprint/docs/patterns/builder-config.md`

- [ ] **Step 1: Read `builder-wizard.md` to confirm its H1 line and what immediately follows**

```bash
head -10 tenxengage-blueprint/docs/patterns/builder-wizard.md
```

Note the H1 (`# Pattern: builder-wizard`) and the line immediately after it (typically a blank line, then `## When this applies`).

- [ ] **Step 2: Insert the redirection block at the top of `builder-wizard.md`**

Use Edit with the following replacement. The `old_string` matches the H1 and the blank line + opening of the "When this applies" section. The `new_string` adds the redirection block between them.

```
old_string: # Pattern: builder-wizard

## When this applies
new_string: # Pattern: builder-wizard

> **⚠️ Legacy bespoke pattern — incentive domain only.**
>
> This file describes the multi-step builder UI pattern as implemented by the
> incentive builder. Status per the [domain registry](domains/INDEX.md):
> `active-legacy` (see [domains/incentive.md](domains/incentive.md)). The code
> stays in production; the file stays for reference. **New code should not
> adopt this pattern as-is.**
>
> **Implementing a feature for a new domain (enablement, future)?**
> Do NOT follow this pattern directly. New domains use **platform primitives**:
> `BuilderDefinition` / `BuilderSectionDefinition` / `BuilderFieldDefinition`
> per [domains/platform-primitives.md](domains/platform-primitives.md). The
> platform-primitives implementation does not exist in code yet — the first
> feature landing on platform primitives builds it, guided by:
> - The slot list and naming convention in [domains/INDEX.md](domains/INDEX.md)
> - The design at [../superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md](../superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md)
>
> **First engineer to land platform primitives:** as a final deliverable of
> your feature, write `builder-wizard-platform.md` (or a naturally-named
> equivalent once implementation has clarified the structure) and register it
> in [INDEX.md](INDEX.md). This redirection block points at your file once
> it exists.

---

## When this applies
```

- [ ] **Step 3: Read `builder-config.md` to confirm its H1 and what follows**

```bash
head -10 tenxengage-blueprint/docs/patterns/builder-config.md
```

- [ ] **Step 4: Insert the redirection block at the top of `builder-config.md`**

```
old_string: # Pattern: builder-config

## When this applies
new_string: # Pattern: builder-config

> **⚠️ Legacy bespoke pattern — incentive domain only.**
>
> This file describes the `BuilderSectionConfig` / `BuilderFieldConfig` /
> `BuilderConfigService` stack which serves the incentive domain. Status per
> the [domain registry](domains/INDEX.md): `active-legacy` (see
> [domains/incentive.md](domains/incentive.md)). The code stays in production;
> the file stays for reference. **New code should not adopt this pattern.**
>
> **Implementing a feature for a new domain (enablement, future)?**
> Do NOT follow this pattern. New domains use **platform primitives**:
> `BuilderDefinition` / `BuilderSectionDefinition` / `BuilderFieldDefinition`
> per [domains/platform-primitives.md](domains/platform-primitives.md). The
> platform-primitives implementation does not exist in code yet — the first
> feature landing on platform primitives builds it, guided by:
> - The slot list and naming convention in [domains/INDEX.md](domains/INDEX.md)
> - The design at [../superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md](../superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md)
>
> **First engineer to land platform primitives:** as a final deliverable of
> your feature, write `builder-config-platform.md` (or a naturally-named
> equivalent once implementation has clarified the structure) and register it
> in [INDEX.md](INDEX.md). This redirection block points at your file once
> it exists.

---

## When this applies
```

- [ ] **Step 5: Verify both files have the redirection block and that links resolve**

```bash
head -30 tenxengage-blueprint/docs/patterns/builder-wizard.md
head -30 tenxengage-blueprint/docs/patterns/builder-config.md

# Verify each link target exists relative to the file's directory
ls tenxengage-blueprint/docs/patterns/domains/INDEX.md
ls tenxengage-blueprint/docs/patterns/domains/incentive.md
ls tenxengage-blueprint/docs/patterns/domains/platform-primitives.md
ls tenxengage-blueprint/docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md
ls tenxengage-blueprint/docs/patterns/INDEX.md
```

Expected: all `ls` commands return without error.

- [ ] **Step 6: Commit**

```bash
git add docs/patterns/builder-wizard.md docs/patterns/builder-config.md
git commit -m "$(cat <<'EOF'
docs(patterns): add domain-awareness redirection blocks to blueprint builder pattern files

Both files now open with a substantive block that labels them as
incentive-legacy, points at the domain registry, tells new-domain
developers to use platform primitives instead, and assigns the
platform-primitives pattern-doc authoring to the first engineer who lands
platform-primitives implementation.

Closes the silent leakage path a developer would otherwise follow when
told to "follow the builder-config pattern" for a new enablement feature.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Backend `builder-config.md` — redirection block + stale-data fix + checklist relabelling

**Files:**
- Modify: `tenxengage-backend/docs/patterns/builder-config.md`

- [ ] **Step 1: Read the file's structure to find the H1, the SPIFF/REBATE references, and the "Adding a new builder type — checklist" section**

```bash
cat tenxengage-backend/docs/patterns/builder-config.md
```

Note:
- The H1 (likely `# Builder Configuration Pattern — Backend`)
- Every line containing `SPIFF` or `REBATE`
- The exact heading of the checklist section (likely `## Adding a new builder type — checklist`)

- [ ] **Step 2: Insert the redirection block at the top of the file, immediately under the H1**

Use the Edit tool. Adjust the `old_string` to match the actual H1 + line that follows it in the file:

```
old_string: # Builder Configuration Pattern — Backend

How the backend manages dynamic, tenant-scoped builder configuration. Tenants customize which sections and fields appear in the incentive builder, their order, and how field values resolve at runtime.
new_string: # Builder Configuration Pattern — Backend

> **⚠️ Legacy bespoke pattern — incentive domain only.**
>
> This file describes the `BuilderSectionConfig` / `BuilderFieldConfig` /
> `BuilderConfigService` stack which serves the incentive domain. Status per
> the [domain registry](../../../tenxengage-blueprint/docs/patterns/domains/INDEX.md):
> `active-legacy` (see [incentive.md](../../../tenxengage-blueprint/docs/patterns/domains/incentive.md)).
> The code stays in production; the file stays for reference. **New code should
> not adopt this pattern.**
>
> **Implementing a feature for a new domain (enablement, future)?**
> Do NOT follow this pattern. New domains use **platform primitives**:
> `BuilderDefinition` / `BuilderSectionDefinition` / `BuilderFieldDefinition`
> per [platform-primitives.md](../../../tenxengage-blueprint/docs/patterns/domains/platform-primitives.md).
> The platform-primitives implementation does not exist in code yet — the
> first feature landing on platform primitives builds it, guided by:
> - The slot list and naming convention in [domains/INDEX.md](../../../tenxengage-blueprint/docs/patterns/domains/INDEX.md)
> - The design at [docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md](../../../tenxengage-blueprint/docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md)
>
> **First engineer to land platform primitives:** as a final deliverable of
> your feature, write `builder-config-platform.md` in this directory (or a
> naturally-named equivalent once implementation has clarified the structure)
> and register it in the blueprint's [patterns INDEX](../../../tenxengage-blueprint/docs/patterns/INDEX.md).
> This redirection block points at your file once it exists.

---

How the backend manages dynamic, tenant-scoped builder configuration. Tenants customize which sections and fields appear in the incentive builder, their order, and how field values resolve at runtime.
```

If the second line of the file differs from "How the backend manages..." adjust the `old_string` to match what's actually there.

- [ ] **Step 3: Replace `SPIFF` references with current incentive_type values**

For each occurrence of `SPIFF` and `REBATE` in the file, replace with the current canonical values. The most common pattern is in code blocks or prose like `(\`SPIFF\`, \`REBATE\`, …)`.

Identify each occurrence first:

```bash
grep -n "SPIFF\|REBATE" tenxengage-backend/docs/patterns/builder-config.md
```

For each line in the output, use the Edit tool with the exact line as `old_string` (with sufficient surrounding text to make it unique) and replace the example values. Typical replacement:

```
old_string: (`SPIFF`, `REBATE`, …)
new_string: (`SALES`, `TRAINING`, `ACTIVITY`, `JOURNEY`)
```

If the file mentions SPIFF/REBATE in multiple distinct contexts, perform each Edit separately with appropriate disambiguating context.

- [ ] **Step 4: Relabel the "Adding a new builder type — checklist" section**

Use the Edit tool:

```
old_string: ## Adding a new builder type — checklist
new_string: ## Adding a new INCENTIVE_TYPE — checklist

> ⚠️ **This checklist applies only to adding a new variant within the
> incentive domain** (e.g., a fifth value beyond `SALES`, `TRAINING`,
> `ACTIVITY`, `JOURNEY`). For a new DOMAIN (enablement, future), see the
> redirection block at the top of this file — do not follow this checklist.

```

(Note the blank line at the end of `new_string`, which separates the warning paragraph from the existing first checklist item.)

- [ ] **Step 5: Verify the edits**

```bash
head -35 tenxengage-backend/docs/patterns/builder-config.md
grep -n "SPIFF\|REBATE" tenxengage-backend/docs/patterns/builder-config.md
grep -n "Adding a new" tenxengage-backend/docs/patterns/builder-config.md
```

Expected:
- `head` shows the redirection block under the H1.
- `grep SPIFF\|REBATE` produces no output (all references replaced).
- `grep "Adding a new"` shows the relabelled heading.

- [ ] **Step 6: Verify cross-repo links resolve**

```bash
ls /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-blueprint/docs/patterns/domains/INDEX.md
ls /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-blueprint/docs/patterns/domains/incentive.md
ls /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-blueprint/docs/patterns/domains/platform-primitives.md
ls /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-blueprint/docs/patterns/INDEX.md
ls /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-blueprint/docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md
```

Expected: all five `ls` commands succeed.

- [ ] **Step 7: Commit (in the backend repo)**

```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-backend
git add docs/patterns/builder-config.md
git commit -m "$(cat <<'EOF'
docs(patterns): builder-config — add domain-awareness redirection + fix stale incentive_type examples + scope checklist

Three concerns batched:

1. Substantive redirection block at the top labels this file as
   incentive-legacy, points at the blueprint's domain registry, and
   assigns platform-primitives pattern-doc authoring to the first
   engineer who lands platform primitives.

2. SPIFF / REBATE examples replaced with current incentive_type values
   (SALES, TRAINING, ACTIVITY, JOURNEY) matching the actual codebase.

3. "Adding a new builder type — checklist" renamed to "Adding a new
   INCENTIVE_TYPE — checklist" with a domain-scoping warning. Prevents
   a new-domain developer from following the incentive seed steps.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Frontend `builder-config.md` — redirection block

**Files:**
- Modify: `tenxengage-frontend/docs/patterns/builder-config.md`

- [ ] **Step 1: Read the file's H1 and the line that follows**

```bash
head -5 tenxengage-frontend/docs/patterns/builder-config.md
```

- [ ] **Step 2: Insert the redirection block at the top of the file, immediately under the H1**

Use the Edit tool. Adjust the `old_string` to match the actual H1 + line that follows:

```
old_string: # Builder Configuration Pattern — Frontend

This document describes how the frontend consumes and renders dynamic builder configurations. Builder configuration makes the steps, sections, and fields within a builder data-driven rather than hardcoded, allowing administrators to customize what fields appear, their order, and their behavior.
new_string: # Builder Configuration Pattern — Frontend

> **⚠️ Legacy bespoke pattern — incentive domain only.**
>
> This file describes how the frontend consumes the `BuilderSectionConfig` /
> `BuilderFieldConfig` API which serves the incentive domain. Status per the
> [domain registry](../../../tenxengage-blueprint/docs/patterns/domains/INDEX.md):
> `active-legacy` (see [incentive.md](../../../tenxengage-blueprint/docs/patterns/domains/incentive.md)).
> The code stays in production; the file stays for reference. **New code
> should not adopt this pattern.**
>
> **Implementing a feature for a new domain (enablement, future)?**
> Do NOT follow this pattern. New domains use **platform primitives**:
> `BuilderDefinition` / `BuilderSectionDefinition` / `BuilderFieldDefinition`
> per [platform-primitives.md](../../../tenxengage-blueprint/docs/patterns/domains/platform-primitives.md).
> The platform-primitives implementation does not exist in code yet — the
> first feature landing on platform primitives builds it, guided by:
> - The slot list and naming convention in [domains/INDEX.md](../../../tenxengage-blueprint/docs/patterns/domains/INDEX.md)
> - The design at [docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md](../../../tenxengage-blueprint/docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md)
>
> **First engineer to land platform primitives:** as a final deliverable of
> your feature, write `builder-config-platform.md` in this directory (or a
> naturally-named equivalent once implementation has clarified the structure)
> and register it in the blueprint's [patterns INDEX](../../../tenxengage-blueprint/docs/patterns/INDEX.md).
> This redirection block points at your file once it exists.

---

This document describes how the frontend consumes and renders dynamic builder configurations. Builder configuration makes the steps, sections, and fields within a builder data-driven rather than hardcoded, allowing administrators to customize what fields appear, their order, and their behavior.
```

- [ ] **Step 3: Verify**

```bash
head -30 tenxengage-frontend/docs/patterns/builder-config.md
```

- [ ] **Step 4: Commit (in the frontend repo)**

```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-frontend
git add docs/patterns/builder-config.md
git commit -m "$(cat <<'EOF'
docs(patterns): add domain-awareness redirection block to builder-config

Labels the file as incentive-legacy, points at the blueprint's domain
registry, and routes new-domain developers to platform primitives.
First-enablement engineer is assigned the platform-pattern pattern-doc
deliverable.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Frontend `builder-widget.md` — redirection block

**Files:**
- Modify: `tenxengage-frontend/docs/patterns/builder-widget.md`

- [ ] **Step 1: Read the file's H1 and the line that follows**

```bash
head -5 tenxengage-frontend/docs/patterns/builder-widget.md
```

- [ ] **Step 2: Insert the redirection block at the top, immediately under the H1**

```
old_string: # Builder Widget Pattern

This document describes the standard architecture for builder/wizard components in the TenXEngage frontend. Builders are complex multi-step forms used to create and edit entities like incentives, courses, and other configurable objects. Every new builder must follow this pattern for consistency.
new_string: # Builder Widget Pattern

> **⚠️ Legacy bespoke pattern — incentive domain only.**
>
> This file describes the frontend builder component architecture as
> implemented for the incentive builder. Status per the
> [domain registry](../../../tenxengage-blueprint/docs/patterns/domains/INDEX.md):
> `active-legacy` (see [incentive.md](../../../tenxengage-blueprint/docs/patterns/domains/incentive.md)).
> The code stays in production; the file stays for reference. **New code
> should not adopt this pattern as-is.**
>
> **Implementing a feature for a new domain (enablement, future)?**
> Do NOT follow this pattern directly. New domains use **platform primitives**:
> `BuilderDefinition` / `BuilderSectionDefinition` / `BuilderFieldDefinition`
> per [platform-primitives.md](../../../tenxengage-blueprint/docs/patterns/domains/platform-primitives.md).
> The platform-primitives implementation does not exist in code yet — the
> first feature landing on platform primitives builds it (including the
> frontend renderer shell `BuilderShell` and the config-driven section
> dispatch), guided by:
> - The slot list and naming convention in [domains/INDEX.md](../../../tenxengage-blueprint/docs/patterns/domains/INDEX.md)
> - The design at [docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md](../../../tenxengage-blueprint/docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md)
>
> **First engineer to land platform primitives:** as a final deliverable of
> your feature, write `builder-widget-platform.md` in this directory (or a
> naturally-named equivalent once implementation has clarified the structure)
> and register it in the blueprint's [patterns INDEX](../../../tenxengage-blueprint/docs/patterns/INDEX.md).
> This redirection block points at your file once it exists.

---

This document describes the standard architecture for builder/wizard components in the TenXEngage frontend. Builders are complex multi-step forms used to create and edit entities like incentives, courses, and other configurable objects. Every new builder must follow this pattern for consistency.
```

- [ ] **Step 3: Verify**

```bash
head -30 tenxengage-frontend/docs/patterns/builder-widget.md
```

- [ ] **Step 4: Commit (in the frontend repo)**

```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-frontend
git add docs/patterns/builder-widget.md
git commit -m "$(cat <<'EOF'
docs(patterns): add domain-awareness redirection block to builder-widget

Labels the file as incentive-legacy, points at the blueprint's domain
registry, and routes new-domain developers to platform primitives. The
existing "reference impl" callouts in the body stay in place; the
redirection block at the top does the heavy lifting for new-domain
readers.

Note: file is NOT renamed to builder-wizard. The frontend file's name
and H1 both say "widget" — internally consistent. Only the blueprint's
file had a naming inconsistency (file widget vs H1 wizard); that one
was renamed separately.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Final verification (after all six tasks)

- [ ] **Cross-repo sanity check**

```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application

# All five files should contain the redirection block heading
for f in \
  tenxengage-blueprint/docs/patterns/builder-wizard.md \
  tenxengage-blueprint/docs/patterns/builder-config.md \
  tenxengage-backend/docs/patterns/builder-config.md \
  tenxengage-frontend/docs/patterns/builder-config.md \
  tenxengage-frontend/docs/patterns/builder-widget.md ; do
  echo "=== $f ==="
  grep -c "Legacy bespoke pattern" "$f" || echo "MISSING redirection block in $f"
done

# Blueprint INDEX should have the domains pointer
grep -c "domain-registry" tenxengage-blueprint/docs/patterns/INDEX.md

# builder-widget.md should NOT exist in blueprint (renamed)
[ ! -f tenxengage-blueprint/docs/patterns/builder-widget.md ] && echo "blueprint builder-widget.md correctly removed" || echo "ERROR: blueprint builder-widget.md still exists"

# builder-wizard.md should exist in blueprint
[ -f tenxengage-blueprint/docs/patterns/builder-wizard.md ] && echo "blueprint builder-wizard.md exists" || echo "ERROR: blueprint builder-wizard.md missing"

# Backend should not have SPIFF/REBATE references
grep -c "SPIFF\|REBATE" tenxengage-backend/docs/patterns/builder-config.md && echo "ERROR: stale SPIFF/REBATE references remain" || echo "backend stale-data fix applied"
```

Expected outputs:
- Each of the 5 files: `1` (redirection block present)
- Blueprint INDEX: `1` or higher (domain-registry section present)
- "blueprint builder-widget.md correctly removed"
- "blueprint builder-wizard.md exists"
- "backend stale-data fix applied"

- [ ] **Confirm acceptance criteria from design section 8**

Read `docs/superpowers/specs/2026-05-13-patterns-domain-redirection-and-cleanup-design.md` section 8 and verify each criterion is satisfied. Specifically:

1. `builder-wizard.md` exists, `builder-widget.md` doesn't — verified above.
2. No reference to `builder-widget.md` (in blueprint context) anywhere — verified via grep in Task 1 Step 4.
3. INDEX.md step references match current /create-spec — verified visually in Task 2 Step 4.
4. Domains pointer in INDEX.md — verified above.
5. All five files carry redirection block with resolving links — verified above; spot-check by clicking links in a markdown viewer if available.
6. Backend no longer contains SPIFF/REBATE — verified above.
7. Backend checklist relabelled — verified visually in Task 4 Step 5.
8. A developer opening any file is directed to platform primitives within first screen — visual confirmation; the redirection block is the first thing after the H1 in every file.
