# Builder Widget Pattern

> **⚠️ Legacy bespoke pattern — incentive domain only.**
>
> This file describes the frontend builder component architecture as
> implemented for the incentive builder. Status per the
> [domain registry](../../../tenxengage-blueprint/docs/patterns/domains/INDEX.md):
> `active-legacy` (see [incentive.md](../../../tenxengage-blueprint/docs/patterns/domains/incentive.md)).
> The code stays in production; the file stays for reference. **New code
> should not adopt this pattern as-is.**
>
> **Implementing a feature for a new domain (enablement, future)?**
> Do NOT follow this pattern directly. New domains use **platform primitives**:
> `BuilderDefinition` / `BuilderSectionDefinition` / `BuilderFieldDefinition`
> per [platform-primitives.md](../../../tenxengage-blueprint/docs/patterns/domains/platform-primitives.md).
> The platform-primitives implementation does not exist in code yet — the
> first feature landing on platform primitives builds it (including the
> frontend renderer shell `BuilderShell` and the config-driven section
> dispatch), guided by:
> - The slot list and naming convention in [domains/INDEX.md](../../../tenxengage-blueprint/docs/patterns/domains/INDEX.md)
> - The design at [docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md](../../../tenxengage-blueprint/docs/superpowers/specs/2026-05-12-create-spec-domain-awareness-design.md)
>
> **First engineer to land platform primitives:** as a final deliverable of
> your feature, write `builder-widget-platform.md` in this directory (or a
> naturally-named equivalent once implementation has clarified the structure)
> and register it in the blueprint's [patterns INDEX](../../../tenxengage-blueprint/docs/patterns/INDEX.md).
> This redirection block points at your file once it exists.

---

This document describes the standard architecture for builder/wizard components in the TenXEngage frontend. Builders are complex multi-step forms used to create and edit entities like incentives, courses, and other configurable objects. Every new builder must follow this pattern for consistency.

## Layout Pattern

The builder uses a two-column layout with a 40/60 split. The left column (40%) serves dual purpose: it can display either the AI copilot chat panel or a manual summary panel, toggled by the user. The right column (60%) contains the primary editing interface, which cycles between three views — the setup accordion, a detail editor, and a preview panel — using the FlipTransition animation component.

This split-view design keeps contextual help or AI assistance visible alongside the active editing area at all times. The left panel preserves its scroll position and chat history when toggling between AI and manual modes. The right column's FlipTransition handles smooth animated transitions when switching between setup, detail editing, and preview views.

## Header

Every builder includes a PageBanner header component at the top of the page. The header displays a title/subtitle for the entity being built, a mode toggle (AI-assisted vs manual; the AI option is gated by the `ai_copilot` feature flag), a completion percentage indicator, and a "Step X of N" label paired with a segmented progress bar.

The segmented progress bar visually represents each step as a distinct segment that fills or highlights as the user completes it. The completion percentage is calculated from the number of steps marked complete versus the total step count.

## Flow Architecture

The entry flow into a builder follows a specific sequence: the user navigates from a menu to select the entity type, then a sub-type, then optionally a template, and finally lands in the builder itself. Each transition in this flow uses the FlowTransition component for directional slide animations. Direction is computed from a per-flow-state depth map (`FLOW_DEPTH` in `IncentiveBuilderPage.tsx`) — moving to a deeper state slides forward; moving to a shallower state slides backward.

This entry flow is separate from the builder's internal step navigation. The flow state tracks which stage the user is in (type selection, sub-type selection, template selection, or active building) and enables proper back-navigation through the entire sequence.

## Step/Wizard Pattern

Within the builder, steps are rendered as an accordion. Only one step can be expanded at a time — expanding a new step automatically collapses the previous one. Each step uses the AnimatedCollapse component with a 300ms transition for smooth open/close animation.

Each step displays a badge to the left of its title: a number when the step is incomplete and a checkmark icon when the step is marked complete. At the bottom of each expanded step, a "Continue to [Next Step Name]" button advances the user to the next step and auto-scrolls the view to bring the newly expanded step into the viewport. Steps can be revisited by clicking their header in the accordion.

## State Management

Builder state is managed through a dedicated BuilderContext combined with a useBuilderReducer hook, following the React Context + useReducer pattern. The context provider wraps the entire builder page and provides state and dispatch to all child components.

The state object contains several categories of data: flow control (current flow state and direction), step tracking (which step is active, which are complete), per-step data objects (the actual form values for each step), AI chat state (conversation history, streaming status), detail editor state (which sub-item is being edited), preview/forecasting state, a `builderOrigin` marker (`scratch` / `template` / `existing` / `edit` — used to contextualize AI copilot prompts), and edit tracking via an `isDirty` flag. `isDirty` must never be set during a `LOAD_*` hydration — loading initial data is not a user edit.

The context is initialized with default state on mount for new entities. For editing existing entities, an entity-specific `LOAD_*` action populates the state from API response data — in the reference impl this is `LOAD_INCENTIVE`. All components within the builder read from and dispatch to this single context rather than maintaining local state.

## Actions Pattern

The reducer handles several categories of actions. `SET_*` actions control navigation and UI state (e.g., `SET_ACTIVE_STEP`, `SET_FLOW_STATE`, `SET_MODE`). `UPDATE_*` actions modify per-step data objects (e.g., `UPDATE_BASICS`, `UPDATE_SCHEDULE`, `UPDATE_AUDIENCE`, `UPDATE_BUDGET`, `UPDATE_CRITERIA`, `UPDATE_APPROVAL`). `MARK_STEP_COMPLETE` and `MARK_STEP_INCOMPLETE` toggle step completion status. An entity-specific `LOAD_{ENTITY}` action (literal `LOAD_INCENTIVE` in the reference impl) hydrates the full state from an API response when editing — future builders should follow the same `LOAD_{ENTITY}` convention with their own entity name. `RESET` returns the state to its initial default.

