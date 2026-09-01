import type { ComponentType } from "react";
import { AiAssistantWidget } from "@/components/home/widgets/AiAssistantWidget";
import { ApprovalsWidget } from "@/components/home/widgets/ApprovalsWidget";
import { ProgramPerformanceWidget } from "@/components/home/widgets/ProgramPerformanceWidget";
import { RewardsBalancesWidget } from "@/components/home/widgets/RewardsBalancesWidget";
import { TenXSuggestionsWidget } from "@/components/home/widgets/TenXSuggestionsWidget";
import type { HomeDashboardWidgetKey } from "@/types/home-dashboard.types";

export const homeDashboardWidgetRegistry: Record<
  HomeDashboardWidgetKey,
  ComponentType
> = {
  ai_assistant: AiAssistantWidget,
  program_performance: ProgramPerformanceWidget,
  tenx_suggestions: TenXSuggestionsWidget,
  rewards_balances: RewardsBalancesWidget,
  approvals: ApprovalsWidget,
};

export function getWidgetComponent(key: string): ComponentType | undefined {
  return homeDashboardWidgetRegistry[key as HomeDashboardWidgetKey];
}
