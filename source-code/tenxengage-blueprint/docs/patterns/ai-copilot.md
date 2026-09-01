# Pattern: ai-copilot

## When this applies

Use this pattern when a feature **integrates AI assistance** — specifically an interactive chat copilot embedded in a builder or editor that can read the current UI state, fill in fields, and trigger actions through a streaming conversation. Also applies when a feature adds a document-upload-to-AI pipeline.

## Spec authoring guidance

- Describe the copilot's home in the UI: it lives in the builder's left panel and is toggled against the manual summary view.
- List the builder actions the AI can trigger (e.g., `UPDATE_BASIC_INFO`, `MARK_STEP_COMPLETE`, `CONFIRM_CREATE`). These must correspond 1-to-1 with reducer action names.
- Specify which fields the AI may fill and which are off-limits.
- Describe the AI Guard rule: what constitutes a "complete" state before `CONFIRM_CREATE` is allowed? List the required fields/steps.
- If the feature supports document upload, list: accepted file types, max size, and the phases of extraction (basics/schedule, budget, criteria, summary, etc.).
- Specify the two permissions: `action.ai.copilot` (chat) and `action.ai.assistant` (document upload).
- Name the system prompt template file: `resources/prompts/{builder}-copilot-system.txt`.
- List any dynamic placeholders the system prompt requires (e.g., `{{currentDate}}`, `{{fiscalYearLabels}}`, `{{fiscalQuarterDates}}`).

## Implementation guidance

### Frontend

#### SSE Streaming via POST

The copilot uses POST-based Server-Sent Events (SSE) — **not** the browser's native `EventSource` API. The frontend sends `POST /api/v1/ai/chat` with a JSON body containing:
- Conversation history (last 20 messages)
- Current builder state snapshot (including a `stepFieldStatus` audit of filled/missing fields)
- Entity type

The response streams back as SSE events.

#### Event Types

| Event | Payload |
|---|---|
| `text_delta` | Chunk of AI's visible text reply — append to current message |
| `action` | Tool call — structured instruction to modify builder state |
| `suggestions` | Array of 2–3 clickable suggestion chips shown after response |
| `done` | Response complete |
| `error` | Error information |

#### Tool-Based Actions

When the AI modifies the builder, it emits `action` events containing tool calls. Tool call names match builder reducer action names exactly (`UPDATE_BASIC_INFO`, `UPDATE_SCHEDULE`, `MARK_STEP_COMPLETE`, `CONFIRM_CREATE`, etc.). The frontend's action handler translates these into `dispatch()` calls — the AI uses the same state system as the UI.

#### Deferred Actions

`MARK_STEP_COMPLETE` and `CONFIRM_CREATE` are deferred via `requestAnimationFrame`. This ensures field updates dispatched in the same response cycle have committed to state before the completion check runs.

#### AI Guard

Before allowing `CONFIRM_CREATE`, the AI Guard checks `stepFieldStatus`. If any required fields are missing or steps are incomplete, the guard blocks creation and sends a message back to the AI describing what is still needed.

#### Document Pipeline

Users can upload documents (PDF, PPTX, XLSX, XLS, TXT, CSV, MD — max 10 MB). Pipeline stages:
1. Upload and text extraction
2. Distill to script: one-shot Claude call produces a structured series of prompts, each targeting specific builder fields
3. Execute script: each prompt runs as a mini conversation (up to 5 tool-loop rounds)

#### UI Components

| Component | Purpose |
|---|---|
| `AICopilotPanel` | Main container — chat state and streaming |
| `ChatMessage` | Renders individual messages with markdown + action indicators |
| `ChatInput` | Text input with file upload support |
| `SuggestionChips` | Clickable follow-up suggestions |
| `CreateConfirmationCard` | Summary card shown when AI proposes entity creation |

### Backend

#### SDK and Configuration

Use the Anthropic Java SDK. Model name and token limits are configured in `application.yml` — never hardcode them.

#### SSE Streaming

