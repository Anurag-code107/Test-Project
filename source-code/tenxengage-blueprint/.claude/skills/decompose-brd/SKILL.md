---
name: "decompose-brd"
description: "Use when user says 'decompose a BRD', 'slice a BRD', 'break down a BRD', or provides a BRD/PRD that describes an initiative-scoped requirement (multiple features, multiple personas, multi-phase plan). Produces a feature roadmap and a BRD digest. Run BEFORE /create-spec when the input is initiative-scoped, not feature-scoped."
argument-hint: "Path to a BRD file (PDF, MD, DOCX) or pasted BRD text"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

You **MUST** consider the user input before proceeding.

This skill runs in **plan mode**. It produces a plan file containing the proposed roadmap and digest, the user reviews and approves, then the actual files are written.

---

## Purpose & When to Use This Skill

`/decompose-brd` is the upstream complement to `/create-spec`. Use it when the BRD describes a **multi-feature initiative**, not a single feature. Symptoms an input is initiative-scoped:

- Multiple personas with different jobs-to-be-done
- Multiple capability areas / functional sections that could ship independently
- A pre-committed Phase 1 / Phase 2 / Phase 3 plan (or similar phasing)
- Multiple sub-agents or modules within one document
- Multiple recommended backend modules / frontend areas
- Cross-quadrant integration points

If the input is already a single well-scoped feature (one persona, one journey, one module, ≤5 entities), **skip this skill — go straight to `/create-spec`.** Tell the user this; don't run unnecessary slicing.

This skill produces five artifacts: `roadmaps/{slug}/digest.md`, `digest-annex.md`, `roadmap.md`, one `features/F-NN-{slug}.md` per feature, and `backlog-seeds.csv`. It does NOT create feature folders, registry rows, or branches.

---

## Architecture: step-file orchestration

This skill executes as a sequence of **step files** under `steps/`. Only one step file is loaded into context at a time — load the next step **only when the current step's routing condition is met**.

**Sequential. No skipping. No optimization.**

| Step | File | Phase mapping |
|---|---|---|
| 01 | `steps/step-01-parse-brd.md` | Parse BRD, lock structural understanding |
| 02 | `steps/step-02-catalog-reconcile.md` | Persona + partner-type reconciliation |
| 03 | `steps/step-03-required-reading.md` | Project context |
| 04 | `steps/step-04-probe-extract.md` | Probe vague topics + classify all BRD content |
| 04c | `steps/step-04c-completeness-audit.md` | Pre-slice BRD completeness audit — orphan refs, umbrella terms, producer-consumer gaps |
| 05 | `steps/step-05-slice.md` | Slice + per-feature FRs + story seeds |
| 06 | `steps/step-06-challenge-validate.md` | Strategic challenge + pre-write validation |
| 07 | `steps/step-07-write-plan.md` | Plan file with verbatim content |
| 08 | `steps/step-08-write-files.md` | Write files to disk on roadmap branch |
| 09 | `steps/step-09-finalize.md` | ClickUp seeding offer + wall time |

References (loaded only by the step that needs them):
- `references/system-catalog.md` — used by step 02
- `references/brd-artifact-patterns.md` — used by step 04
- `references/per-feature-approval-flow.md` — used by step 08 only when `decomposeBrdFeatureApproval=true`

Templates (used by step 07 to produce verbatim plan content; copied to disk by step 08):
- `templates/digest.md`, `templates/digest-annex.md`, `templates/roadmap.md`, `templates/feature.md`, `templates/backlog-seeds.csv`

## Step processing rules

- **Just-in-time loading.** Load the current step file only. Never preload a future step.
- **Self-contained steps.** Each step file states its goal, inputs, body, in-scope rules, and routing.
- **Routing is explicit.** Each step ends with a `## Routing` section that names the next step file. Do not infer.
- **State is in conversation context** for steps 01–06, and in the plan file frontmatter (`stepsCompleted`, `filesWritten`) for steps 07+. Step 01 checks for an existing plan file on entry; if `stepsCompleted` is present it routes directly to step 08 or 09 based on `filesWritten` state.
- **Mentality (carried across steps):** strategic slicing partner. Preserve business-truth verbatim. Treat technical-truth as advisory. Always probe UX journey + persona-of-record + workflow-vs-tool per slice. Push back where the BRD is vague, contradictory, or over-scoped. Synthesize phasing rather than copy it.

## Initialize

Run this immediately, before loading step 01:

```bash
date +%s > /tmp/decompose_brd_start && echo "⏱ decompose-brd started: $(date '+%H:%M:%S')"
```

## Begin

Load `steps/step-01-parse-brd.md` and execute it.