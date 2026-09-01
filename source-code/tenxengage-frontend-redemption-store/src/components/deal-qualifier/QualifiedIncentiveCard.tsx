import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { CheckCircle2, AlertCircle, TrendingUp } from "lucide-react";
import type { QualifiedIncentiveResult } from "@/types/deal-qualifier.types";
import { getCurrency } from "@/config/currencies";

interface QualifiedIncentiveCardProps {
  result: QualifiedIncentiveResult;
  onViewDetails: () => void;
}

export function QualifiedIncentiveCard({
  result,
  onViewDetails,
}: QualifiedIncentiveCardProps) {
  const {
    incentiveName,
    incentiveDescription,
    matchPercentage,
    estimatedReward,
    rewardCurrency,
    metCriteria,
    unmetCriteria,
    payoutBreakdown,
  } = result;

  const currency = getCurrency(rewardCurrency || "cash");

  const getMatchColor = (percentage: number) => {
    if (percentage >= 90)
      return "bg-green-500/10 text-green-700 border-green-500/20";
    if (percentage >= 70)
      return "bg-yellow-500/10 text-yellow-700 border-yellow-500/20";
    return "bg-muted text-muted-foreground border-border";
  };

  const formatReward = (amount: number) => {
    if (currency.type === "monetary") {
      return `$${amount.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
    }
    return `${amount.toLocaleString()} ${currency.label}`;
  };

  return (
    <Card className="hover:bg-muted/30 transition-colors">
      <CardContent className="pt-6">
        <div className="space-y-4">
          {/* Header */}
          <div className="flex items-start justify-between gap-4">
            <div className="flex-1 min-w-0">
              <h3 className="font-semibold text-foreground mb-1">
                {incentiveName}
              </h3>
              <p className="text-sm text-muted-foreground line-clamp-2">
                {incentiveDescription}
              </p>
            </div>
            <div className="flex flex-col items-end gap-2 shrink-0">
              <Badge
                className="bg-primary/10 text-primary border-primary/20"
                variant="outline"
              >
                Sales Incentive
              </Badge>
              <Badge
                className={getMatchColor(matchPercentage)}
                variant="outline"
              >
                {matchPercentage}% Match
              </Badge>
            </div>
          </div>

          {/* Estimated Reward + Payout Tier */}
          <div className="bg-primary/5 border border-primary/10 rounded-lg p-4">
            <div className="flex items-start justify-between">
              <div>
                <div className="text-sm text-muted-foreground mb-1">
                  Estimated Reward
                </div>
                <div className="text-2xl font-semibold text-primary">
                  {formatReward(estimatedReward)}
                </div>
              </div>
              {payoutBreakdown?.gapToNextTier != null &&
                payoutBreakdown.gapToNextTier > 0 && (
                  <div className="flex items-center gap-1 text-xs text-amber-600 bg-amber-50 px-2 py-1 rounded-md">
                    <TrendingUp className="h-3 w-3" />
                    <span>
                      ${payoutBreakdown.gapToNextTier.toLocaleString()} to next
                      tier
                    </span>
                  </div>
                )}
            </div>
          </div>

          {/* Matched Criteria */}
          {metCriteria.length > 0 && (
            <div className="space-y-2">
              <div className="text-sm font-medium text-foreground">
                Requirements Met
              </div>
              <div className="space-y-1">
                {metCriteria.slice(0, 3).map((criterion, index) => (
                  <div key={index} className="flex items-start gap-2 text-sm">
                    <CheckCircle2 className="h-4 w-4 text-green-600 shrink-0 mt-0.5" />
                    <span className="text-muted-foreground">
                      {criterion.description}
                    </span>
                  </div>
                ))}
                {metCriteria.length > 3 && (
                  <div className="text-xs text-muted-foreground ml-6">
                    +{metCriteria.length - 3} more requirements met
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Unmet Criteria */}
          {unmetCriteria.length > 0 && (
            <div className="space-y-2">
              <div className="text-sm font-medium text-foreground">
                Requirements Not Met
              </div>
              <div className="space-y-1">
                {unmetCriteria.slice(0, 2).map((criterion, index) => (
                  <div key={index} className="space-y-0.5">
                    <div className="flex items-start gap-2 text-sm">
                      <AlertCircle className="h-4 w-4 text-yellow-600 shrink-0 mt-0.5" />
                      <span className="text-muted-foreground">
                        {criterion.description}
                      </span>
                    </div>
                    {criterion.hint && (
                      <div className="ml-6 text-xs text-amber-600">
                        {criterion.hint}
                      </div>
                    )}
                  </div>
                ))}
                {unmetCriteria.length > 2 && (
                  <div className="text-xs text-muted-foreground ml-6">
                    +{unmetCriteria.length - 2} more requirements needed
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Actions */}
          <div className="flex gap-2 pt-2">
            <Button
              onClick={onViewDetails}
              variant="outline"
              className="flex-1"
            >
              View Details
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
