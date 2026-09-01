import { format } from "date-fns";
import type { DateFilter } from "@/types/filters";

export interface ChartDataPoint {
  quarter: string;
  value: number;
}

export function generateHistoricalData(
  baseValue: number,
  isPercentage = false,
  dateFilter: DateFilter,
  customStartDate?: Date,
  customEndDate?: Date,
): ChartDataPoint[] {
  let labels: string[] = [];
  let numPoints = 6;

  switch (dateFilter) {
    case "recent":
      labels = [];
      for (let i = 5; i >= 0; i--) {
        const date = new Date();
        date.setDate(date.getDate() - i * 5);
        labels.push(format(date, "MMM d"));
      }
      break;
    case "quarter":
      labels = ["Week 1", "Week 2", "Week 3", "Week 4", "Week 5", "Week 6"];
      break;
    case "year":
      labels = ["Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
      break;
    case "custom":
      if (customStartDate && customEndDate) {
        const daysDiff = Math.ceil(
          (customEndDate.getTime() - customStartDate.getTime()) /
            (1000 * 60 * 60 * 24),
        );
        if (daysDiff <= 14) {
          numPoints = Math.min(7, daysDiff);
          for (let i = numPoints - 1; i >= 0; i--) {
            const date = new Date(customEndDate);
            date.setDate(date.getDate() - i * Math.floor(daysDiff / numPoints));
            labels.push(format(date, "MMM d"));
          }
        } else if (daysDiff <= 90) {
          labels = ["Wk 1", "Wk 2", "Wk 3", "Wk 4", "Wk 5", "Wk 6"];
        } else {
          labels = [
            "Month 1",
            "Month 2",
            "Month 3",
            "Month 4",
            "Month 5",
            "Month 6",
          ];
        }
      } else {
        labels = [
          "Q3 2024",
          "Q4 2024",
          "Q1 2025",
          "Q2 2025",
          "Q3 2025",
          "Q4 2025",
        ];
      }
      break;
    default:
      labels = [
        "Q3 2024",
        "Q4 2024",
        "Q1 2025",
        "Q2 2025",
        "Q3 2025",
        "Q4 2025",
      ];
  }

  const data: ChartDataPoint[] = [];
  let value = isPercentage ? baseValue - 15 : baseValue * 0.7;

  for (const label of labels) {
    const growth = isPercentage
      ? Math.random() * 4 + 1
      : baseValue * (0.05 + Math.random() * 0.03);
    value = Math.min(isPercentage ? 100 : baseValue * 1.1, value + growth);
    data.push({
      quarter: label,
      value: Math.round(value),
    });
  }
  return data;
}

export function getComparisonLabel(
  dateFilter: DateFilter,
  customStartDate?: Date,
  customEndDate?: Date,
): string {
  switch (dateFilter) {
    case "recent":
      return "vs previous 30 days";
    case "quarter":
      return "vs last quarter";
    case "year":
      return "vs last year";
    case "custom":
      if (customStartDate && customEndDate) {
        const daysDiff = Math.ceil(
          (customEndDate.getTime() - customStartDate.getTime()) /
            (1000 * 60 * 60 * 24),
        );
        return `vs previous ${daysDiff} days`;
      }
      return "vs previous period";
    default:
      return "vs previous period";
  }
}
