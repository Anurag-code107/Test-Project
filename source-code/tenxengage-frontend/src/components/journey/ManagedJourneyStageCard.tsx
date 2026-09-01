import { Calendar, TrendingUp, User } from "lucide-react";
import {
  cardAccent,
  engagementColors,
  engagementIconMap,
  formatIncentiveDate,
} from "@/components/incentive-card/incentive-card.config";
import { RewardBreakdownHover } from "@/components/RewardBreakdownHover";
import { Progress } from "@/components/ui/progress";
import { getCurrency } from "@/config/currencies";
import { cn } from "@/lib/utils";
import type { IncentiveResponse, IncentiveType } from "@/types/incentive.types";

function TypeIcon({
  type,
  className,
}: {
  type: IncentiveType;
  className?: string;
}) {
  const Icon = engagementIconMap[type];
  return <Icon className={className} />;
}

interface ManagedJourneyStageCardProps {
  incentive: IncentiveResponse;
  /** Right-side slot rendered in the footer (e.g. the edit pencil button). */
  extraActions?: React.ReactNode;
}

/**
 * Stage card shown inside a Journey on Manage Incentives. Mirrors the manage
 * shape of `ManagedIncentiveCard` (description + budget utilization +
 * creator footer) instead of the participant-facing `PartnerIncentiveCard`
 * reused in the View Incentives variant — admins shouldn't see "Earn up to"
 * reward banners or partner progress bars on the manage view.
 */
export function ManagedJourneyStageCard({
  incentive,
  extraActions,
}: ManagedJourneyStageCardProps) {
  const accent = cardAccent[incentive.incentiveType];
  const startDate = incentive.startDate ? new Date(incentive.startDate) : null;
  const endDate = incentive.endDate ? new Date(incentive.endDate) : null;

  const totalNum = incentive.budgetTotal ? parseFloat(incentive.budgetTotal) : 0;
  const utilPercent = incentive.budgetUtilizationPercent ?? 0;
  const utilizedNum = totalNum * (utilPercent / 100);

  const budgetEntries: Record<string, string | number> = {};
  const usedEntries: Record<string, string | number> = {};
  let monetaryTotal = 0;
  let monetaryUsed = 0;
  if (incentive.budgets && incentive.budgets.length > 0) {
    for (const b of incentive.budgets) {
      const amt = parseFloat(b.totalBudget) || 0;
      budgetEntries[b.currencyId] = amt;
      usedEntries[b.currencyId] = Math.round(amt * (utilPercent / 100));
      if (getCurrency(b.currencyId).type === "monetary") {
        monetaryTotal += amt;
        monetaryUsed += amt * (utilPercent / 100);
      }
    }
  } else if (totalNum > 0) {
    const currency = incentive.budgetCurrency || "cash";
    budgetEntries[currency] = totalNum;
    usedEntries[currency] = utilizedNum;
    if (getCurrency(currency).type === "monetary") {
      monetaryTotal = totalNum;
      monetaryUsed = utilizedNum;
    }
  }
  const hasBudget = totalNum > 0 || Object.keys(budgetEntries).length > 0;

  return (
    <div className="relative flex flex-col h-full flex-1 p-4">
      <div
        className="-mx-4 -mt-4 px-4 pt-3 pb-2.5 rounded-t-xl"
        style={{
          background: accent.bandGradient,
          borderBottom: `1px solid ${accent.bandBorder}`,
        }}
      >
        <div className="flex items-center gap-2 min-w-0">
          <div
            className={cn(
              "flex items-center justify-center h-7 w-7 rounded-lg shrink-0",
              engagementColors[incentive.incentiveType].replace(
                "text-",
                "bg-",
              ) + "/10",
            )}
          >
            <TypeIcon
              type={incentive.incentiveType}
              className={cn(
                "h-3.5 w-3.5",
                engagementColors[incentive.incentiveType],
              )}
            />
          </div>
          <h3 className="font-semibold text-sm text-foreground leading-tight truncate">
            {incentive.name}
          </h3>
        </div>
      </div>

      <div className="flex flex-col flex-1 pt-3">
        {incentive.description && (
          <p className="text-sm text-muted-foreground line-clamp-2 mb-3 leading-relaxed">
            {incentive.description}
          </p>
        )}

        {hasBudget && (
          <div className="space-y-2 mb-3">
            <div className="flex items-center justify-between text-xs">
              <span className="text-muted-foreground flex items-center gap-1.5">
                <TrendingUp className="h-3 w-3" />
                Budget
              </span>
              <span className="font-semibold text-foreground">
                {utilPercent}%
              </span>
            </div>
            <Progress value={utilPercent} className="h-1.5" />
            <div className="flex justify-between text-xs text-muted-foreground">
              <RewardBreakdownHover
                label="Used"
                entries={usedEntries}
                monetaryTotal={monetaryUsed}
                suffix="used"
                animate={false}
              />
              <RewardBreakdownHover
                label="Budget"
                entries={budgetEntries}
                monetaryTotal={monetaryTotal}
                suffix="total"
                animate={false}
              />
            </div>
          </div>
        )}

        <div className="mt-auto pt-3 border-t border-border">
          <div className="flex items-center justify-between gap-2">
            <div className="space-y-1 min-w-0">
              {startDate && endDate ? (
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <Calendar className="h-3 w-3 shrink-0" />
                  <span className="truncate">
                    {formatIncentiveDate(startDate)} –{" "}
                    {formatIncentiveDate(endDate)}
                  </span>
                </div>
              ) : endDate ? (
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <Calendar className="h-3 w-3 shrink-0" />
                  <span className="truncate">
                    Ends {formatIncentiveDate(endDate)}
                  </span>
                </div>
              ) : null}
              {incentive.createdByName && (
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <User className="h-3 w-3 shrink-0" />
                  <span className="truncate">{incentive.createdByName}</span>
                </div>
              )}
            </div>
            {extraActions && (
              <div className="flex items-center gap-1 shrink-0">
                {extraActions}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