Responses stream via Spring's `SseEmitter`. Timeouts: 5 minutes for chat, 10 minutes for document processing. Each streaming session runs on a **virtual thread** with security context propagated.

#### Multi-Round Tool Loop

The AI operates in a tool loop with a maximum of 10 rounds. Each round: Claude generates text and/or tool calls → backend executes tool calls → results fed back as tool results → next round. Loop exits when Claude produces no tool calls or the round limit is reached. If truncation occurs (token limit), re-prompt Claude to continue.

#### Available Tools

| Tool | Purpose |
|---|---|
| `update_builder` | Modifies builder fields |
| `suggest_actions` | Proposes follow-up actions to the user |
| `search_products` | Queries product catalog (product-based incentives) |
| `search_courses` | Queries course catalog (learning-based incentives) |

#### System Prompt

Template file: `resources/prompts/{builder}-copilot-system.txt`. Placeholders resolved at runtime:
- `{{currentDate}}`
- `{{fiscalYearLabels}}`
- `{{fiscalQuarterDates}}`

At request time, the resolved template is augmented with: rule field UUIDs (so Claude can reference fields by ID) and a JSON snapshot of the current builder state.

#### Document Pipeline (Backend)

For document uploads:
1. `DocumentTextExtractor` pulls text from the file
2. `distillToScript` — one-shot call to Claude → structured prompt script
3. `streamDocumentScript` — executes each prompt as a mini conversation (up to 5 tool-loop rounds per prompt)

For complex documents, extraction runs in phases:
- Phase 1: basics, schedule, audience
- Phase 2: budget
- Phase 3a: product search
- Phase 3b: criteria and rules
- Phase 4: summary

#### Permissions

| Permission | Controls |
|---|---|
| `action.ai.copilot` | Access to the chat interface |
| `action.ai.assistant` | Access to document upload and processing |

Both must be checked via `@RequiresPermission` before their respective endpoints execute.

#### Key Rules

- System prompt templates live in `resources/prompts/` — edit there, not in Java code.
- All builder modifications go through tool-based actions — the AI never writes to the database directly.
- The multi-round tool loop is the standard pattern for any AI interaction that needs to take actions.

## Examples in codebase

- `../tenxengage-backend/src/main/java/com/tenxengage/app/service/AiChatService.java` — incentive copilot: tool loop, SSE emission, document pipeline
- `../tenxengage-backend/src/main/java/com/tenxengage/app/service/EnablementCopilotService.java` — enablement (course) copilot: tool loop, SSE emission
- `../tenxengage-backend/src/main/resources/prompts/incentive-copilot-system.txt` — canonical system prompt template
- `../tenxengage-frontend/src/components/incentive-builder/ai/AICopilotPanel.tsx` — streaming SSE consumer + action dispatch
- `../tenxengage-frontend/src/components/incentive-builder/ai/SuggestionChips.tsx` — suggestion chip rendering

## Common gotchas

- **Do not use the browser's native `EventSource` API.** It only supports GET requests. The copilot requires a POST body (conversation history + builder state), so use a custom SSE fetch implementation.
- **`MARK_STEP_COMPLETE` and `CONFIRM_CREATE` must be deferred.** Without `requestAnimationFrame`, field updates and completion checks race — the step can be marked complete before the fields are actually in state.
- **The AI Guard is not optional.** Without it, the AI can call `CONFIRM_CREATE` on an incomplete entity. The guard must check `stepFieldStatus`, not just step completion flags.
- **System prompt templates must be files, not strings in Java.** Hardcoded prompts are impossible to iterate on without redeploying. Keep all prompts in `resources/prompts/`.
- **Token limits vary by document complexity.** The 10-minute SSE timeout exists for a reason — do not shorten it for document processing endpoints.
- **Virtual thread + security context propagation is required.** Without explicit context propagation, tenant and user information is unavailable inside the streaming thread, causing authorization failures mid-stream.
- **Tool call names must exactly match reducer action names.** If a reducer action is renamed, the AI tool definition must be updated in lockstep — otherwise the action handler silently ignores the tool call.
