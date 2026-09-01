import { useEffect } from "react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { GraduationCap } from "lucide-react";
import { useRecommendationInsights } from "@/hooks/useRecommendationInsights";
import { AiInsightsSection } from "./AiInsightsSection";
import { getCurrency } from "@/config/currencies";
import type { TrainingRecommendationResponse } from "@/types/recommendation.types";

interface TrainingDetailDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  recommendation: TrainingRecommendationResponse | null;
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

export function TrainingDetailDrawer({
  open,
  onOpenChange,
  recommendation,
}: TrainingDetailDrawerProps) {
  const { insight, isStreaming, error, startStreaming } =
    useRecommendationInsights();

  useEffect(() => {
    if (open && recommendation) {
      startStreaming("training", recommendation.courseId);
    }
  }, [open, recommendation, startStreaming]);

  if (!recommendation) return null;

  const reward = formatReward(
    recommendation.rewardAmount,
    recommendation.rewardCurrencyId,
  );

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full sm:max-w-2xl flex flex-col p-0">
        {/* Header */}
        <SheetHeader className="px-6 pt-6 pb-4 border-b border-border">
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-lg bg-primary/10">
                <GraduationCap className="h-5 w-5 text-primary" />
              </div>
              <div>
                <SheetTitle className="text-lg font-semibold text-foreground">
                  {recommendation.courseName}
                </SheetTitle>
                <div className="flex items-center gap-2 mt-1">
                  <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-muted text-muted-foreground">
                    {recommendation.courseCategory}
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
              About This Course
            </h4>
            <p className="text-sm text-muted-foreground">
              {recommendation.courseDescription}
            </p>
          </div>

          {/* Details grid */}
          <div className="grid grid-cols-2 gap-3">
            <div className="rounded-lg border border-border p-3">
              <span className="text-xs text-muted-foreground">
                Product Category
              </span>
              <p className="text-sm font-medium mt-1">
                {recommendation.productCategory || "General"}
              </p>
            </div>
            <div className="rounded-lg border border-border p-3">
              <span className="text-xs text-muted-foreground">Reward</span>
              <p className="text-sm font-semibold text-success mt-1">
                {reward || "—"}
              </p>
            </div>
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
          <Button onClick={() => onOpenChange(false)} className="w-full">
            Close
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}
