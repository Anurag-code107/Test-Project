# Stories Index — {{feature-slug}}

_One row per user story. Each row maps to a `stories/US-NN-*.md` file that is the self-contained execution unit for that story._
_Every story row must produce at least one Playwright E2E test in its story file._

---

## Stories Table

| US | Title | Layers | Actor | Touches Entities | Depends on | Can Parallel With | Story File |
|---|---|---|---|---|---|---|---|
| US-01 | {{title — e.g., "Create course"}} | BE + FE | {{Actor}} | {{Entity1}}, {{Entity2}} | Foundation | US-03 | [stories/US-01-{{slug}}.md](stories/US-01-{{slug}}.md) |
| US-02 | {{title — e.g., "Edit course"}} | BE + FE | {{Actor}} | {{Entity1}} | Foundation, US-01 | — | [stories/US-02-{{slug}}.md](stories/US-02-{{slug}}.md) |
| US-03 | {{title — e.g., "List courses"}} | BE + FE | {{Actor}} | {{Entity1}} | Foundation | US-01 | [stories/US-03-{{slug}}.md](stories/US-03-{{slug}}.md) |

_Layers values: `BE + FE` (full stack), `BE` (no user-visible UI — e.g., event processors, scheduled jobs), `FE` (no new endpoints — reads from existing APIs)._

_"Touches Entities" determines sequential vs parallel: two stories touching the same entity run sequential. Two stories touching different entities can run in parallel once their deps are met._

---

## Dependency graph

```
Foundation (F1 → F2 → F3, F4 → F5)
├── US-01 (create-{{entity}})
│   └── US-02 (edit-{{entity}})       ← same entity, sequential after US-01
├── US-03 (list-{{entities}})          ← disjoint operation, can parallel with US-01
└── US-04 (delete-{{entity}})          ← depends on US-01 (entity must exist)
    └── US-05 (archive-{{entity}})     ← same entity, sequential after US-04
```

---

## Parallelism notes

_Stories that can run concurrently (disjoint entities or disjoint UI pages, shared only Foundation dependency):_
- US-01 and US-03 — independent operations; US-03 only reads, no write conflict

_Stories that must run sequentially (touch the same entity — create before edit/delete):_
- US-02 after US-01 — edit depends on entity existing
- US-05 after US-04 — archive is a status transition on the same entity

---

## Story count

| Total stories | BE-only | FE-only | BE + FE |
|---|---|---|---|
| {{N}} | {{N}} | {{N}} | {{N}} |

_Target: 150–300 lines per story file. If a story's execution checklist exceeds 12–15 items, split it into two stories._

---

## Flow-level Completeness Audit

_Records the story-level completeness probe run during Phase 1.5 of `/create-stories`. Documents flow-level gaps surfaced after reading the spec — distinct from the spec-level probe (which runs earlier in `/create-spec`). If no gaps were found, this section records that result so future readers know the probe ran._

| # | Gap | Resolution | Story / Note |
|---|---|---|---|
| 1 | {{flow-level gap — natural language}} | Added AC to {{US-NN}} | AC-{{N}} added |
| 2 | {{flow-level gap}} | New story | {{US-NN}} created |
| 3 | {{flow-level gap}} | ⚠️ DEFERRED — flow-level gap | {{description of what was deferred}} |

_If no flow-level gaps were identified: "No flow-level gaps identified — the spec-level probe already covered applicable dimensions."_
