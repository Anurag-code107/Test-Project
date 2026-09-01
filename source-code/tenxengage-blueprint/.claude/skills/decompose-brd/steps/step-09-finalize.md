# Step 09: finalize

**Goal:** Offer ClickUp seeding. Print wall time. Terminal state.

**Inputs:** Roadmap files written and next-steps shown by step 08.

> **Step 09 — Final.**

## File-presence sanity check

Before continuing to the ClickUp seeding offer, verify that all expected files were actually written to disk by step 08. This is filesystem-level sanity (did the write succeed?), not content validation — content validation is owned by step 06's pre-write gate.

For each file path in the plan file's `filesWritten` frontmatter list, confirm the path exists. If any file is missing, abort with a clear error:

```
Step 09 file-presence check failed.
Expected file not found: {path}

This indicates step 08 did not complete successfully. Output may be
inconsistent. Inspect the roadmap directory and rerun /decompose-brd
if needed.
```

If all files are present, continue to ClickUp seeding.

**This step does NOT re-check content.** No coverage hole detection, no cross-reference re-verification, no phase chain re-validation. Step 06 owns those checks pre-write. Re-running them here would be redundant, slow, and would invite split responsibility.

## ClickUp seeding (optional)

Ask the user:

```
Would you like to seed this roadmap to ClickUp now?
This will create the Epic → Feature → Story hierarchy in your configured list.
(yes / no — you can also run /seed-clickup later)
```

**If no:** Print and continue to Wall Time:
```
Tip: run `/seed-clickup` any time to create the ClickUp hierarchy for this roadmap.
```

**If yes:** Run the seed-clickup flow inline. The roadmap slug is already known (`{slug}`). Skip seed-clickup Phase 1 Step 2 (roadmap picker). Execute:
- seed-clickup Phase 1 Steps 1, 3, 4 (token check, read Epic title, resolve List ID)
- seed-clickup Phase 2 (load cache, parse CSV)
- seed-clickup Phase 3 (create Epic → Features → Stories)
- seed-clickup Phase 4 (save cache, print summary)

Then continue to Wall Time.

## Wall time

Final step:

```bash
echo "⏱ decompose-brd wall time: $(($(date +%s) - $(cat /tmp/decompose_brd_start)))s"
```

## Routing

Terminal state. No further routing.