Data normalization happens inside the reducer, not in the components. When an UPDATE action is dispatched, the reducer ensures the data is properly structured before storing it. This keeps components simple — they dispatch raw user input and the reducer handles transformation.

## Form Pattern

Unlike typical form pages that use react-hook-form, builders dispatch changes directly to the BuilderContext on every field change. There is no intermediate form state managed by react-hook-form within the builder steps. Instead, each field's onChange handler dispatches an UPDATE action immediately.

Per-step validation runs automatically via useEffect hooks that watch the relevant slice of builder state. When all required fields for a step are populated and valid, the step can be marked complete. The DynamicFieldRenderer component renders fields based on configuration data (from builder-config), making the form structure data-driven rather than hardcoded.

## Location Filtering and Hierarchy Scoping

When a step allows location-based eligibility or filtering, use the `LocationFilter` component (single-select) or Step 3's multi-select pickers with cascading scope. See [location-hierarchy.md](location-hierarchy.md) for the full pattern, including the names-vs-UUIDs invariant and cascading scope rules.

## Type-Specific Content

A type dispatcher component examines the entity type being built and renders the appropriate editor component. Common steps (like basics, schedule, audience) remain the same across types, while type-specific steps switch their content based on the selected type. For example, a sales incentive might have a "Criteria" step with product-based rules, while a learning incentive might have a "Courses" step.

Step labels and call-to-action text are driven by configuration, so the same builder framework can adapt its terminology to different entity types without code changes. The type dispatcher pattern keeps the builder shell generic while allowing deep customization per type.

## Navigation Guards

The NavigationGuard component prevents users from accidentally leaving the builder with unsaved changes. It monitors two conditions: `isDirty` (state has been modified from initial) and `hasActualEdits` (the modifications are meaningful, not just opening and closing a step).

When a user attempts to navigate away while both conditions are true, a confirmation dialog appears asking them to confirm they want to discard their changes. This guard hooks into React Router's navigation events and also handles browser back/forward buttons and tab close events.

## Save and Publish Flow

The builder does not have an explicit "save draft" button. State is maintained entirely in the BuilderContext as the user works. When required steps are marked complete, a primary CTA — labelled **"Review & Launch"** in the reference impl — becomes enabled. Clicking it transitions the right panel to a terminal review/forecast view. In the incentive builder this is `ForecastingPanel` (under `components/incentive-builder/forecasting/`), gated solely by the `ai_forecasting` feature flag and applied uniformly to all incentive types (`SALES`, `TRAINING`, `ACTIVITY`, `JOURNEY`). When the flag is off, the builder dispatches `REQUEST_CREATE_CONFIRMATION` and routes directly to a confirm dialog instead.

From the review/forecast view, the user confirms and the builder makes the API call (create for new entities, update for existing ones). On success, the builder navigates away to the entity list or detail page. In edit mode, the entity-specific `LOAD_*` action populates all state from the API response on mount (without setting `isDirty`), and the builder tracks changes from that loaded baseline for the `isDirty` flag.

## Animation Standards

The builder uses a consistent set of animation components throughout. FlowTransition handles directional slide animations for the entry flow (type selection through to builder). AnimatedCollapse manages height-based expand/collapse with a 300ms duration for accordion steps. FlipTransition provides panel-switching animation in the right column (setup, detail editor, preview).

The mode toggle in the left column uses a fade animation when switching between AI copilot and manual summary. AI streaming text uses a typing animation effect. All animations follow the project's standard easing curves and respect the user's reduced-motion preferences via the `prefers-reduced-motion` media query.

## File Organization

Every builder follows this directory structure:

```
components/{builder-name}/
  ├── {Builder}Layout.tsx
  ├── {Builder}Accordion.tsx
  ├── steps/Step1{Name}.tsx ... StepN{Name}.tsx
  ├── {type-specific}/{Type}Editor.tsx
  ├── ai/AICopilotPanel.tsx
  ├── ManualSummaryPanel.tsx
  ├── DynamicFieldRenderer.tsx     // builder-aware field-type dispatcher; lives with the builder, NOT under shared/
  ├── DynamicExtraFields.tsx        // renders config-driven extra fields per step
  └── {preview-or-forecast}/        // reference impl uses forecasting/ForecastingPanel.tsx, gated by feature flag
contexts/BuilderContext.tsx         // generic name; reference impl is currently the only builder
hooks/useBuilderReducer.ts
hooks/useBuilderConfig.ts
types/builder-state.types.ts
pages/.../{Entity}BuilderPage.tsx
```

The Layout component is the top-level component rendered by the page. It sets up the two-column split and renders the left panel (AI or manual) and right panel (accordion, detail editor, or forecasting/preview). The Accordion component manages step expansion. Each step is a separate file in the `steps/` directory. Type-specific editors live in their own subdirectory. The context, reducer hook, config hook, and state types each get their own dedicated file. Note that `DynamicFieldRenderer.tsx` lives inside the builder folder (not under `components/shared/`); a separate `src/components/DynamicFieldRenderer.tsx` exists for the data-objects feature and is unrelated.

When creating a new builder, copy this structure and rename the placeholders. Do not consolidate these files — the separation is intentional to keep each concern in a single, focused file.
