# /create-spec Amendment — Mode-Gate Scoping + Step Ordering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Amend the previously-implemented `/create-spec` domain-awareness work to fix two issues surfaced by the final review: (1) narrow the Mode-1 gate from the entire `resolve-open-questions` step to only the open-questions sub-phase (Phase 2 Part A), so slot-filling detection / domain awareness / builder sub-flow / "anything else?" fire for every input mode; (2) move `resolve-open-questions` from position 02 to position 04 so it runs after both BRD context (step 02) and project context (step 03) are loaded.

**Architecture:** Three step-file renames (a three-way slot rotation via a temp filename) + routing-pointer updates in four step files + content edits to two step files: `resolve-open-questions` (gate narrowing + removal of an on-demand registry load) and `load-project-context` (adding `docs/patterns/domains/INDEX.md` to its always-load list). All edits land on branch `skills-optimization-v5` in `tenxengage-blueprint`. No sibling-repo work.

**Tech Stack:** Markdown skill files; git for renames; grep for verification.

---

## Source design

Design doc: `docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md` (latest commit `76bb749`).

Sections referenced:
- §3 — corrected placement and "Fires for" semantics.
- §11 — final step-numbering table and routing-pointer list.
- §15 #9 — acceptance criterion for the amendment.
- §16 — implementation order (amendment folds into the existing step-5 / step-6 entries).

## Pre-amendment state (verified before planning)

- `.claude/skills/create-spec/steps/step-02-resolve-open-questions.md` exists; line 1 header `# Step 02:`; lines 11–13 contain the step-entry Mode gate; line 83 reads `docs/patterns/domains/INDEX.md` on-demand inside the domain awareness procedure; line 118 boundary routes to step 03.
- `.claude/skills/create-spec/steps/step-03-load-brd-context.md` exists; line 22 skip-ahead routes to step 04; line 45 prose "step 04 (project context)"; line 56 boundary routes to step 04.
- `.claude/skills/create-spec/steps/step-04-load-project-context.md` exists; line 8 input "from step 03"; line 16 has `tenxengage-blueprint/docs/patterns/INDEX.md` (pattern registry — NOT the domain registry); line 50 boundary routes to step 05.
- `.claude/skills/create-spec/steps/step-01-parse-input.md` line 37 prose "step 03"; line 39 prose "step 04"; line 66 boundary routes to `step-02-resolve-open-questions.md`.
- `.claude/skills/create-spec/references/plan-file-schema.md` line 12 stepsCompleted lists `resolve-open-questions` second (after `parse-input`); line 32 references "step 04 reading".

## File structure

**Files renamed (3-way swap via temp filename):**

| From | To |
|---|---|
| `steps/step-02-resolve-open-questions.md` | `steps/step-04-resolve-open-questions.md` |
| `steps/step-03-load-brd-context.md`       | `steps/step-02-load-brd-context.md` |
| `steps/step-04-load-project-context.md`   | `steps/step-03-load-project-context.md` |

**Files modified in blueprint:**

- `steps/step-01-parse-input.md` — boundary target; two in-prose step numbers.
- `steps/step-02-load-brd-context.md` (post-rename) — header, skip-ahead, in-prose "step 04", boundary.
- `steps/step-03-load-project-context.md` (post-rename) — header, in-prose "from step 03", always-load list, boundary.
- `steps/step-04-resolve-open-questions.md` (post-rename) — header, remove step-entry Mode gate, narrow Mode-1 to Phase 2 Part A only, update Goal, drop on-demand registry load, boundary.
- `references/plan-file-schema.md` — stepsCompleted ordering; "step 04 reading" prose.
- `steps/step-05-detect-feature-shape.md` through `steps/step-16-…` and `SKILL.md` — sweep for any in-prose references to "step 02"/"step 03"/"step 04" that need bumping.

## Task ordering rationale

