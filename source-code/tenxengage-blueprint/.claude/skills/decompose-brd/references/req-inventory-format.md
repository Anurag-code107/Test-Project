# REQ Inventory Format

The `/decompose-brd` skill produces a flat requirement inventory across step 04 (BRD-verbatim items) and step 04c (gap-resolution items). It is consumed by step 05 (FR source-tagging), step 06 (coverage check), and pinned to the plan file by step 07.

## Format

Each item is one line, five `||`-delimited positional fields:

```
{id}||{verbatim text}||{anchor label}||{semantic class}||{origin or gap-ref}
```

| # | Field | Content | Empty allowed |
|---|---|---|---|
| 1 | id | `REQ-NNN`, zero-padded sequential | No |
| 2 | verbatim text | BRD statement as written; for gap-derived items, the user's interpretation rendered as a capability statement | No |
| 3 | anchor label | BRD heading text where the item lives (no §-numbers); for gap-derived items, the most authoritative anchor from the gap's `anchors[]` | No |
| 4 | semantic class | Judgment label: `capability-statement`, `observable-behavior-promise`, `user-story`, `kpi-commitment`, etc. | No |
| 5 | origin or gap-ref | Empty → BRD-verbatim item; `gap:{slug}` → gap-resolution item where `{slug}` matches a `gapResolutions[].gap` slug from step 04c | Yes (when BRD-verbatim) |

Records are separated by newlines. One inventory item per line.

## Why `||` (double pipe)

- 1-token sequence in the Claude tokenizer (ASCII).
- Essentially zero collision risk in BRD prose. Single `|` appears in BRD tables; `||` does not appear in normal business text.
- ASCII-safe — no encoding surprises across consumers (the plan file pinned by step 07, downstream `/create-spec` reading the plan).

## Delimiter collision policy

If a verbatim BRD requirement contains the literal sequence `||` (rare; would only arise in code samples or unusual table syntax embedded in prose), step 04 replaces it with `/` in the inventory text and notes the substitution in conversation context. The inventory is downstream-of-BRD scaffolding; the `digest.md` (which preserves verbatim BRD content for human consumption) is unaffected.

## Worked example

```
REQ-001||Admin can configure certification programs with passing thresholds and validity periods||Certification Management||capability-statement||
REQ-002||System publishes a certification_lapsed business fact when a user's certification expires||Certification Management||observable-behavior-promise||
REQ-047||Vendor PSM can push specific training to any participant in one click from within the deal collaboration room||Deal Collaboration||capability-statement||
REQ-052||System surfaces training recommendations within 30 minutes of triggering deal event||Recommendations Engine||observable-behavior-promise||
REQ-088||Admin can author inline knowledge-check quizzes scoped to a single lesson||Assessments and Quizzes||capability-statement||gap:assessments-umbrella
REQ-089||Admin can author end-of-course graded quizzes scoped to a course||Assessments and Quizzes||capability-statement||gap:assessments-umbrella
REQ-090||Admin can author certification exams as a distinct authoring flow within the course builder||Certification Management||capability-statement||gap:cert-exam-authoring
```

REQ-001 through REQ-052 are BRD-verbatim (field 5 empty). REQ-088, REQ-089, REQ-090 came from step 04c clarifications: an umbrella-term gap "assessments" split into three subtypes (the first two share a gap-ref because they came from one umbrella resolution; the third is a separate gap entirely).
