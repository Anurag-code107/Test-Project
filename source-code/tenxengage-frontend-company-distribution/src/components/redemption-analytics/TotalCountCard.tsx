// Adapted from: src/pages/DashboardPage.tsx (production analog from Mirror)
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { RedemptionCountDto } from "@/types/redemption-analytics.types";

const STATUS_LABELS: Record<string, string> = {
  PENDING: "Pending",
  PROCESSING: "Processing",
  COMPLETED: "Completed",
  FAILED: "Failed",
  CANCELLED: "Cancelled",
};

const STATUS_ORDER = ["PENDING", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED"] as const;

interface TotalCountCardProps {
  data: RedemptionCountDto;
}

export function TotalCountCard({ data }: TotalCountCardProps) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">Total Redemptions</CardTitle>
      </CardHeader>
      <CardContent>
        {data.hasActivity ? (
          <>
            <div className="text-2xl font-semibold">
              {data.total.toLocaleString()}
            </div>
            <ul className="mt-2 space-y-1" aria-label="Redemptions by status">
              {STATUS_ORDER.map((status) => (
                <li
                  key={status}
                  className="flex items-center justify-between text-xs text-muted-foreground"
                >
                  <span>{STATUS_LABELS[status]}</span>
                  <span className="font-medium text-foreground">
                    {(data.byStatus[status] ?? 0).toLocaleString()}
                  </span>
                </li>
              ))}
            </ul>
          </>
        ) : (
          <div
            role="status"
            className="text-sm text-muted-foreground py-2"
          >
            No redemptions in this period
          </div>
        )}
      </CardContent>
    </Card>
  );
}
