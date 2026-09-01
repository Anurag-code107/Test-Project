# AI Copilot Integration Pattern — Backend

> **Cross-ref:** For the full copilot pattern (spec authoring guidance, frontend SSE components, tool-based actions),
> see [tenxengage-blueprint/docs/patterns/ai-copilot.md](../../../tenxengage-blueprint/docs/patterns/ai-copilot.md).
> This file covers backend-only implementation details.



How the backend implements the AI copilot: SSE streaming, multi-round tool loop, system prompt construction, and the document extraction pipeline. Implementation lives in [AiChatService.java](../../src/main/java/com/tenxengage/app/service/AiChatService.java) and [AiChatController.java](../../src/main/java/com/tenxengage/app/controller/AiChatController.java).

## Operating modes

- **Interactive chat** — user sends messages one at a time; backend streams Claude's responses with tool calls.
- **Document-driven** — file is uploaded, distilled into a numbered prompt script, then each prompt is executed sequentially as a mini-conversation.

## Configuration

```yaml
app:
  ai:
    model: claude-sonnet-4-20250514
    max-tokens: 4096
```

The `AnthropicClient` bean is **conditionally created** — `@Autowired(required = false)` with a null-check (`isAvailable()`). If `ANTHROPIC_API_KEY` is unset, AI endpoints fail fast with a clear error rather than throwing NPE deep in the streaming code.

## SSE event protocol

Frontend listens for these named events:

| Event | Data shape | Purpose |
|---|---|---|
| `text_delta` | `{"text": "..."}` | Incremental chat text |
| `action` | `{"toolName": "...", "input": {...}}` | Tool call to apply to builder state |
| `suggestions` | `{"suggestions": [...]}` | Clickable suggestion chips |
| `done` | `{}` | Stream complete |
| `error` | `{"message": "..."}` | Error during processing |

## Timeouts (rationale)

- **Chat: 5 min (`300_000L`)** — multi-round tool loops can take 20–30s per round; 10 rounds × 30s = 5 min worst case.
- **Document: 10 min (`600_000L`)** — distill stage + N sequential script prompts, each with its own mini tool loop.

These are not arbitrary. Don't shorten them without recomputing for the worst-case round count.

## Virtual thread + SecurityContext

Streaming runs on `Thread.startVirtualThread(...)` to avoid pinning a servlet thread. **Virtual threads do not inherit `SecurityContextHolder`** — capture it on the request thread, re-set on the virtual thread, clear in `finally`. The `TenantContext` thread-local has the same problem and must be re-set inside the virtual thread for any code that reads it.

## Multi-round tool loop

```java
private static final int MAX_TOOL_ROUNDS = 10;
```

The hard cap on rounds is the load-bearing guardrail — prevents runaway API spend if Claude gets into a tool-call loop. **Never remove or raise this without a circuit-breaker upstream.**

Loop logic per round:

1. Call `client.messages().create(...)`.
2. Process content blocks → emit `text_delta` and/or `action` events.
3. If `stopReason == MAX_TOKENS`: append response, send `"Continue where you left off."` user message, continue loop. **Without this, long responses are silently truncated.**
4. If no tool calls in the response: break (we're done).
5. Otherwise: append response + tool results, loop.

Exit conditions: no tool calls (normal), `MAX_TOOL_ROUNDS` reached (cap hit), or exception.

## Available tools

| Tool | Purpose |
|---|---|
| `update_builder` | Updates builder fields (name, dates, regions, budget, …) |
| `suggest_actions` | Proposes 2-3 clickable suggestions |
| `search_products` | Product catalog keyword search |
| `search_courses` | LMS course catalog search (enablement incentives) |

Tool execution dispatches on `toolUse.name()`. **Tool result execution must call `TenantContext.getClientId()` itself** — it runs on the streaming virtual thread and can't trust upstream propagation. `update_builder` and `suggest_actions` return `"OK"`; data-fetch tools return JSON the next round will consume.

## System prompt construction

Template at `src/main/resources/prompts/{builder}-copilot-system.txt`. Loaded into the `systemPromptTemplate` field at startup.

Placeholders replaced at runtime:

| Placeholder | Source |
|---|---|
| `{{currentDate}}` | `LocalDate.now()` |
| `{{fiscalYearLabels}}` | `FiscalYearConfigService.getFiscalYearLabels()` |
| `{{fiscalQuarterDates}}` | `FiscalYearConfigService.getFiscalQuarterDates()` |

After substitution, two appendices are added:

1. **`--- RULE FIELD IDS ---`** — `fieldKey → UUID` map for the current incentive type. Lets Claude reference fields by ID in `update_builder` calls.
2. **`--- CURRENT STATE ---`** — JSON snapshot of the builder so Claude knows what's filled vs missing.

Both appendices are in the system prompt, not the user message — they're context, not instructions.

## Document pipeline

Two-stage. Supported uploads: PDF, PPTX, XLSX, XLS, TXT, CSV, MD. **Max 10 MB.**

### Stage 1: distill-to-script

`DocumentTextExtractor` extracts raw text. A one-shot call to Claude converts it to a numbered instruction list with this guardrail:

> "Be precise with all numbers, dates, and names. NEVER invent information not in the document."

Output is parsed line-by-line. Required regex: `^\d+\.\s+.+` — anything not matching is dropped. The script ordering is part of the prompt: basics → timeline → audience/regions → budget → requirements with payout tiers.

### Stage 2: execute-script

Each numbered prompt is fed into the copilot **as a turn in one shared conversation** (preserves continuity — later prompts can reference earlier state). Per-prompt round cap is **5** (lower than chat's 10 — each script step should be focused).

During script execution: **text responses from Claude are suppressed**. Only tool calls are emitted to the frontend, plus pre-baked progress labels ("Setting up the basics...", "Configuring the timeline..."). This keeps the UX clean during automated processing.

## Permissions

| Endpoint | Permission |
|---|---|
| `POST /api/v1/ai/chat` | `action.ai.copilot` |
| `POST /api/v1/ai/chat-with-document` | `action.ai.assistant` |

## Rules

1. System prompt templates **must** live in `src/main/resources/prompts/*.txt`. Never inline in Java.
2. Every copilot action goes through tools — Claude must never tell the user to fill fields manually.
3. The `MAX_TOOL_ROUNDS` cap is non-negotiable. New flows that need more rounds need their own cap and rationale.
4. Always propagate `SecurityContext` and re-set `TenantContext` inside virtual threads before any service call.
5. Keep tool results minimal — return only what Claude needs for the next decision (token budget).