Tasks 1–3 are sequential (each depends on the prior task's state). Task 4 is a final sweep that catches any straggling in-prose references. Task 5 is read-only verification. Each task commits independently so an engineer can review the diff before moving on.

The renames in Task 1 happen via a temp filename to avoid `git mv` target-exists errors during the three-way rotation.

---

## Task 1: Three-way file swap + routing pointers + headers

**Acceptance criterion:** AC9 partial — step files are at their new positions; routing graph reads step-01 → step-02 → step-03 → step-04 → step-05.

**Files:**
- Renames (3 files, listed above).
- Modify: `steps/step-01-parse-input.md` (boundary + in-prose).
- Modify: post-rename `steps/step-02-load-brd-context.md` (header, skip-ahead, in-prose, boundary).
- Modify: post-rename `steps/step-03-load-project-context.md` (header, in-prose, boundary).
- Modify: post-rename `steps/step-04-resolve-open-questions.md` (header, boundary). Content edits land in Tasks 2 + 3.

### Steps

- [ ] **Step 1.1: Verify pre-state**

```bash
cd /Users/vijayanandkandiraju/WorkWorkWork/VSCode/tenxengage-application/tenxengage-blueprint
ls .claude/skills/create-spec/steps/ | head -6
git branch --show-current
```

Expected output:
```
step-01-parse-input.md
step-02-resolve-open-questions.md
step-03-load-brd-context.md
step-04-load-project-context.md
step-05-detect-feature-shape.md
step-06-load-shape-references.md
skills-optimization-v5
```

If anything differs, STOP — the pre-amendment state is not what this plan assumes. Re-read the design doc and reconcile before continuing.

- [ ] **Step 1.2: Three-way rename via temp filename**

```bash
cd .claude/skills/create-spec/steps
git mv step-02-resolve-open-questions.md __tmp-resolve-open-questions.md
git mv step-03-load-brd-context.md       step-02-load-brd-context.md
git mv step-04-load-project-context.md   step-03-load-project-context.md
git mv __tmp-resolve-open-questions.md   step-04-resolve-open-questions.md
cd -
```

Verify:
```bash
ls .claude/skills/create-spec/steps/ | head -5
```

Expected:
```
step-01-parse-input.md
step-02-load-brd-context.md
step-03-load-project-context.md
step-04-resolve-open-questions.md
step-05-detect-feature-shape.md
```

- [ ] **Step 1.3: Update step-01 boundary + in-prose step numbers**

Edit `.claude/skills/create-spec/steps/step-01-parse-input.md`:

Find line 37:
```
- For Mode 1 (BRD identifier), record "digest will be loaded in step 03" — do NOT load it here.
```
Change to:
```
- For Mode 1 (BRD identifier), record "digest will be loaded in step 02" — do NOT load it here.
```

Find line 39:
```
- Do NOT read project-context files in this step (that's step 04).
```
Change to:
```
- Do NOT read project-context files in this step (that's step 03).
```

Find line 66 (boundary):
```
User confirms understanding → route to step 02: read `steps/step-02-resolve-open-questions.md`.
```
Change to:
```
User confirms understanding → route to step 02: read `steps/step-02-load-brd-context.md`.
```

- [ ] **Step 1.4: Update new step-02 (load-brd-context) header + skip-ahead + in-prose + boundary**

Edit `.claude/skills/create-spec/steps/step-02-load-brd-context.md`:

Line 1 header:
```
# Step 03: load-brd-context
```
Change to:
```
# Step 02: load-brd-context
```

Line 22 (skip-ahead, inside the procedure):
```
   - If no digest applies → skip ahead to the boundary (route to step 04)
```
Change to:
```
   - If no digest applies → skip ahead to the boundary (route to step 03)
```

Line 45 (cross-reference inside Rules):
```
- This step does NOT replace step 04 (project context); it adds BRD-specific context on top.
```
Change to:
```
- This step does NOT replace step 03 (project context); it adds BRD-specific context on top.
```

Line 56 (boundary):
```
Cross-cutting context loaded (or skipped) → route to step 04: read steps/step-04-load-project-context.md`.
```
Change to:
```
Cross-cutting context loaded (or skipped) → route to step 03: read steps/step-03-load-project-context.md`.
```

- [ ] **Step 1.5: Update new step-03 (load-project-context) header + in-prose + boundary**

Edit `.claude/skills/create-spec/steps/step-03-load-project-context.md`:

Line 1 header:
```
# Step 04: load-project-context
```
Change to:
```
# Step 03: load-project-context
```

Line 8 (Inputs section):
```
- Cross-cutting BRD context if applicable (from step 03)
```
Change to:
```
- Cross-cutting BRD context if applicable (from step 02)
```

Line 50 (boundary):
```
All files read, all globs run → route to step 05: read steps/step-05-detect-feature-shape.md`.
```
Change to:
```
All files read, all globs run → route to step 04: read steps/step-04-resolve-open-questions.md`.
```

(Note: Task 3 also edits this file's always-load list. Do not touch that block in this task — keep edits scoped to header + input + boundary.)

- [ ] **Step 1.6: Update new step-04 (resolve-open-questions) header + boundary**

Edit `.claude/skills/create-spec/steps/step-04-resolve-open-questions.md`:

Line 1 header:
```
# Step 02: resolve-open-questions-and-additions
```
Change to:
```
# Step 04: resolve-open-questions-and-additions
```

Line 118 (boundary):
```
All inputs gathered → route to step 03: read `steps/step-03-load-brd-context.md`.
```
Change to:
```
All inputs gathered → route to step 05: read `steps/step-05-detect-feature-shape.md`.
```

(Note: Task 2 narrows the Mode gate in this file. Do not touch the Mode gate / Goal section in this task.)

- [ ] **Step 1.7: Verify routing graph**

```bash
for f in .claude/skills/create-spec/steps/step-*.md; do
  printf "%-50s -> " "$(basename "$f")"
  grep -E "route to step" "$f" | tail -1 | sed -E 's/.*route to step ([0-9]+).*/step \1/' || echo "(terminal)"
done
```

Expected:
```
step-01-parse-input.md                     -> step 02
step-02-load-brd-context.md                -> step 03
step-03-load-project-context.md            -> step 04
step-04-resolve-open-questions.md          -> step 05
step-05-detect-feature-shape.md            -> step 06
...
step-15-write-plan-file.md                 -> step 16
step-16-branch-write-review-finalize.md    -> (terminal)
```

Note: `step-02-load-brd-context.md` will show its first "route to step" match (the skip-ahead "route to step 03"), not the boundary line — both should now say step 03, which is consistent. If `tail -1` shows the skip-ahead instead of the boundary, both lines must still match step 03.

- [ ] **Step 1.8: Commit**

```bash
git add .claude/skills/create-spec/steps/
git commit -m "refactor(create-spec): swap step ordering — load-brd/project to 02/03, resolve-open-questions to 04"
```

---

## Task 2: Narrow Mode-1 gate inside resolve-open-questions

**Acceptance criterion:** AC9 mode-gate half — slot-filling detection, domain awareness, builder sub-flow, "anything else?", classification all run unconditionally on mode; only Phase 2 Part A (open-questions) is Mode-1-gated.

**Files:**
- Modify: `steps/step-04-resolve-open-questions.md` (post-rename) — Goal text, remove step-entry Mode gate, narrow Mode-1 to Phase 2 Part A only.

### Steps

- [ ] **Step 2.1: Update Goal section to reflect "always runs"**

Edit `.claude/skills/create-spec/steps/step-04-resolve-open-questions.md`:

Lines 3–4 currently:
```
## Goal
For Mode 1 (BRD identifier) only: resolve every open question from the feature brief, gather any net-new additions from the user, and (if the feature is builder-shaped) present a draft builder structure for review.
```

Change to:
```
## Goal
Resolve open questions (Mode 1 only — those live in a feature brief), detect slot-filling and run domain awareness (all modes), gather any net-new additions from the user (all modes), and — if the feature is builder-shaped — present a draft builder structure for review (all modes).
```

- [ ] **Step 2.2: Remove step-entry Mode gate (lines 11–13)**

Current block (lines 10–14):
```
## Mode gate
- If input mode is Mode 2, 3, or 4 → SKIP this step entirely. Route to step 03.
- If Mode 1 → continue.

## Procedure
```

Delete the entire `## Mode gate` section. The file should now flow directly from `## Inputs (from prior steps)` to `## Procedure`.

After this edit, the lines around the deletion should read:
```
## Inputs (from prior steps)
- Locked FR/NFR set (step 01)
- Input mode flag
- For Mode 1: BRD slug and feature ID; the feature brief content already in conversation

## Procedure
```

- [ ] **Step 2.3: Gate Phase 1's "Open questions" derivation on Mode 1**

In Phase 1, the current numbered list item 1 reads:
```
1. **Open questions** — every bullet under the brief's `## Edge cases / open questions` section, verbatim. If the section is missing or empty, record "none".
```

Replace with:
```
1. **Open questions (Mode 1 only)** — if input mode is Mode 1, list every bullet under the brief's `## Edge cases / open questions` section, verbatim; if the section is missing or empty, record "none". For Modes 2, 3, 4 → there is no structured open-questions section to extract; record "n/a (non-Mode-1 input)" and skip to item 2.
```

- [ ] **Step 2.4: Gate Phase 2 Part A on Mode 1**

The current Part A block:
```
**Part A — open questions from the brief.**
For each open question:
- If the brief gives context (e.g., "ADR-07 must be resolved"), include it.
- Ask the user. The user answers, or defers with "TBD" / "ask offline".
- Resolved answers → carried to step 13 for inlining into the appropriate spec section.
- Deferred items → list of `(question, target_section)` for `NEEDS_CLARIFICATION` placement in step 13.
```

Replace with:
```
**Part A — open questions from the brief (Mode 1 only).**
If input mode is not Mode 1, SKIP Part A entirely and proceed to Part B. Modes 2/3/4 do not carry a structured open-questions section to surface.

For Mode 1, for each open question:
- If the brief gives context (e.g., "ADR-07 must be resolved"), include it.
- Ask the user. The user answers, or defers with "TBD" / "ask offline".
- Resolved answers → carried to step 13 for inlining into the appropriate spec section.
- Deferred items → list of `(question, target_section)` for `NEEDS_CLARIFICATION` placement in step 13.
```

- [ ] **Step 2.5: Adapt Part C prompt phrasing for all modes**

Current Part C block:
```
**Part C — anything else?**
Final prompt — verbatim:
> "Anything else to add or change beyond what's captured in the feature brief?"
```

Replace with:
```
**Part C — anything else?**
Final prompt, phrased per input mode:
- Mode 1 → "Anything else to add or change beyond what's captured in the feature brief?"
- Mode 2 → "Anything else to add or change beyond what's in your prompt?"
- Mode 3 → "Anything else to add or change beyond what's in the file?"
- Mode 4 → "Anything else to add or change beyond what's in the files?"
```

- [ ] **Step 2.6: Verify the file now has no step-entry Mode gate**

```bash
grep -n "## Mode gate\|SKIP this step entirely" .claude/skills/create-spec/steps/step-04-resolve-open-questions.md
```

Expected: no matches.

```bash
grep -n "Mode 1" .claude/skills/create-spec/steps/step-04-resolve-open-questions.md
```

Expected: matches only inside Goal, Inputs ("For Mode 1: BRD slug …"), Phase 1 item 1 ("Mode 1 only"), and Part A ("Mode 1 only").

- [ ] **Step 2.7: Commit**

```bash
git add .claude/skills/create-spec/steps/step-04-resolve-open-questions.md
git commit -m "fix(create-spec): narrow resolve-open-questions Mode-1 gate to Phase 2 Part A only"
```

---

## Task 3: Promote `docs/patterns/domains/INDEX.md` to load-project-context's always-load list; remove on-demand load from resolve-open-questions

**Acceptance criterion:** AC9 always-load half — `docs/patterns/domains/INDEX.md` appears in step-03's always-load list; the resolve-open-questions step no longer issues an on-demand read for the same file.

**Files:**
- Modify: `steps/step-03-load-project-context.md` (always-load list).
- Modify: `steps/step-04-resolve-open-questions.md` (domain awareness procedure — drop step 1's on-demand read).

### Steps

- [ ] **Step 3.1: Add domain registry INDEX.md to load-project-context always-load**

Edit `.claude/skills/create-spec/steps/step-03-load-project-context.md`:

Current always-load block (lines 10–16):
```
## Loads (always)
- `tenxengage-blueprint/PROJECT-CONTEXT.md` — application-wide standards
- `tenxengage-backend/PROJECT-CONTEXT.md` — backend conventions
- `tenxengage-frontend/PROJECT-CONTEXT.md` — frontend conventions
- `tenxengage-contracts/PROJECT-CONTEXT.md` — contracts conventions
- `tenxengage-contracts/enums-index.md` — enum registry
- `tenxengage-blueprint/docs/patterns/INDEX.md` — pattern registry
```

Replace with:
```
## Loads (always)
- `tenxengage-blueprint/PROJECT-CONTEXT.md` — application-wide standards
- `tenxengage-backend/PROJECT-CONTEXT.md` — backend conventions
- `tenxengage-frontend/PROJECT-CONTEXT.md` — frontend conventions
- `tenxengage-contracts/PROJECT-CONTEXT.md` — contracts conventions
- `tenxengage-contracts/enums-index.md` — enum registry
- `tenxengage-blueprint/docs/patterns/INDEX.md` — pattern registry
- `tenxengage-blueprint/docs/patterns/domains/INDEX.md` — domain registry (load-bearing for slot-filling features; step 04 reads `{domain}.md` on-demand based on user selection)
```

- [ ] **Step 3.2: Remove on-demand load from resolve-open-questions domain awareness procedure**

Edit `.claude/skills/create-spec/steps/step-04-resolve-open-questions.md`:

Current Domain awareness procedure step 1 (line 83):
```
1. Read `docs/patterns/domains/INDEX.md`.
```

Delete this line entirely. Renumber the subsequent steps so step 2 becomes step 1, step 3 becomes step 2, etc.

After the renumber the procedure should start:
```
### Domain awareness procedure (only if `$SLOT_FILLING`)

1. Prompt the user:
   ```
   This feature is filling builder-shaped slots. Which domain does it belong to?
     • incentive  (existing legacy bespoke domain — uses IncentiveAudienceRule etc.)
     • enablement (new domain — anchored on platform primitives)
     • A new domain not listed above
   ```

2. Load the domain file based on selection:
   - `incentive` → read `docs/patterns/domains/incentive.md`.
   - `enablement` → if `docs/patterns/domains/enablement.md` exists, read it; otherwise enter bootstrap flow ...
```

…and so on through what was step 6 (slot addition guard) becoming step 5.

- [ ] **Step 3.3: Verify**

```bash
grep -n "docs/patterns/domains/INDEX.md" .claude/skills/create-spec/steps/step-03-load-project-context.md
```
Expected: one match in the always-load section.

```bash
grep -n "Read \`docs/patterns/domains/INDEX.md\`" .claude/skills/create-spec/steps/step-04-resolve-open-questions.md
```
Expected: NO match (the on-demand read is gone).

```bash
grep -n "docs/patterns/domains" .claude/skills/create-spec/steps/step-04-resolve-open-questions.md
```
Expected: any remaining matches are for `{domain}.md`, `incentive.md`, `enablement.md`, or `platform-primitives.md` — domain-specific files still loaded on-demand based on user selection. NOT `INDEX.md`.

- [ ] **Step 3.4: Commit**

```bash
git add .claude/skills/create-spec/steps/step-03-load-project-context.md .claude/skills/create-spec/steps/step-04-resolve-open-questions.md
git commit -m "feat(create-spec): promote domain registry INDEX to always-load; drop on-demand load"
```

---

## Task 4: SKILL.md + plan-file-schema + cross-step prose sweep

**Acceptance criterion:** No stale step-number references remain anywhere in the create-spec skill or its references.

**Files:**
- Modify: `references/plan-file-schema.md`.
- Modify: any step file (05–16) that references "step 02"/"step 03"/"step 04" in prose.
- Modify (if needed): `SKILL.md`.

### Steps

- [ ] **Step 4.1: Reorder stepsCompleted array in plan-file-schema.md**

Edit `.claude/skills/create-spec/references/plan-file-schema.md` line 12.

Current:
```yaml
stepsCompleted: [parse-input, resolve-open-questions, load-brd-context, load-project-context, detect-feature-shape, load-shape-references, scope-decomposition, security-analysis, events-analysis, test-strategy, permissions-analysis, derive-slug, generate-spec-content, generate-technical-content, write-plan-file]
```

Change to:
```yaml
stepsCompleted: [parse-input, load-brd-context, load-project-context, resolve-open-questions, detect-feature-shape, load-shape-references, scope-decomposition, security-analysis, events-analysis, test-strategy, permissions-analysis, derive-slug, generate-spec-content, generate-technical-content, write-plan-file]
```

- [ ] **Step 4.2: Update plan-file-schema.md line 32 step number**

Current line 32:
```
[2-4 paragraphs: why now, scope, explicit deferrals, de-risking findings from step 04 reading]
```

Change to:
```
[2-4 paragraphs: why now, scope, explicit deferrals, de-risking findings from step 03 reading]
```

(load-project-context is now step 03, not step 04.)

- [ ] **Step 4.3: Sweep step files 05–16 for stale references**

```bash
grep -n "step 02\|step 03\|step 04\|step-02\|step-03\|step-04" .claude/skills/create-spec/steps/step-0[5-9]-*.md .claude/skills/create-spec/steps/step-1[0-6]-*.md
```

For each match, judge whether the referenced step is one of the moved ones, and bump accordingly:
- "step 02" referencing BRD context → still "step 02" (BRD context didn't move from the spec's pov — it was at 03, now at 02). Verify case-by-case.
- "step 02 (BRD digest)" → now correct as "step 02".
- "step 03 (load-project-context)" → was "step 04", now correct as "step 03". Bump down by 1.
- "step 04 (project context)" → now "step 03". Bump down by 1.
- "step 04 (resolve-open-questions)" → now correct as "step 04" — no change.
- References to "step 02" that meant the OLD resolve-open-questions → now "step 04". Bump up by 2.

Apply each fix with the Edit tool, one at a time, after reading the surrounding context to confirm which file/step the reference points at.

**Common patterns to look for** (from a pre-task survey of the create-spec skill prior to amendment):
- `step-05-detect-feature-shape.md` likely references "step 02 (BRD digest)" — that's load-brd-context, now correctly step 02. No change.
- `step-05-detect-feature-shape.md` and others may reference "step 03 (project context)" — that's load-project-context, now correctly step 03 in some files OR was at step 04 in others. Verify each.
- Any reference to "step 02 (resolve-open-questions)" or "step 02 (open questions)" needs to become "step 04".
- Any reference to "from step 03 (load-brd-context)" or "step 03 (BRD context)" needs to become "step 02".

- [ ] **Step 4.4: Check SKILL.md for any step-number prose**

```bash
grep -n "step 0[1-9]\|step 1[0-6]" .claude/skills/create-spec/SKILL.md
```

Expected matches: only lines 25–26, which reference "step 15" / "step 16" (resumption check). Those are unchanged by this amendment.

If any other matches appear, judge per Step 4.3 logic and fix.

- [ ] **Step 4.5: Re-run final routing verification**

```bash
for f in .claude/skills/create-spec/steps/step-*.md; do
  printf "%-50s | header: %-40s | route: %s\n" \
    "$(basename "$f")" \
    "$(head -1 "$f")" \
    "$(grep -E "route to step" "$f" | tail -1 | sed -E 's/.*(route to step [0-9]+:.*)/\1/' | head -c 80)"
done
```

Expected: each step's header line number matches its filename position; each non-terminal step routes to the immediate next step.

- [ ] **Step 4.6: Commit**

```bash
git add .claude/skills/create-spec/
git commit -m "fix(create-spec): sync plan-file-schema + sweep stale step-number prose"
```

---

## Task 5: Verification (acceptance check for AC9)

**Acceptance criterion:** Section 15 #9 — Mode-2 slot-filling input triggers domain detection; Mode-2 non-slot-filling input produces no domain prompts; resolve-open-questions at position 04 after both context loaders; domain registry INDEX in load-project-context's always-load list.

This task is read-only verification + a manual mental walkthrough. The actual `/create-spec` smoke test (with a real Mode-2 input) is a separate manual step, to be run by the user after this plan completes.

### Steps

- [ ] **Step 5.1: Routing graph end-to-end**

```bash
for f in .claude/skills/create-spec/steps/step-*.md; do
  echo -n "$(basename "$f"): "
  grep -E "route to step" "$f" | tail -1 | sed -E 's/.*route to step ([0-9]+).*/-> step \1/' || echo "(terminal)"
done
```

Expected: 01→02, 02→03, 03→04, 04→05, 05→06, …, 15→16, 16 terminal.

- [ ] **Step 5.2: Mode-gate scoping verification**

```bash
grep -n "## Mode gate" .claude/skills/create-spec/steps/step-04-resolve-open-questions.md
```
Expected: no match (step-entry gate removed).

```bash
grep -n "Mode 1 only\|For Mode 1\|Mode 1, for\|Mode 1\b" .claude/skills/create-spec/steps/step-04-resolve-open-questions.md
```
Expected: matches only inside Goal, Inputs, Phase 1 item 1, and Phase 2 Part A. NO match inside Phase 1 items 2–5 (slot-filling detection, builder-shape detection, builder draft, domain awareness reference) or inside Phase 2 Parts B and C or inside the Domain awareness procedure.

- [ ] **Step 5.3: Always-load registry verification**

```bash
grep -n "docs/patterns/domains/INDEX.md" .claude/skills/create-spec/steps/step-03-load-project-context.md
```
Expected: one match inside `## Loads (always)`.

```bash
grep -nc "Read \`docs/patterns/domains/INDEX.md\`" .claude/skills/create-spec/steps/step-04-resolve-open-questions.md
```
Expected: 0.

- [ ] **Step 5.4: Mental walkthrough of AC9**

Walk through, on paper or in your head, both halves of AC9:

**Half 1 — Mode-2 slot-filling input triggers domain detection.**
A `/create-spec` run is invoked with a Mode-2 prompt: `"Build a course creation wizard with sections for basics, lessons, audience, and approval."` Trace through the step chain:
1. Step 01 parses Mode 2; locks FRs.
2. Step 02 (load-brd-context) — Mode 2 → no digest → skip-ahead routes to step 03.
3. Step 03 (load-project-context) — always-load runs; `docs/patterns/domains/INDEX.md` is now in conversation context.
4. Step 04 (resolve-open-questions) — step-entry runs unconditionally (Mode gate is gone).
   - Phase 1 item 1 — Mode 2, so records "n/a (non-Mode-1 input)" and skips to item 2.
   - Phase 1 item 2 — slot-filling detection runs; "builder, audience, approval" all hit; `$SLOT_FILLING = true`.
   - Phase 1 item 3 — `$BUILDER_SHAPED = true`.
   - Phase 1 item 4 — builder draft derived.
   - Phase 1 item 5 — domain awareness fires (because `$SLOT_FILLING = true`).
   - Phase 2 Part A — Mode 2, so skipped.
   - Phase 2 Part B — runs (builder-shaped).
   - Phase 2 Part C — runs with Mode-2 phrasing.
   - Domain awareness procedure runs → user is prompted to pick a domain.

✅ Domain detection fires for Mode-2 slot-filling. Pass.

**Half 2 — Mode-2 non-slot-filling input produces no domain prompts.**
A `/create-spec` run is invoked with: `"Add CSV export to the partner-list page."` Trace:
1. Steps 01–03 as above.
2. Step 04 — Phase 1 item 2 — no builder, no audience model, no eligibility decision, no budget, no approval, no completion semantic → `$SLOT_FILLING = false`.
3. Phase 1 item 5 — domain awareness gated on `$SLOT_FILLING` → skipped.
4. Phase 2 Parts B and C run; Part A skipped (Mode 2).
5. Domain awareness procedure not entered.

✅ No domain prompts for Mode-2 non-slot-filling. Pass.

Confirm in writing that both walkthroughs pass before declaring the task done.

- [ ] **Step 5.5: Hand off to the user for the live smoke test**

After Tasks 1–4 commit cleanly and the verifications in Steps 5.1–5.4 pass, report back to the user:

> "Amendment plan complete and committed. Routing graph verified; Mode-1 gate narrowed to Phase 2 Part A; domain registry promoted to load-project-context's always-load list; no stale step-number prose remains. Ready for AC9 smoke test: please run `/create-spec` on (a) a Mode-2 slot-filling input ('Build a course creation wizard with sections…') and confirm a domain prompt fires after Phase 1, and (b) a Mode-2 non-slot-filling input ('Add CSV export to the partner list') and confirm no domain prompts."

(This live smoke test is the user's call to make and not part of this plan's commits.)

---

## Self-review notes

**Spec coverage check:**
- §3 corrected placement → Task 1 (renames + routing) + Task 4 (cross-step prose).
- §3 "fires for" semantics (always runs; only Part A Mode-1-gated) → Task 2 (Mode gate narrowing).
- §11 step-numbering table → Task 1 (header + boundary updates per file).
- §11 routing-pointer list (4 routes) → Task 1 Steps 1.3, 1.4, 1.5, 1.6.
- §15 #9 acceptance criterion → Task 5 (verification walkthroughs).
- §16 implementation order entries 5+6 (always-load addition + on-demand removal) → Task 3.

**Placeholder scan:** Every step has a concrete bash command, exact file path, or verbatim diff. The "any in-prose references" sweep in Task 4 Step 4.3 lists concrete patterns rather than vague "fix as needed" — the engineer is given the disambiguation logic.

**Type / signature consistency:** N/A — markdown skill files only. The variable names `$SLOT_FILLING`, `$BUILDER_SHAPED`, `$DOMAIN`, `$BUILDER_TYPE` are referenced consistently across Tasks 2 and the verification walkthrough.

**No additional sibling-repo work:** The sibling-repo skill changes from the original implementation continue to apply — they read `docs/patterns/domains/INDEX.md` from disk; the always-load promotion in this amendment is for the `/create-spec` skill's own context loading, not for sibling skills.

**Risk:** Task 4 Step 4.3 (cross-step prose sweep) is the highest-risk step because it's judgment-based. An engineer who mis-bumps a reference could introduce a subtle off-by-one. Mitigation: each grep match is judged in isolation against the disambiguation rules listed, and Task 5 Step 5.4's mental walkthrough catches end-to-end inconsistencies before the user runs the live smoke test.
