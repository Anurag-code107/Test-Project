# Learnings Log

Append-only record of findings promoted from ready-check reports to project docs.
Not referenced anywhere — exists to track the rate of new pitfalls discovered over time.
A declining number of entries per feature signals the conventions are working.

---

## 2026-05-15 — wallet-ledger-foundation

| Rule | Category | Applied to |
|---|---|---|
| Layout wrapper components that render `<main>` must include `aria-label="Main content"` for screen reader navigation | accessibility | PROJECT-CONTEXT.md |

---

## 2026-05-15 — features/redemption-catalog (90d7c8c)

### [HIGH] z.coerce.number() coerces empty string to 0 in optional override fields
- **File:** `src/components/redemption-catalog/ItemConfigPanel.tsx`
- **Source:** adversarial-review (blocking)
- **Promoted to:** `docs/patterns/form-handling.md` § Optional Numeric Override Fields; `PROJECT-CONTEXT.md` Anti-Patterns
- **Fix:** `z.preprocess((v) => (v === "" || v == null ? undefined : v), z.coerce.number().min(0).optional())` + spread-omit pattern in request payload.

### [MEDIUM] useEffect syncing server data into react-hook-form via reset()
- **Files:** `ItemConfigPanel.tsx`, `TenantRedemptionSettingsForm.tsx`
- **Source:** code-review (advisory)
- **Promoted to:** `docs/patterns/form-handling.md` § Syncing Server Data into react-hook-form
- **Note:** Accepted pattern for edit forms, but triggers an extra render per dependency change. Using `reset()` over `setValue` field-by-field is preferred.

---

## 2026-05-26 — redemption-flow

| Rule | Category | Applied to |
|---|---|---|
| Never import lucide icons via ESM sub-paths (`lucide-react/dist/esm/icons/*`) — lacks TS declarations, breaks `tsc -b`. Use `import { Icon } from "lucide-react"` only. | react-patterns | PROJECT-CONTEXT.md Anti-Patterns |

