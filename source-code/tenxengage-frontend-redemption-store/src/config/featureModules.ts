/**
 * Feature flag → UI surface registry.
 *
 * Each seeded entry in `feature_flags.feature_key` that gates a tenant-side UI
 * surface is documented here. The actual gating is wired in:
 *  - `App.tsx` via `<ProtectedRoute feature="...">` on the matching route.
 *  - `sidebarConfigs.ts` via `featureKey: "..."` on the matching NavItem.
 *  - Page / component code via `<FeatureGate feature="...">` on the matching
 *    button, section, or tab content (see `FEATURE_TO_SURFACE` below).
 *
 * Resolution semantics (see `useFeatures`): unknown / mistyped keys evaluate
 * to `false` (fail-closed) — a typo will hide UI rather than silently expose
 * a tier-gated feature.
 */
export const FEATURE_TO_ROUTE: Record<string, string> = {
  audit_log: "/activity-log",
  deal_qualifier: "/deal-qualifier",
};

/**
 * Feature flags that gate non-route surfaces (buttons, settings tabs,
 * sub-sections). Pure documentation — the gating itself happens in the
 * component file via `<FeatureGate feature="...">`. Listed here so future
 * contributors can grep one file to find every wired flag.
 */
export const FEATURE_TO_SURFACE: Record<string, string> = {
  custom_branding:
    "Platform Settings → Branding tab (trigger + content). Gated in PlatformSettingsPage.tsx.",
  api_access:
    "Platform Settings → Integrations tab (trigger + content). Gated in PlatformSettingsPage.tsx.",
  export_reports:
    "Export buttons on Activity Log, Claims table, and Report Builder. Gated in ActivityLogPage.tsx, ClaimsTable.tsx, ReportBuilderPage.tsx.",
  guided_tours:
    "Service-level gate on startTour in GuidedTourContext.tsx + AIAssistantInput render gate. When off, tour state never activates and the AI assistant input is hidden.",
  journey_incentives:
    "Hides the Journey tab on /incentives and /manage-incentives, the Journey card in the incentive builder Type/Template selectors, and the Journey filter option in ExistingIncentiveSelector. Frontend-only gate (v1) — backend still serves journey-type creates/updates if called directly.",
  bulk_import:
    "Platform Settings → Manage Data tab → (a) 'Manual Upload' button on each data object card (gated in ManageDataTab.tsx); (b) entire 'Manual Data Uploads' collapsible inside the data object detail view (gated in DataOperationsPanel.tsx — covers connector pull + manual file upload). Both surfaces hidden when off.",
  multi_currency:
    "Platform Settings → Manage Business Rules → 'Manage Reward Types' subtab (trigger + content). Gated in PlatformSettingsPage.tsx. Hidden when off.",
  ai_copilot:
    "Incentive Builder → AI/Manual mode toggle. When off, the AI Mode button is disabled (not hidden) with a tooltip explaining the tier gap, and a useEffect forces state.mode to 'manual' so the AI Copilot panel never renders. Gated in BuilderLayout.tsx.",
  ai_forecasting:
    "Incentive Builder → Complete Setup flow. When off, clicking Complete Setup in ManualSummaryPanel (or the equivalent terminal step in BuilderAccordion) skips the AI Forecasting panel entirely and opens a create-incentive confirmation dialog instead. Gated in BuilderLayout.tsx via handleShowForecasting + a Dialog rendered when state.pendingCreate && state.mode === 'manual' && !aiForecastingEnabled.",
};

/**
 * Seeded feature flags whose tenant-side UI surface does not yet exist in
 * tenxengage-frontend. The flag persists correctly through the admin
 * Subscriptions toggle and rides on /auth/me's enabledFeatures, but there
 * is nothing to wrap today. Re-run BUG-048's fix path once the corresponding
 * UI ships.
 */
export const FEATURE_PENDING_UI: Record<string, string> = {
  white_labeling:
    "No custom-logo / custom-domain UI exists. BrandingSection only configures colors and fonts.",
  sso_integration:
    "No SSO / SAML / OAuth configuration UI exists. IntegrationsTab today is third-party connectors only.",
};
