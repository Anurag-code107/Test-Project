import { DollarSign, Award } from "lucide-react";
import { getCurrency } from "@/config/currencies";

export interface RewardBreakdownData {
  monetary: {
    cash: { amount: number; percent: number };
    points: { amount: number; percent: number };
  };
  nonMonetary: {
    credits: { count: number };
    tickets: { count: number };
  };
}

interface RewardBreakdownExpandedProps {
  breakdown: RewardBreakdownData;
  formatAmount?: (amount: number) => string;
}

export function RewardBreakdownExpanded({
  breakdown,
  formatAmount = (amount) => `$${amount.toLocaleString()}`,
}: RewardBreakdownExpandedProps) {
  const cashConfig = getCurrency("cash");
  const pointsConfig = getCurrency("points");
  const creditsConfig = getCurrency("credits");
  const ticketsConfig = getCurrency("tickets");
  const CashIcon = cashConfig.icon;
  const PointsIcon = pointsConfig.icon;
  const CreditsIcon = creditsConfig.icon;
  const TicketsIcon = ticketsConfig.icon;

  return (
    <div className="mt-3 pt-3 border-t border-border space-y-4">
      {/* Monetary Rewards */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <DollarSign className="h-4 w-4 text-emerald-500" />
          <span className="text-sm font-semibold text-foreground">
            Monetary Rewards
          </span>
          <span className="text-xs text-muted-foreground">
            (Included in total)
          </span>
        </div>
        <div className="pl-6 space-y-2">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <CashIcon className={`h-3.5 w-3.5 ${cashConfig.iconClass}`} />
              <span className="text-sm text-muted-foreground">
                {cashConfig.label}
              </span>
            </div>
            <div className="text-right">
              <span className="text-sm font-medium text-foreground">
                {formatAmount(breakdown.monetary.cash.amount)}
              </span>
              <span className="text-xs text-muted-foreground ml-1">
                ({breakdown.monetary.cash.percent}%)
              </span>
            </div>
          </div>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <PointsIcon className={`h-3.5 w-3.5 ${pointsConfig.iconClass}`} />
              <span className="text-sm text-muted-foreground">
                {pointsConfig.label}
              </span>
            </div>
            <div className="text-right">
              <span className="text-sm font-medium text-foreground">
                {formatAmount(breakdown.monetary.points.amount)}
              </span>
              <span className="text-xs text-muted-foreground ml-1">
                ({breakdown.monetary.points.percent}%)
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Non-Monetary Rewards */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <Award className="h-4 w-4 text-purple-500" />
          <span className="text-sm font-semibold text-foreground">
            Non-Monetary Rewards
          </span>
          <span className="text-xs text-muted-foreground">
            (Not in $ total)
          </span>
        </div>
        <div className="pl-6 space-y-2">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <CreditsIcon
                className={`h-3.5 w-3.5 ${creditsConfig.iconClass}`}
              />
              <span className="text-sm text-muted-foreground">
                {creditsConfig.label}
              </span>
            </div>
            <span className="text-sm font-medium text-foreground">
              {breakdown.nonMonetary.credits.count.toLocaleString()} earned
            </span>
          </div>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <TicketsIcon
                className={`h-3.5 w-3.5 ${ticketsConfig.iconClass}`}
              />
              <span className="text-sm text-muted-foreground">
                {ticketsConfig.label}
              </span>
            </div>
            <span className="text-sm font-medium text-foreground">
              {breakdown.nonMonetary.tickets.count.toLocaleString()} earned
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
