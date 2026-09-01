---
name: "ui-ux-review"
description: "Review frontend UI/UX quality against project design standards. Checks design consistency, responsiveness, state handling, and accessibility. Can be run standalone or as part of ready-check."
argument-hint: "Optional: specific file paths to review (defaults to all changed files)"
user-invocable: true
---

## User Input

```text
$ARGUMENTS
```

---

## Required Reading

1. Read `CLAUDE.md` — stack conventions
2. Read `src/index.css` — HSL color tokens and theme variables
3. Glob `src/components/ui/*.tsx` — available shadcn/ui components
4. Read `src/config/currencies.ts` — currency formatting system
5. Read 1-2 existing page components to understand existing UI patterns

---

## Files to Review

- If user specified file paths, review those
- Otherwise: `git diff main --name-only --diff-filter=ACMR` filtered for `*.tsx` files

---

## Review Checklist

### 1. Design Consistency
- [ ] Colors use HSL tokens from CSS custom properties — no hardcoded hex (`#3B82F6` → `text-primary`)
- [ ] UI elements use shadcn/ui components from `@/components/ui/` — no custom buttons, inputs, dialogs
- [ ] Icons use `lucide-react` exclusively
- [ ] Spacing uses Tailwind utilities (`p-4`, `gap-2`, `space-y-3`) — no inline margin/padding styles
- [ ] Typography uses Tailwind text classes — no custom font-size or font-weight values
- [ ] Consistent use of `cn()` helper for conditional class merging

### 2. Responsive Design
- [ ] Mobile-first: base styles for mobile, progressive enhancement with `sm:`, `md:`, `lg:`
- [ ] Tables have horizontal scroll wrapper on small screens
- [ ] Forms stack vertically on mobile, side-by-side on desktop where appropriate
- [ ] Dialog/sheet widths are responsive (`w-full sm:max-w-md`)
- [ ] No fixed-width containers that break on mobile

### 3. State Handling
- [ ] **Loading**: Skeleton components (not spinners) while data fetches
- [ ] **Error**: User-friendly error message with retry action
- [ ] **Empty**: Descriptive message + optional CTA when list/table has 0 items
- [ ] **Success**: Toast notification (sonner) for successful mutations
- [ ] **Pending**: Disabled buttons during form submission, loading indicator

### 4. Accessibility
- [ ] All interactive elements are keyboard-navigable (Tab, Enter, Escape)
- [ ] Form inputs have proper `<label>` elements (not just placeholder text)
- [ ] Color contrast meets WCAG AA standards
- [ ] Custom interactive elements have appropriate ARIA attributes
- [ ] Focus is managed on modal/dialog open and close
- [ ] Images have alt text

### 5. Animations & Transitions
- [ ] Uses Framer Motion patterns consistent with existing animations (if applicable)
- [ ] Page transitions use existing `route-in` / `fade-in` keyframes
- [ ] No jarring layout shifts — content areas have min-height or skeletons
- [ ] Animations respect `prefers-reduced-motion`

### 6. Currency & Number Formatting
- [ ] If feature displays currency: uses `getCurrency()` from currencies config
- [ ] Monetary values formatted with proper decimals and symbols
- [ ] Large numbers formatted with locale-appropriate separators

---

## Output Format

```
=== UI/UX REVIEW ===

Issues found: {N}

CRITICAL:
  1. [{Category}] {file}:{line} — {Issue description}

WARNINGS:
  2. [{Category}] {file}:{line} — {Issue description}

PASSED: {list of checks that passed}
```

---

## Auto-fix & Report Update

- Auto-fix issues where the fix is clear (hardcoded hex → Tailwind class, missing loading state → add skeleton)
- Get branch name via `git branch --show-current`
- If `.ready-check/{branch-name}/review.json` exists, update the `ui-ux-review` step and write an archive snapshot to `.ready-check/{branch-name}/review_{YYYY-MM-DD}_{short-commit}.json`

---

## Structured Output Mode

When the prompt starts with `[STRUCTURED-OUTPUT]`, you are being called by the ready-check orchestrator. In this mode:

1. Perform the Required Reading (CLAUDE.md, src/index.css, shadcn/ui components) then apply the review checklist to the specified `.tsx` files
2. Return **ONLY** the JSON below — no markdown, no explanation, no preamble

```json
{
  "skill": "ui-ux-review",
  "status": "passed|failed",
  "findings": [
    {
      "severity": "critical|high|medium|low",
      "file": "src/pages/FeaturePage.tsx",
      "line": 42,
      "rule": "design-consistency|responsive|loading-state|error-state|empty-state|accessibility|animation|currency-format",
      "message": "Brief description of the UI/UX issue",
      "suggestion": "How to fix it",
      "autoFixable": true
    }
  ],
  "filesReviewed": ["src/pages/FeaturePage.tsx"],
  "summary": "N UI/UX issue(s) found across M file(s)"
}
```

**`status` rules:** `"failed"` if any `critical` or `high` finding (hardcoded colors, missing loading/error/empty states). `"passed"` otherwise.

**Do NOT apply fixes** in structured mode — findings only.
