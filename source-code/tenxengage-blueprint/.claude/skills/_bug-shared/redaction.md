---
name: redaction
description: Regex patterns and replacement rules for redacting sensitive data before any external write (ClickUp, MR descriptions, bugs-index.md). NOT applied to local bugs-evidence/ folder.
type: reference
---

# Sensitive Data Redaction

Applied to text written to **external** destinations: ClickUp task descriptions/comments, GitLab MR descriptions, `bugs-index.md`.

**Not applied to:** `bugs-evidence/` folders (local-only, gitignored, trust boundary is the developer's machine).

---

## Patterns and replacements

Apply in order. Each match is replaced with the token shown.

| Pattern (regex) | Replacement | Notes |
|---|---|---|
| `bearer\s+[A-Za-z0-9\-._~+/]+=*` | `<redacted:bearer-token>` | Case-insensitive |
| `eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}` | `<redacted:jwt>` | JWT format |
| `(api[_-]?key\|secret\|token\|password\|passwd\|pwd)[\s:=]+['"]?[A-Za-z0-9_\-]{16,}['"]?` | `<redacted:credential>` | Case-insensitive |
| `AKIA[0-9A-Z]{16}` | `<redacted:aws-key>` | AWS access key |
| `[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}` | `<redacted:email>` | Standard RFC email |
| `pk_[A-Za-z0-9_]{20,}` | `<redacted:clickup-token>` | ClickUp personal API token |
| `glpat-[A-Za-z0-9_\-]{20,}` | `<redacted:gitlab-token>` | GitLab personal access token |

---

## What is NOT redacted

- **UUIDs** (e.g., tenant IDs, user IDs in non-auth contexts) — these are debugging signals, not secrets.
- **Tenant names** — helpful for context.
- **URLs** — redact only query-param values that match a credential pattern, not the full URL.
- **Error messages** — redact embedded tokens/keys, keep the message structure.
- **Screenshot content** — out of scope for V1 (requires OCR). Dev is responsible for cropping sensitive regions before capture.

---

## How to apply

### Bash (for MR descriptions and ClickUp comments)

```bash
redact() {
  local text="$1"
  text=$(echo "$text" | sed -E 's/bearer[[:space:]]+[A-Za-z0-9._~+\/-]+=*/<redacted:bearer-token>/gi')
  text=$(echo "$text" | sed -E 's/eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/<redacted:jwt>/g')
  text=$(echo "$text" | sed -E 's/(api[_-]?key|secret|token|password|passwd|pwd)[[:space:]:=]+['"'"'"]?[A-Za-z0-9_-]{16,}['"'"'"]?/<redacted:credential>/gi')
  text=$(echo "$text" | sed -E 's/AKIA[0-9A-Z]{16}/<redacted:aws-key>/g')
  text=$(echo "$text" | sed -E 's/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/<redacted:email>/g')
  text=$(echo "$text" | sed -E 's/pk_[A-Za-z0-9_]{20,}/<redacted:clickup-token>/g')
  text=$(echo "$text" | sed -E 's/glpat-[A-Za-z0-9_-]{20,}/<redacted:gitlab-token>/g')
  echo "$text"
}
```

### Node.js (for generate-bug-list.mjs external exports if needed)

```javascript
const REDACTION_PATTERNS = [
  [/bearer\s+[A-Za-z0-9._~+/-]+=*/gi, '<redacted:bearer-token>'],
  [/eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/g, '<redacted:jwt>'],
  [/(api[_-]?key|secret|token|password|passwd|pwd)[\s:=]+['"]?[A-Za-z0-9_-]{16,}['"]?/gi, '<redacted:credential>'],
  [/AKIA[0-9A-Z]{16}/g, '<redacted:aws-key>'],
  [/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, '<redacted:email>'],
  [/pk_[A-Za-z0-9_]{20,}/g, '<redacted:clickup-token>'],
  [/glpat-[A-Za-z0-9_-]{20,}/g, '<redacted:gitlab-token>'],
]

function redact(text) {
  return REDACTION_PATTERNS.reduce((t, [pattern, replacement]) => t.replace(pattern, replacement), text)
}
```

---

## Scope notes

Redaction is applied to:
- ClickUp task description and comments (bug-reporter + bug-fixer)
- GitLab MR title and description (bug-fixer)
- `bugs-index.md` entries (bug-fixer Step 10)

It is **not** applied to:
- `bugs-evidence/` folder contents (local trust boundary)
- Console logs written locally to evidence folder
- Network HARs written locally to evidence folder

When in doubt, redact before writing to any external system.