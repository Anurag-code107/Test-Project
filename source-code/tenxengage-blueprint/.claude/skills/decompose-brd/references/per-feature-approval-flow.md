# Per-Feature Approval Flow

> Reference used by `/decompose-brd` **step 08 (write-files)** when `decomposeBrdFeatureApproval=true` in `.claude/settings.json`. When the flag is `false` (default), step 08 ignores this file and writes all output files in a single batch.
>
> Three stages, executed in order: A (write common files immediately), B (per-feature approval loop), C (write roadmap last). Stage B may invoke the Change & Impact Flow.

## Stage A — Write common files immediately

Write these files verbatim from the plan right after plan approval (no approval gate — they have no per-feature granularity):
- `roadmaps/{slug}/digest.md`
- `roadmaps/{slug}/digest-annex.md`
- `roadmaps/{slug}/backlog-seeds.csv` *(written now so a working file exists even if the loop is interrupted; may be regenerated in Stage C if seeds change)*

> `roadmap.md` is **not** written here. Deferred to Stage C so it reflects the final approved state of all feature files.

## Stage B — Per-feature approval loop

For each feature file in phase order (F-01 first, following the roadmap's recommended sequence):

1. Print a progress header:
   ```
   [F-NN of TT] {Feature Name} (Phase N) — reviewing...
   ```
   *(TT = total feature count for this roadmap)*

2. Show the **full verbatim content** of that feature brief (copied from the plan — not regenerated).

3. Ask:
   ```
   Approve this feature as-is, or do you have changes? (approve / <describe changes>)
   ```

4. If **approved** → write `roadmaps/{slug}/features/F-NN-{slug}.md` to disk → move to next feature.

5. If **changes requested** → enter the **Change & Impact Flow** below.

> No proactive clarifying questions are asked per feature. The approval prompt is the open invitation — the user surfaces concerns reactively. The `riskiest unknown` field is visible in the content shown.

## Stage C — Write roadmap last

After all feature files are approved and written:

1. Incorporate any changes made during Stage B into the roadmap content (feature table rows, phase assignments, dependency links, recommended start-here).

2. If any features were changed during Stage B, show a diff of the updated roadmap sections and ask:
   ```
   Roadmap updated to reflect feature changes. Confirm? (yes / adjust)
   ```

3. Write `roadmaps/{slug}/roadmap.md`.

4. If any story seeds were added, removed, or renamed during Stage B, offer:
   ```
   Seeds changed in F-NN. Regenerate backlog-seeds.csv? (yes / skip)
   ```
   If yes, regenerate and overwrite `roadmaps/{slug}/backlog-seeds.csv`.

## Change & Impact Flow

> *Entered from Stage B step 5 when the user requests changes instead of approving.*

**Step 1 — Apply change to current feature (in memory).** Rewrite the in-memory content of F-NN with the requested change. Do not write to disk yet.

**Step 2 — Targeted impact scan.** Scan all other feature briefs (both already-written and not-yet-written) for overlapping content:

| What changed | Where to scan in other features |
|---|---|
| Business noun / domain concept | Business outcome, FRs, story seed titles and outcomes |
| Feature name / slug | `Dependencies` field of other features |
| Recommended phase | Phase assignments of features that depend on this one |
| Story seed cross-reference | `depends_on` fields in other features' story seeds |
| Primary persona | Persona fields of features sharing the same journey |

The scan is **targeted** — it flags specific fields, not whole features.

**Step 3 — Show impact summary.** Before writing anything:

```
Change applied to F-04 (in memory). Impact detected:
  • F-02 (already written) — dependency list references the renamed concept
  • F-07 (not yet reviewed) — FR-07.3 and story seed S-02 reference the same business noun
Proceeding will re-write F-02 immediately and pre-apply changes to F-07 before you review it.
Confirm? (yes / adjust)
```

If no impact detected:
```
Change applied to F-04. No cross-feature impact detected.
Writing F-04...
```

**Step 4 — Execute.**
- **If yes:**
  1. Write F-NN to disk with the change applied.
  2. For each **already-written** backward feature in the impact list: re-write its file immediately with the targeted update (specific fields only — not a full regeneration).
  3. For each **not-yet-written** forward feature in the impact list: pre-apply the change to its queued in-memory content so the user sees the updated version when its turn comes.
  4. Continue to the next feature in the loop.
- **If adjust:** User refines the change. Re-run from Step 2 with the revised change.