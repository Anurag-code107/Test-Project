### 5b. Mockup as verbatim fidelity anchor

Check the story frontmatter `mockup_file` field captured in Step 2:

- **If `mockup_file` is a real file path** (not `null`, `N/A`, or absent): read the file at that path. The mockup is the verbatim visual reference for Step 6 — **not a suggestion, not a starting sketch, the source of truth for visual fidelity**.

  When implementing components in Step 6:
  - **Copy the mockup's JSX structure** for each section the story creates. Preserve the outer container element, child element ordering, and section boundaries.
  - **Copy the mockup's Tailwind classes verbatim** for: outer container, spacing scale (`gap-*`, `space-*`, `p-*`, `m-*`), layout (`flex`, `grid`, `flex-row`, `flex-col`, `justify-*`, `items-*`), hover/transition (`hover:*`, `transition-*`, `duration-*`), and animation (`animate-*`, Framer Motion variants).
  - **Swap only**: hardcoded mock data for real query data from hooks; inline types for imported types from `src/types/`; mock event handlers for real service calls; mock copy for real i18n strings (if applicable, otherwise keep the mockup's copy verbatim — it is the spec).
  - If the mockup's JSX has comments at the top (`// Covers:`, `// Mirrors:`, etc., per the /create-mockups spec), DO NOT carry them into production code. The production component's own provenance comment (Step 6) replaces them.

  Announce: "Mockup found at `{mockup_file}` — adopting its JSX as the visual fidelity anchor."

- **If `mockup_file` is `null`, `N/A`, or absent:** fall back to the production references discovered in Step 5c. Announce only if there are no Mirror matches either (then this is a no-analog implementation — see the Step 6 provenance comment rule).

## Next step

Read `subagent/step-05c-production-ref-discovery.md`.
