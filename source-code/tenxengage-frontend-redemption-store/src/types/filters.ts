export type DateFilter = "recent" | "quarter" | "year" | "custom";

export type EngagementType = "ALL" | "SALES" | "ENABLEMENT" | "JOURNEYS";

export const engagementTypeLabels: Record<EngagementType, string> = {
  ALL: "All Incentive Types",
  SALES: "Sales Incentives",
  ENABLEMENT: "Enablement Incentives",
  JOURNEYS: "Journeys",
};
