# AI Copilot Pattern — Frontend

> **Cross-ref:** For the full copilot pattern (spec authoring guidance, backend SSE/tool-loop implementation),
> see [tenxengage-blueprint/docs/patterns/ai-copilot.md](../../../tenxengage-blueprint/docs/patterns/ai-copilot.md).
> This file covers frontend-only implementation details.



This document describes how the frontend integrates the AI copilot into builder components. The AI copilot provides a conversational assistant that can read the current builder state, suggest changes, directly modify fields, and process uploaded documents to auto-populate the builder.

## SSE Streaming via POST

The AI copilot uses a POST-based Server-Sent Events (SSE) connection to `/api/v1/ai/chat`. This is deliberately not a browser-native `EventSource` because `EventSource` only supports GET requests and cannot send a JSON body. Instead, the frontend uses `fetch` with a readable stream to parse SSE events from the response body.

The streaming connection opens when the user sends a message or uploads a document. The frontend reads the response stream line by line, parsing SSE event types and data payloads. The connection stays open until the server sends a `done` event or an `error` event. The frontend must handle network interruptions gracefully and show an appropriate error message if the stream fails.

## Request Format

Every chat request sends a JSON body with three required fields: `conversationHistory` (the last 20 messages in the conversation), `currentState` (a full snapshot of the builder's current state including a `stepFieldStatus` audit of every field's completion), and `incentiveType` (the type of entity being built).

The `conversationHistory` array is capped at 20 messages to keep request size manageable and stay within the AI model's effective context window. The `currentState` snapshot gives the AI full visibility into what the user has configured so far, enabling it to make contextual suggestions and validate its own proposed changes. The `stepFieldStatus` audit within the state provides a field-by-field accounting of which fields are filled, empty, or invalid.

## Event Types

The SSE stream delivers five event types that the frontend must handle:

- **`text_delta`** — A chunk of text from the AI's response. Append these incrementally to the current message to create a streaming text effect. Render partial markdown as it arrives.
- **`action`** — A tool call from the AI requesting a change to the builder state. Contains the action type and payload. The frontend translates this into a reducer dispatch (see Tool-Based Actions below).
- **`suggestions`** — An array of 2-3 suggested follow-up actions the user might want to take. Render these as clickable chips below the AI's response.
- **`done`** — Signals the end of the AI's response. Close the stream, finalize the message, and re-enable user input.
- **`error`** — An error occurred during processing. Display a user-friendly error message and re-enable input so the user can retry.

## Tool-Based Actions

The AI can dispatch tool calls that directly modify the builder state. When an `action` event arrives, it contains a tool name (like `UPDATE_BASICS`, `UPDATE_SCHEDULE`, `MARK_STEP_COMPLETE`, `CONFIRM_CREATE`, or `SHOW_FORECASTING`) and a payload with the data to apply.

The frontend translates these tool calls into reducer actions. For example, an `UPDATE_BASICS` tool call with `{ name: "Q1 Sales Push" }` becomes a dispatch of the `UPDATE_BASICS` action with that payload merged into the current basics state. The AI can update fields across multiple steps in a single response by issuing multiple tool calls.

This tool-based approach means the AI does not directly mutate state. It proposes changes through the same action/reducer pipeline that manual user edits use, ensuring all state transitions go through the same validation and normalization logic.

## Deferred Actions

Two specific actions — `MARK_STEP_COMPLETE` and `CONFIRM_CREATE` — are deferred using `requestAnimationFrame` rather than dispatched immediately. This deferral ensures that any preceding UPDATE actions in the same AI response have been processed and the builder state is fully up-to-date before the step completion check or creation confirmation runs.

Without deferral, a race condition can occur: the AI sends an UPDATE followed by MARK_STEP_COMPLETE in rapid succession, but the completion check runs against stale state that does not yet reflect the update. The `requestAnimationFrame` deferral guarantees the state audit uses the freshest state.

## AI Guard

The AI Guard is a client-side safety mechanism that blocks the `CONFIRM_CREATE` action if the builder's field audit detects incomplete steps. When a `CONFIRM_CREATE` tool call arrives, the guard runs a full audit of every step's required fields. If any step has missing or invalid required fields, the guard rejects the creation, sends a message back to the AI explaining which fields are incomplete, and the AI adjusts its response accordingly.

This prevents the AI from prematurely triggering entity creation when the builder is not fully configured. The guard acts as a final validation layer independent of the AI's own assessment of completeness.

## Document Pipeline

The document pipeline allows users to upload files that the AI processes to auto-populate the builder. The flow is: upload the file, extract text content from it, distill the text into a sequence of structured prompts, then execute each prompt to populate builder fields with visible actions.

During document processing, the AI shows each action it takes so the user can follow along. For example, it might show "Setting incentive name to 'Q1 Sales Challenge'" followed by "Adding product criteria for Widget Pro". Each action dispatches tool calls that update the builder state in real time, giving the user a transparent view of what the AI is doing with their document.

## Supported File Types

The copilot accepts the following file types for document upload: PDF, PPTX, XLSX, XLS, TXT, CSV, and MD. The maximum file size is 10MB. File type validation happens on the client side before upload — display a clear error message if the user attempts to upload an unsupported type or an oversized file.

The frontend sends the file as a multipart upload to the document processing endpoint. Different file types have different extraction fidelity — PDFs and text files tend to extract cleanly, while spreadsheets may need column-header mapping. The backend handles all extraction logic; the frontend only needs to upload the file and process the resulting SSE stream.

## Suggestion Chips

After each AI response, the `suggestions` event delivers 2-3 clickable action suggestions. These are rendered as SuggestionChips below the latest message. When clicked, a suggestion chip sends that text as the user's next message, triggering a new chat round.

Suggestions are context-aware — the AI generates them based on the current builder state and conversation history. For example, after helping set up the basics step, suggestions might include "Set up the schedule" or "Add audience criteria". Chips are replaced with each new AI response and are not shown while the AI is streaming.

## UI Components

The AI copilot UI is composed of several components:

- **AICopilotPanel** — The main container for the left-column AI interface. Manages the chat message list, scroll position, and streaming state.
- **ChatMessage** — Renders a single message (user or AI). AI messages support markdown rendering and display tool actions inline as they execute.
- **ChatInput** — The text input area at the bottom of the panel. Includes the send button, file upload button, and is disabled during streaming.
- **SuggestionChips** — Renders clickable suggestion pills below the latest AI message.
- **CreateConfirmationCard** — A special card rendered when the AI proposes creating the entity. Shows a summary of what will be created and confirm/cancel buttons.

These components live in the builder's `ai/` subdirectory following the standard builder file organization pattern.
