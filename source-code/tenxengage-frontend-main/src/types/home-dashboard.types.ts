export const HOME_DASHBOARD_WIDGET_KEYS = [
  "ai_assistant",
  "program_performance",
  "tenx_suggestions",
  "rewards_balances",
  "approvals",
] as const;

export type HomeDashboardWidgetKey =
  (typeof HOME_DASHBOARD_WIDGET_KEYS)[number];

export const HOME_DASHBOARD_ROW_LAYOUTS = ["full", "half-half"] as const;

export type HomeDashboardRowLayoutKey =
  (typeof HOME_DASHBOARD_ROW_LAYOUTS)[number];

export interface HomeDashboardSlot {
  widgetKey: string;
}

export interface HomeDashboardRow {
  layout: string;
  slots: HomeDashboardSlot[];
}

export interface HomeDashboardLayout {
  rows: HomeDashboardRow[];
}

export interface HomeDashboardTemplate {
  id: string;
  clientId: string;
  name: string;
  description: string | null;
  roleType: "INTERNAL" | "EXTERNAL";
  layout: HomeDashboardLayout;
  isSystem: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface HomeDashboardWidgetCatalogEntry {
  key: string;
  supportedRoleTypes: ("INTERNAL" | "EXTERNAL")[];
}

export interface HomeDashboardRowLayoutCatalogEntry {
  key: string;
  slotCount: number;
}
