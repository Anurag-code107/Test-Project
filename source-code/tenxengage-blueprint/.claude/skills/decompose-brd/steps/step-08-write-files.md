# Step 08: write-files

**Goal:** Write the actual roadmap files to disk. Copy verbatim from the plan; do not regenerate.

**Inputs:** Approved plan file from step 07.

> **Step 08 — Writing the actual files. Copy verbatim from the plan; do not regenerate.**

## Step 0 — Check the feature approval flag

Read `.claude/settings.json`:
```bash
python3 -c "import json; d=json.load(open('.claude/settings.json')); print(d.get('decomposeBrdFeatureApproval', False))" 2>/dev/null || echo False
```

- Output `True` → load `../references/per-feature-approval-flow.md` and follow Stage A → Stage B → Stage C with per-feature approval gates and cross-feature impact scanning.
- Output `False`, missing key, or file-read error → continue with the **single-batch write** path below (the common case).

## Read the plan file first

Read the plan file written by step 07. Copy all content verbatim from the `### File: roadmaps/...` sections. Do NOT regenerate or reinterpret content.

## Step 1 — Create the roadmap branch in blueprint

Before writing any files. Run this single compound command verbatim — do not substitute any value for `${ROADMAP_BASE_BRANCH:-main}` yourself:

```bash
git checkout roadmaps/{slug} 2>/dev/null || git fetch origin roadmaps/{slug}:roadmaps/{slug} 2>/dev/null && git checkout roadmaps/{slug} || _b="${ROADMAP_BASE_BRANCH:-main}" && echo "Creating roadmap branch from: $_b" && git checkout -b roadmaps/{slug} "$_b"
```

## Step 2 — Create sub-folders

```bash
mkdir -p roadmaps/{slug}/features
```

## Step 3 — Write files (single-batch path)

> If the flag was `True` in Step 0, skip this step and follow the per-feature approval flow loaded earlier instead.

Write all files verbatim from the plan in one pass:
- `roadmaps/{slug}/digest.md`
- `roadmaps/{slug}/digest-annex.md`
- `roadmaps/{slug}/roadmap.md`
- `roadmaps/{slug}/features/F-NN-{slug}.md` (one per feature)
- `roadmaps/{slug}/backlog-seeds.csv`

Update the plan file's `filesWritten` frontmatter array with each filename as you go (so a mid-step interruption can be resumed by step 01's resume check).

> Do NOT create feature folders or stub specs — that's `/create-spec`'s job per slice. The roadmap branch (`roadmaps/{slug}`) is created here; individual feature branches (`features/{feature-slug}`) are created by `/create-spec`.

## Step 4 — Ask about commit + push

- If user says YES:
  ```bash
  git add roadmaps/ && git commit -m "roadmap: decompose {slug} BRD into N features" && git push -u origin roadmaps/{slug}
  ```
- If NO: print the commands above so the user can run them later.

## Step 5 — Show next steps

Use the recommended start-here from the plan:

```
Roadmap created on branch: roadmaps/{slug}

Files written:
  • roadmaps/{slug}/digest.md
  • roadmaps/{slug}/digest-annex.md
  • roadmaps/{slug}/roadmap.md
  • roadmaps/{slug}/features/F-01-{slug}.md
  • ... (N feature files total)
  • roadmaps/{slug}/backlog-seeds.csv

Recommended start-here: F-NN ({Feature Name})

Next steps:

  1. [Spec the first feature] /create-spec {slug} F-NN
     Creates features/{feature-slug} branch off roadmaps/{slug} automatically.
     Reads features/F-NN-*.md + digest.md + digest-annex.md — no copy-paste needed.

  2. [Review the spec] /review-spec {feature-slug}
     After /create-spec produces the draft.

  3. [Stories + Foundation] /create-stories {feature-slug}
     After /review-spec passes.

All features in this roadmap branch off roadmaps/{slug}.
When all features are done and tracker is all-green, open one PR: roadmaps/{slug} → main.
Pick another feature from the roadmap when ready. Each /create-spec run is independent.
```

## Rules in scope for this step

- **No feature folders, no feature branches** — `features/{feature-slug}/` folders and `features/{feature-slug}` branches are created by `/create-spec` per slice. This step creates only the `roadmaps/{slug}` integration branch and the roadmap files within it.
- **PM-friendly export always emitted** — `backlog-seeds.csv` is always written.
- **Digest-annex always emitted** — stub mode if BRD has no technical-truth artifacts.
- **Recommended `/create-spec` invocation uses the identifier** — every feature's invocation is `/create-spec {roadmap-slug} F-NN`. The downstream skill reads `features/F-NN-*.md`, `digest.md`, and `digest-annex.md` automatically; no copy-paste prompt needed.
- **Per-feature approval is opt-in** — controlled by `decomposeBrdFeatureApproval` in `.claude/settings.json`. Default is `false` (absent key = false). When `true`, follow `references/per-feature-approval-flow.md`.

## Routing

All files written, user shown next steps → load `steps/step-09-finalize.md`.