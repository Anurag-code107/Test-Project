# Advisory reconciliation

Reference loaded by step 02 (`load-brd-context`) when `digest-annex.md` exists. Reconciles BRD-stated technical names against the actual codebase.

---

## When this runs

Only when both:
1. A digest applies (per `brd-digest-handling.md` detection signals)
2. `roadmaps/{slug}/digest-annex.md` exists

If digest-annex is absent, skip this entirely.

---

## Procedure

For each entity / event / API operation / error code listed in `digest-annex.md` that's likely relevant to this feature:

### 1. Search the contracts repo

```bash
ls tenxengage-contracts/models/
grep -i "{name}" tenxengage-contracts/models/*.md tenxengage-contracts/enums.md
```

### 2. Search the backend

```bash
find tenxengage-backend/src/main/java/com/tenxengage/app/entity/ -iname "*{name}*"
find tenxengage-backend/src/main/java/com/tenxengage/app/event/ -iname "*{name}*"
```

### 3. Apply the decision rule

| Codebase finding | Decision | Recording |
|---|---|---|
| Equivalent exists with the SAME name | Adopt the codebase name | No reconciliation note needed |
| Equivalent exists with a DIFFERENT name | **Prefer the codebase name** | Record in spec.md `### Naming reconciliation` sub-section: "BRD called this `{BRD name}`; codebase uses `{codebase name}` — using codebase name." |
| No codebase equivalent exists | BRD-stated name is a candidate | Final name follows platform conventions, not BRD verbatim. Surface to user if the name looks non-idiomatic. |
| Unresolvable (ambiguous match, conflict) | STOP and surface to user | Don't guess. |

---

## What this is NOT

- An automatic name-change script. The reconciliation produces RECOMMENDATIONS that are recorded in the spec.
- A spec for codebase entities that don't yet exist. New entities get spec'd normally; the BRD name is a starting suggestion only.
- A digest update mechanism. If the BRD has the wrong name across the board, that's a digest-evolution concern (out of scope for create-spec).

---

## Output

Reconciliation notes are recorded in conversation context, then appear in spec.md's `### Naming reconciliation` sub-section (within Overview or Data Model section) — written by step 12.

---

## Common gotchas

- **Don't silently adopt BRD names over codebase names.** This rule exists because BRD authors aren't always familiar with codebase vocabulary. Codebase wins.
- **Don't reconcile irrelevant items.** The annex may list dozens of names; only reconcile items likely relevant to THIS feature.
- **Surface ambiguous matches.** If two candidate codebase names match an annex item, the user picks.