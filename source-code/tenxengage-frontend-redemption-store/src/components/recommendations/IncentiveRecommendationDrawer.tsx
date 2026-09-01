import { useEffect } from "react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Target, Clock, Zap, ArrowRight } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useRecommendationInsights } from "@/hooks/useRecommendationInsights";
import { AiInsightsSection } from "./AiInsightsSection";
import { getCurrency } from "@/config/currencies";
import type { IncentiveRecommendationResponse } from "@/types/recommendation.types";

interface IncentiveRecommendationDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  recommendation: IncentiveRecommendationResponse | null;
}

function formatDaysRemaining(endDate: string | null): string {
  if (!endDate) return "No end date";
  const days = Math.ceil(
    (new Date(endDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24),
  );
  if (days < 0) return "Expired";
  if (days === 0) return "Ends today";
  if (days === 1) return "1 day left";
  return `${days} days left`;
}

function formatReward(amount: number, currencyId: string | null): string {
  if (!amount || amount <= 0 || !currencyId) return "";
  try {
    const currency = getCurrency(currencyId);
    return `Earn ${currency.rewardFormat(amount)}`;
  } catch {
    return `Earn $${amount}`;
  }
}

export function IncentiveRecommendationDrawer({
  open,
  onOpenChange,
  recommendation,
}: IncentiveRecommendationDrawerProps) {
  const { insight, isStreaming, error, startStreaming } =
    useRecommendationInsights();
  const navigate = useNavigate();

  useEffect(() => {
    if (open && recommendation) {
      startStreaming("incentive", recommendation.incentiveId);
    }
  }, [open, recommendation, startStreaming]);

  if (!recommendation) return null;

  const budgetUsedPct = 100 - (recommendation.budgetRemainingPct ?? 0);
  const reward = formatReward(
    recommendation.rewardAmount,
    recommendation.rewardCurrency,
  );

  const handleGoToIncentive = () => {
    onOpenChange(false);
    navigate("/incentives");
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full sm:max-w-2xl flex flex-col p-0">
        {/* Header */}
        <SheetHeader className="px-6 pt-6 pb-4 border-b border-border">
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-lg bg-primary/10">
                <Target className="h-5 w-5 text-primary" />
              </div>
              <div>
                <SheetTitle className="text-lg font-semibold text-foreground">
                  {recommendation.incentiveName}
                </SheetTitle>
                <div className="flex items-center gap-2 mt-1">
                  <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-primary/10 text-primary">
                    {recommendation.incentiveType}
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {formatDaysRemaining(recommendation.endDate)}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </SheetHeader>

        {/* Scrollable content */}
        <div className="flex-1 overflow-y-auto px-6 py-4 space-y-5">
          {/* Description */}
          <div className="rounded-xl border border-border p-4">
            <h4 className="text-sm font-semibold text-foreground mb-2">
              About This Incentive
            </h4>
            <p className="text-sm text-muted-foreground">
              {recommendation.description}
            </p>
          </div>

          {/* Details grid */}
          <div className="grid grid-cols-2 gap-3">
            <div className="rounded-lg border border-border p-3">
              <div className="flex items-center gap-1.5 mb-1">
                <Clock className="h-3.5 w-3.5 text-muted-foreground" />
                <span className="text-xs text-muted-foreground">
                  Time Remaining
                </span>
              </div>
              <span className="text-sm font-medium">
                {formatDaysRemaining(recommendation.endDate)}
              </span>
            </div>
            <div className="rounded-lg border border-border p-3">
              <span className="text-xs text-muted-foreground">Reward</span>
              <p className="text-sm font-semibold text-success mt-1">
                {reward || "—"}
              </p>
            </div>
          </div>

          {/* Budget utilization */}
          <div className="rounded-lg border border-border p-3">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs text-muted-foreground">
                Budget Utilization
              </span>
              {budgetUsedPct >= 85 && (
                <div className="flex items-center gap-1 text-warning">
                  <Zap className="h-3 w-3" />
                  <span className="text-xs font-medium">
                    {budgetUsedPct >= 90 ? "Almost capped!" : "Filling up"}
                  </span>
                </div>
              )}
            </div>
            <div className="w-full bg-muted rounded-full h-2">
              <div
                className={`h-2 rounded-full transition-all ${
                  budgetUsedPct >= 90
                    ? "bg-destructive"
                    : budgetUsedPct >= 70
                      ? "bg-warning"
                      : "bg-primary"
                }`}
                style={{ width: `${Math.min(100, budgetUsedPct)}%` }}
              />
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {budgetUsedPct.toFixed(0)}% utilized
            </p>
          </div>

          {/* AI Insights */}
          <AiInsightsSection
            insight={insight}
            isStreaming={isStreaming}
            error={error}
          />
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-border">
          <Button onClick={handleGoToIncentive} className="w-full">
            <ArrowRight className="h-4 w-4 mr-2" />
            Go To Incentive
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}
