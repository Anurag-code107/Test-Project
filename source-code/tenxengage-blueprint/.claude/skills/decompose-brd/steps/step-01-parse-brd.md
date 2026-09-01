# Step 01: parse-brd

**Goal:** Parse the BRD input, extract a structural summary (Parts A–G), present it to the user, and lock confirmed decisions before any slicing begins.

**Inputs:** `$ARGUMENTS` from the orchestrator — a file path (PDF/MD/TXT/DOCX), multiple paths, or pasted text.

**Resume check (run first):** If `roadmaps/{slug}/.decompose-plan.md` already exists with a `stepsCompleted` array, a previous run reached step 07 or later. Branch:
- `stepsCompleted` includes `write-plan` and `filesWritten` is incomplete → load `steps/step-08-write-files.md`.
- `stepsCompleted` includes `write-plan` and all files are written → load `steps/step-09-finalize.md`.
- No plan file or earlier state → continue with this step from the top. Steps 01–06 are not resumable mid-run since no plan file exists yet.

> **Phase 0 — Parsing the BRD.** Detecting input format, extracting structural facts, confirming with you before any slicing begins.

The input can come in different forms. Detect and parse:

1. **Single file path** (e.g., `/decompose-brd /path/to/brd.pdf`) — read the file. Supports `.pdf`, `.md`, `.txt`, `.docx`.
2. **Multiple file paths** — read all and synthesize.
3. **Direct text** — use as-is.

After reading, extract a structural summary. Present these to the user as a numbered list and ask for confirmation:

**Part A — Identity**
- Proposed slug (kebab-case, derived from BRD title — e.g., "Partner Revenue Readiness" → `partner-revenue-readiness`). User can override.
- Module / quadrant the BRD belongs to (if stated).
- BRD version (if versioned).

**Part B — Scope**
- One-sentence vision (extracted from BRD's vision/purpose section).
- What this BRD owns (explicit scope statements).
- What this BRD does NOT own (explicit non-goals / out-of-scope).
- Integration points to other modules / quadrants.

**Part C — Personas**
- Full persona list as the BRD defines them (vendor + partner sides if applicable).

**Part D — Capabilities**
- Distinct functional areas / sub-agents / modules (one bullet each).

**Part E — Phasing intent**
- Does the BRD pre-commit to Phase 1 / 2 / 3 (or similar)? If yes, summarize what's in each phase. If no, note it.
- Exit gates per phase (if defined).

**Part F — Open decisions / ADRs**
- Any explicit ADR log (timing, owner, what they block)?
- Any inline "X or Y" decisions that look blocking?

**Part G — Initial scope assessment**
Estimate: this BRD looks like ~N shippable features. (For multi-section, multi-persona BRDs — agent-driven, data-product-driven, or workflow-driven: 6-10 features is typical.)

Ask: **"Is this understanding correct? Anything to correct, add, or override (especially the slug)?"**

Record the user's confirmations. They will be pinned in the plan file as "Phase 0 + 0.5 answers (locked)" when step 07 writes the plan.

## Rules in scope for this step

- **Anchor labels, not section numbers** — every reference to BRD topics uses heading text, never §-numbers that may not exist in a differently structured BRD.
- **BRD-template-agnosticism** — work on any BRD shape; detect content patterns, not section structure.

## Routing

User confirms the BRD understanding → load `steps/step-02-catalog-reconcile.md`.