### 5c. Production reference discovery

Read the Screen Pattern Mirror in `.claude/skills/create-mockups/SKILL.md → Phase 3`. For each component or page this story creates (from `## FE tasks [FE]`), identify the matching Mirror row by screen type and read the referenced production file(s). The Mirror is the single source of truth — never invent screen-to-file mappings ad hoc.

For each FE task:
1. Determine the screen type: list / detail / settings / form / builder-entry-menu / builder-type-selector / builder-template-picker / builder-existing-picker / builder-wizard-body / builder-config-tab / builder-config-standalone / dashboard.
2. Find the matching Mirror row. Read the referenced file(s) in full.
3. If no Mirror row matches: declare "no production analog" for that task (used by the provenance comment in Step 6 and by the final report block in Step 14).

This replaces the existing "one existing hook, one component test, one Playwright spec" arbitrary-sampling rule for production-component discovery. The arbitrary-sampling rule remains useful for hook and test patterns (TanStack Query shape, Vitest conventions, Playwright spec shape) but is no longer the primary fidelity anchor.

**Behavioral reuse gate (data hooks, parsers, SSE/stream handlers — mandatory):** Screen-type Mirror anchors *visual* fidelity only. For every data hook, SSE/stream parser, or service call this story needs, FIRST search for an existing working implementation in the SAME data domain before writing new: `grep -rln "useAiChat\|EventSource\|text/event-stream\|{Entity}DetailResponse" src/`. If an existing hook already parses this exact stream/response (e.g. an AI-copilot SSE parser, a detail-response hook), REUSE it or extend it — do NOT re-implement the parser from scratch and do NOT copy a sibling feature's thinner hook. Cloning a same-screen-type component from a DIFFERENT feature does not satisfy this gate: the data layer must come from the matching domain. Record reused hooks in the provenance comment; for any new hook, state "no existing domain hook — building new because X" in the Step 14 report. **If NO existing domain hook/parser is found, do not dead-end:** build new and record the one-line "no analog — building new because X" justification in the story Notes and the Step 14 report's no-analog slot. A new parser/hook that duplicates an existing one is a Step 9.5 blocker.

## Next step

Read `subagent/step-06-implement-tasks.md`.
