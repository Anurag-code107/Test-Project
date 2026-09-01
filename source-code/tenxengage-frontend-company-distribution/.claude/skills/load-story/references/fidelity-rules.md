# Fidelity Rules — load-story FE

Read by subagent/step-06-implement-tasks.md when implementing FE components.

**Provenance comment (mandatory).** Every new `.tsx` file under `src/components/`, `src/pages/`, or `src/hooks/` that this session creates must begin with a one-line provenance comment as its first content line (before any imports). Format:

- If the component was adapted from a mockup: `// Adapted from: src/mockups/{feature-id}/{ScreenName}.tsx (mockup) + src/components/incentive-builder/EntryMenu.tsx (production analog from Mirror)`
- If from a mockup only (no Mirror match): `// Adapted from: src/mockups/{feature-id}/{ScreenName}.tsx (mockup); no production analog`
- If from a Mirror reference only (no mockup): `// Adapted from: src/components/incentive-builder/EntryMenu.tsx (production analog from Mirror)`
- If neither mockup nor Mirror analog exists: `// Adapted from: none — no production reference`

Hooks created in `src/hooks/` use the same format but reference the hook pattern source (e.g., `// Adapted from: src/hooks/useIncentives.ts (TanStack Query pattern)`).

This is the structural audit trail in production code. `grep -r '// Adapted from:' src/` lists every component's origin; `grep -r '// Adapted from: none' src/` finds every freely-designed component.

**Fidelity rule (production code).** When adapting from a mockup (Step 5b) or a Mirror reference (Step 5c):
- **Copy verbatim:** outer container, spacing scale (`gap-*`, `space-*`, `p-*`, `m-*`), layout classes (`flex`, `grid`, `justify-*`, `items-*`), hover/transition classes (`hover:*`, `transition-*`, `duration-*`), animation classes (`animate-*`, Framer Motion variants), CSS custom property references (HSL tokens like `text-primary`, `bg-card`).
- **Allowed to differ:** inner text content (will be real strings, not mock copy), icon choice (must still be from `lucide-react`), entity-specific labels, real data fields from hooks instead of mock arrays, real type imports.
- **If you're tempted to write a class string that doesn't appear in the mockup or in the Mirror reference, stop and re-read the source.** Do not paraphrase classes. Do not "improve" the production reference. Do not introduce new HSL tokens — only use what's defined in `src/index.css`.

This rule applies whether the source is a mockup file (Step 5b) or a production analog file (Step 5c).
