import { useState, useRef, useEffect, useCallback, useMemo } from "react";
import { PageBanner } from "@/components/PageBanner";
import { DealQualifierForm } from "@/components/deal-qualifier/DealQualifierForm";
import { QualifiedIncentiveCard } from "@/components/deal-qualifier/QualifiedIncentiveCard";
import { AiInsightsSection } from "@/components/recommendations/AiInsightsSection";
import { useEvaluateDeal } from "@/hooks/useDealQualifier";
import { useDealQualifierInsights } from "@/hooks/useDealQualifierInsights";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Calendar,
  Target,
  ClipboardList,
  Search,
  ChevronsDown,
  TrendingUp,
  Loader2,
} from "lucide-react";
import { getCurrency } from "@/config/currencies";
import type {
  DealQualifierRequest,
  QualifiedIncentiveResult,
} from "@/types/deal-qualifier.types";

export default function DealQualifierPage() {
  const [selectedResult, setSelectedResult] =
    useState<QualifiedIncentiveResult | null>(null);
  const [lastRequest, setLastRequest] = useState<DealQualifierRequest | null>(
    null,
  );
  const [showScrollIndicator, setShowScrollIndicator] = useState(false);
  const resultsScrollRef = useRef<HTMLDivElement>(null);

  const evaluateDeal = useEvaluateDeal();
  const {
    insight,
    isStreaming,
    error: insightError,
    startStreaming,
    stopStreaming,
  } = useDealQualifierInsights();

  const results = useMemo(
    () => evaluateDeal.data?.results ?? [],
    [evaluateDeal.data?.results],
  );
  const hasSearched = evaluateDeal.isSuccess || evaluateDeal.isError;

  const checkScroll = useCallback(() => {
    const el = resultsScrollRef.current;
    if (!el) return;
    const hasMore = el.scrollHeight - el.scrollTop - el.clientHeight > 10;
    setShowScrollIndicator(hasMore);
  }, []);

  useEffect(() => {
    checkScroll();
  }, [results, hasSearched, checkScroll]);

  const handleQualify = (request: DealQualifierRequest) => {
    setLastRequest(request);
    evaluateDeal.mutate(request);
  };

  const handleViewDetails = (result: QualifiedIncentiveResult) => {
    setSelectedResult(result);
    // Start streaming insights when drawer opens
    if (lastRequest) {
      startStreaming(result.incentiveId, lastRequest);
    }
  };

  const handleCloseDrawer = () => {
    stopStreaming();
    setSelectedResult(null);
  };

  const formatReward = (amount: number, currencyId?: string) => {
    const currency = getCurrency(currencyId || "cash");
    if (currency.type === "monetary") {
      return `$${amount.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
    }
    return `${amount.toLocaleString()} ${currency.label}`;
  };

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return "—";
    try {
      return new Date(dateStr).toLocaleDateString("en-US", {
        year: "numeric",
        month: "short",
        day: "numeric",
      });
    } catch {
      return dateStr;
    }
  };

  const getMatchColor = (percentage: number) => {
    if (percentage >= 90)
      return "bg-green-500/10 text-green-700 border-green-500/20";
    if (percentage >= 70)
      return "bg-yellow-500/10 text-yellow-700 border-yellow-500/20";
    return "bg-muted text-muted-foreground border-border";
  };

  return (
    <div
      className="space-y-6 h-[calc(100vh-8rem)] flex flex-col"
      data-tour="deal-qualifier-form"
    >
      {/* Header */}
      <PageBanner
        theme="deal-qualifier"
        title="Deal Qualifier"
        subtitle="Check which incentives your deal qualifies for before you close it"
      />

      {/* Side-by-side layout */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start flex-1 min-h-0">
        {/* Left: Form */}
        <Card className="border h-full">
          <CardHeader className="pb-4">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-lg bg-gradient-to-br from-primary to-primary/80 shadow-md">
                <Search className="h-5 w-5 text-primary-foreground" />
              </div>
              <CardTitle className="text-xl font-semibold text-foreground">
                Deal Parameters
              </CardTitle>
            </div>
          </CardHeader>
          <CardContent>
            <DealQualifierForm
              onQualify={handleQualify}
              isLoading={evaluateDeal.isPending}
            />
          </CardContent>
        </Card>

        {/* Right: Results */}
        <Card className="border h-full flex flex-col overflow-hidden">
          <CardHeader className="pb-4">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-lg bg-gradient-to-br from-emerald-500 to-emerald-600 shadow-md">
                <ClipboardList className="h-5 w-5 text-white" />
              </div>
              <div>
                <CardTitle className="text-xl font-semibold text-foreground">
                  Eligibility Results
                </CardTitle>
                {hasSearched && (
                  <p className="text-sm text-muted-foreground mt-0.5">
                    Results Are Estimates Only And Are Not Guaranteed
                  </p>
                )}
              </div>
            </div>
          </CardHeader>
          <div className="flex-1 overflow-hidden min-h-0 relative">
            <CardContent
              ref={resultsScrollRef}
              onScroll={checkScroll}
              className="h-full overflow-y-auto"
            >
              {evaluateDeal.isPending ? (
                <div className="text-center py-16">
                  <Loader2 className="h-8 w-8 text-primary animate-spin mx-auto mb-4" />
                  <p className="text-sm text-muted-foreground">
                    Evaluating your deal against active incentives...
                  </p>
                </div>
              ) : !hasSearched ? (
                <div className="text-center py-16">
                  <Target className="h-12 w-12 text-muted-foreground/40 mx-auto mb-4" />
                  <h3 className="text-lg font-semibold text-muted-foreground mb-2">
                    No Results Yet
                  </h3>
                  <p className="text-sm text-muted-foreground max-w-xs mx-auto">
                    Fill in your deal parameters and click &quot;Check
                    Eligibility&quot; to see matching incentives
                  </p>
                </div>
              ) : results.length > 0 ? (
                <div className="space-y-4">
                  {/* Results List */}
                  <div className="space-y-4">
                    <h2 className="text-lg font-semibold text-foreground">
                      Matching Incentives
                    </h2>
                    {results.map((result) => (
                      <QualifiedIncentiveCard
                        key={result.incentiveId}
                        result={result}
                        onViewDetails={() => handleViewDetails(result)}
                      />
                    ))}
                  </div>
                </div>
              ) : (
                <div className="text-center py-12">
                  <Target className="h-12 w-12 text-muted-foreground mx-auto mb-4" />
                  <h3 className="text-lg font-semibold text-foreground mb-2">
                    No Matching Incentives
                  </h3>
                  <p className="text-muted-foreground max-w-md mx-auto text-sm">
                    Your deal doesn&apos;t currently match any active
                    incentives. Try adjusting your criteria.
                  </p>
                </div>
              )}
            </CardContent>

            {/* Scroll indicator */}
            {showScrollIndicator && hasSearched && results.length > 0 && (
              <div className="absolute bottom-0 left-0 right-0 h-16 flex items-end justify-center bg-gradient-to-t from-card via-card/80 to-transparent pointer-events-none pb-2">
                <div className="flex items-center gap-1 pointer-events-auto">
                  <span className="text-xs font-medium text-primary opacity-80">
                    Scroll for more
                  </span>
                  <ChevronsDown className="h-4 w-4 text-primary animate-bounce" />
                </div>
              </div>
            )}
          </div>
        </Card>
      </div>

      {/* Detail Sheet */}
      {selectedResult && (
        <Sheet open={!!selectedResult} onOpenChange={handleCloseDrawer}>
          <SheetContent className="w-full sm:max-w-2xl overflow-y-auto">
            <SheetHeader>
              <SheetTitle className="text-2xl">
                {selectedResult.incentiveName}
              </SheetTitle>
              <SheetDescription className="flex gap-2 flex-wrap">
                <Badge
                  className="bg-primary/10 text-primary border-primary/20"
                  variant="outline"
                >
                  Sales Incentive
                </Badge>
                <Badge
                  className={getMatchColor(selectedResult.matchPercentage)}
                  variant="outline"
                >
                  {selectedResult.matchPercentage}% Match
                </Badge>
              </SheetDescription>
            </SheetHeader>

            <div className="mt-6 space-y-4">
              {/* Match Summary */}
              <div className="rounded-xl border p-4">
                <div className="text-sm font-medium text-foreground mb-3">
                  Match Summary
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <div className="text-xs text-muted-foreground mb-1">
                      Estimated Reward
                    </div>
                    <div className="text-xl font-semibold text-primary">
                      {formatReward(
                        selectedResult.estimatedReward,
                        selectedResult.rewardCurrency,
                      )}
                    </div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground mb-1">
                      Match Quality
                    </div>
                    <div className="text-xl font-semibold text-foreground">
                      {selectedResult.matchPercentage}%
                    </div>
                  </div>
                </div>
              </div>

              {/* AI Insights */}
              <AiInsightsSection
                insight={insight}
                isStreaming={isStreaming}
                error={insightError}
              />

              {/* Payout Breakdown */}
              {selectedResult.payoutBreakdown && (
                <div className="rounded-xl border p-4">
                  <div className="text-sm font-medium text-foreground mb-3">
                    Payout Tier
                  </div>
                  <div className="space-y-3">
                    {selectedResult.payoutBreakdown.currentTierPayoutValue !=
                      null && (
                      <div className="flex items-center justify-between text-sm">
                        <span className="text-muted-foreground">
                          Current tier
                        </span>
                        <span className="font-medium">
                          {selectedResult.payoutBreakdown
                            .currentTierPayoutType === "PERCENTAGE"
                            ? `${selectedResult.payoutBreakdown.currentTierPayoutValue}%`
                            : `$${selectedResult.payoutBreakdown.currentTierPayoutValue.toLocaleString()}`}
                          {selectedResult.payoutBreakdown.currentTierMin !=
                            null && (
                            <span className="text-muted-foreground font-normal ml-1">
                              ($
                              {selectedResult.payoutBreakdown.currentTierMin.toLocaleString()}
                              {selectedResult.payoutBreakdown.currentTierMax !=
                              null
                                ? ` - $${selectedResult.payoutBreakdown.currentTierMax.toLocaleString()}`
                                : "+"}
                              )
                            </span>
                          )}
                        </span>
                      </div>
                    )}
                    {selectedResult.payoutBreakdown.nextTierPayoutValue !=
                      null && (
                      <div className="flex items-center justify-between text-sm">
                        <span className="text-muted-foreground">Next tier</span>
                        <span className="font-medium text-amber-600">
                          {selectedResult.payoutBreakdown
                            .currentTierPayoutType === "PERCENTAGE"
                            ? `${selectedResult.payoutBreakdown.nextTierPayoutValue}%`
                            : `$${selectedResult.payoutBreakdown.nextTierPayoutValue.toLocaleString()}`}
                          {selectedResult.payoutBreakdown.nextTierMin !=
                            null && (
                            <span className="text-muted-foreground font-normal ml-1">
                              (from $
                              {selectedResult.payoutBreakdown.nextTierMin.toLocaleString()}
                              )
                            </span>
                          )}
                        </span>
                      </div>
                    )}
                    {selectedResult.payoutBreakdown.gapToNextTier != null &&
                      selectedResult.payoutBreakdown.gapToNextTier > 0 && (
                        <div className="flex items-center gap-2 text-sm bg-amber-50 text-amber-700 rounded-md p-2.5">
                          <TrendingUp className="h-4 w-4 shrink-0" />
                          <span>
                            Increase deal by $
                            {selectedResult.payoutBreakdown.gapToNextTier.toLocaleString()}{" "}
                            to reach the next tier
                          </span>
                        </div>
                      )}
                    {selectedResult.payoutBreakdown.maxPerDeal != null && (
                      <div className="text-xs text-muted-foreground">
                        Max per deal: $
                        {selectedResult.payoutBreakdown.maxPerDeal.toLocaleString()}
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* Objective */}
              {selectedResult.incentiveDescription && (
                <div className="rounded-xl border p-4">
                  <h4 className="text-sm font-medium text-foreground mb-2">
                    Objective
                  </h4>
                  <p className="text-sm text-muted-foreground">
                    {selectedResult.incentiveDescription}
                  </p>
                </div>
              )}

              {/* Requirements Met */}
              {selectedResult.metCriteria.length > 0 && (
                <div className="rounded-xl border p-4">
                  <h4 className="text-sm font-medium text-foreground mb-3">
                    Requirements Met
                  </h4>
                  <div className="space-y-2">
                    {selectedResult.metCriteria.map((criterion, index) => (
                      <div
                        key={index}
                        className="flex items-start gap-2 text-sm bg-green-500/5 border border-green-500/10 rounded-md p-3"
                      >
                        <div className="h-5 w-5 rounded-full bg-green-500/10 flex items-center justify-center shrink-0">
                          <div className="h-2 w-2 rounded-full bg-green-600" />
                        </div>
                        <span className="text-muted-foreground">
                          {criterion.description}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Requirements Not Met */}
              {selectedResult.unmetCriteria.length > 0 && (
                <div className="rounded-xl border p-4">
                  <h4 className="text-sm font-medium text-foreground mb-3">
                    Requirements Not Met
                  </h4>
                  <div className="space-y-2">
                    {selectedResult.unmetCriteria.map((criterion, index) => (
                      <div
                        key={index}
                        className="flex items-start gap-2 text-sm bg-yellow-500/5 border border-yellow-500/10 rounded-md p-3"
                      >
                        <div className="h-5 w-5 rounded-full bg-yellow-500/10 flex items-center justify-center shrink-0">
                          <div className="h-2 w-2 rounded-full bg-yellow-600" />
                        </div>
                        <div>
                          <span className="text-muted-foreground">
                            {criterion.description}
                          </span>
                          {criterion.hint && (
                            <div className="text-xs text-amber-600 mt-1">
                              {criterion.hint}
                            </div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Dates */}
              <div className="rounded-xl border p-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <div className="flex items-center gap-2 text-sm text-muted-foreground mb-1">
                      <Calendar className="h-4 w-4" />
                      Start Date
                    </div>
                    <div className="text-sm font-medium text-foreground">
                      {formatDate(selectedResult.startDate)}
                    </div>
                  </div>
                  <div>
                    <div className="flex items-center gap-2 text-sm text-muted-foreground mb-1">
                      <Calendar className="h-4 w-4" />
                      End Date
                    </div>
                    <div className="text-sm font-medium text-foreground">
                      {formatDate(selectedResult.endDate)}
                    </div>
                  </div>
                </div>
              </div>

              {/* Actions */}
              <div className="flex gap-3 pt-2">
                <Button
                  variant="outline"
                  className="flex-1"
                  onClick={handleCloseDrawer}
                >
                  Close
                </Button>
              </div>
            </div>
          </SheetContent>
        </Sheet>
      )}
    </div>
  );
}
