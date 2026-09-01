import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Sparkles,
  GraduationCap,
  Target,
  Clock,
  ArrowRight,
  Loader2,
} from "lucide-react";
import { ScrollableCardRow } from "@/components/ScrollableCardRow";
import {
  useTrainingRecommendations,
  useIncentiveRecommendations,
} from "@/hooks/useRecommendationApi";
import { TrainingDetailDrawer } from "./TrainingDetailDrawer";
import { IncentiveRecommendationDrawer } from "./IncentiveRecommendationDrawer";
import { getCurrency } from "@/config/currencies";
import type {
  TrainingRecommendationResponse,
  IncentiveRecommendationResponse,
} from "@/types/recommendation.types";

function formatDaysLeft(days: number): string {
  if (days < 0) return "Expired";
  if (days === 0) return "Last day";
  if (days === 1) return "1 day left";
  return `${days} days left`;
}

function formatDaysFromEndDate(endDate: string | null): string {
  if (!endDate) return "";
  const days = Math.ceil(
    (new Date(endDate).getTime() - Date.now()) / (1000 * 60 * 60 * 24),
  );
  return formatDaysLeft(days);
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

export function TenXSuggestionsSection() {
  const trainingQuery = useTrainingRecommendations();
  const incentiveQuery = useIncentiveRecommendations();

  const [selectedTraining, setSelectedTraining] =
    useState<TrainingRecommendationResponse | null>(null);
  const [trainingDrawerOpen, setTrainingDrawerOpen] = useState(false);
  const [selectedIncentive, setSelectedIncentive] =
    useState<IncentiveRecommendationResponse | null>(null);
  const [incentiveDrawerOpen, setIncentiveDrawerOpen] = useState(false);

  const trainingRecs = trainingQuery.data ?? [];
  const incentiveRecs = incentiveQuery.data ?? [];
  const isLoading = trainingQuery.isLoading || incentiveQuery.isLoading;

  const hasTraining = trainingRecs.length > 0;
  const hasIncentives = incentiveRecs.length > 0;

  const hasError = trainingQuery.isError || incentiveQuery.isError;

  return (
    <>
      <Card className="border" data-tour="suggestions-section">
        <CardHeader className="pb-4">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-lg bg-gradient-to-br from-amber-500 to-orange-500 shadow-md">
              <Sparkles className="h-5 w-5 text-white" />
            </div>
            <div className="space-y-1">
              <CardTitle className="text-xl font-semibold text-foreground">
                tenX Suggestions
              </CardTitle>
              <p className="text-sm text-muted-foreground">
                Personalized Recommendations Based On Your Performance
              </p>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-8">
          {isLoading && (
            <div className="flex items-center justify-center py-8 gap-2 text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin" />
              <span className="text-sm">Loading recommendations...</span>
            </div>
          )}

          {!isLoading && !hasTraining && !hasIncentives && !hasError && (
            <div className="flex flex-col items-center justify-center py-10 text-center">
              <div className="p-3 rounded-full bg-muted mb-3">
                <Sparkles className="h-6 w-6 text-muted-foreground" />
              </div>
              <h5 className="text-sm font-semibold text-foreground mb-1">
                Recommendations Coming Soon
              </h5>
              <p className="text-sm text-muted-foreground max-w-md">
                We're analyzing your performance data to find the best training
                courses and incentives for you. Check back shortly.
              </p>
            </div>
          )}

          {!isLoading && hasError && (
            <div className="flex flex-col items-center justify-center py-10 text-center">
              <p className="text-sm text-muted-foreground">
                Unable to load recommendations right now. Please try again
                later.
              </p>
            </div>
          )}

          {/* Recommended Training */}
          {!isLoading && hasTraining && (
            <ScrollableCardRow
              icon={<GraduationCap className="h-4 w-4 text-primary" />}
              title="Recommended Training"
            >
              {trainingRecs.map((rec) => {
                const reward = formatReward(
                  rec.rewardAmount,
                  rec.rewardCurrencyId,
                );
                return (
                  <Card
                    key={rec.courseId}
                    className="min-w-[260px] w-[calc(33%-8px)] flex-shrink-0 transition-[box-shadow,border-color] hover:shadow-md hover:border-primary/30 cursor-pointer group h-[220px]"
                    onClick={() => {
                      setSelectedTraining(rec);
                      setTrainingDrawerOpen(true);
                    }}
                  >
                    <CardContent className="p-5 flex flex-col h-full">
                      <div className="flex items-start justify-between mb-3">
                        <div className="p-2 rounded-lg bg-primary/10">
                          <GraduationCap className="h-5 w-5 text-primary" />
                        </div>
                        <div className="flex items-center gap-1.5 text-sm text-muted-foreground">
                          <Clock className="h-3.5 w-3.5" />
                          <span>{formatDaysLeft(rec.daysUntilQuarterEnd)}</span>
                        </div>
                      </div>
                      <h5 className="text-base font-semibold text-foreground mb-2 line-clamp-1">
                        {rec.courseName}
                      </h5>
                      <p className="text-sm text-muted-foreground flex-1 line-clamp-3">
                        {rec.courseDescription}
                      </p>
                      <div className="flex items-center justify-between pt-3 border-t border-border mt-auto">
                        <span className="text-sm font-semibold text-success">
                          {reward || "Complete to earn"}
                        </span>
                        <ArrowRight className="h-4 w-4 text-primary opacity-0 group-hover:opacity-100 transition-opacity" />
                      </div>
                    </CardContent>
                  </Card>
                );
              })}
            </ScrollableCardRow>
          )}

          {/* Incentives For You */}
          {!isLoading && hasIncentives && (
            <ScrollableCardRow
              icon={<Target className="h-4 w-4 text-primary" />}
              title="Incentives For You"
            >
              {incentiveRecs.map((rec) => {
                const reward = formatReward(
                  rec.rewardAmount,
                  rec.rewardCurrency,
                );
                return (
                  <Card
                    key={rec.incentiveId}
                    className="min-w-[260px] w-[calc(33%-8px)] flex-shrink-0 transition-[box-shadow,border-color] hover:shadow-md hover:border-primary/30 cursor-pointer group h-[220px]"
                    onClick={() => {
                      setSelectedIncentive(rec);
                      setIncentiveDrawerOpen(true);
                    }}
                  >
                    <CardContent className="p-5 flex flex-col h-full">
                      <div className="flex items-start justify-between mb-3">
                        <div className="p-2 rounded-lg bg-primary/10">
                          <Target className="h-5 w-5 text-primary" />
                        </div>
                        <div className="flex items-center gap-1.5 text-sm text-muted-foreground">
                          <Clock className="h-3.5 w-3.5" />
                          <span>{formatDaysFromEndDate(rec.endDate)}</span>
                        </div>
                      </div>
                      <h5 className="text-base font-semibold text-foreground mb-2 line-clamp-1">
                        {rec.incentiveName}
                      </h5>
                      <p className="text-sm text-muted-foreground flex-1 line-clamp-3">
                        {rec.description}
                      </p>
                      <div className="flex items-center justify-between pt-3 border-t border-border mt-auto">
                        <span className="text-sm font-semibold text-success">
                          {reward || "Participate to earn"}
                        </span>
                        <ArrowRight className="h-4 w-4 text-primary opacity-0 group-hover:opacity-100 transition-opacity" />
                      </div>
                    </CardContent>
                  </Card>
                );
              })}
            </ScrollableCardRow>
          )}
        </CardContent>
      </Card>

      {/* Detail Drawers */}
      <TrainingDetailDrawer
        open={trainingDrawerOpen}
        onOpenChange={setTrainingDrawerOpen}
        recommendation={selectedTraining}
      />
      <IncentiveRecommendationDrawer
        open={incentiveDrawerOpen}
        onOpenChange={setIncentiveDrawerOpen}
        recommendation={selectedIncentive}
      />
    </>
  );
}
