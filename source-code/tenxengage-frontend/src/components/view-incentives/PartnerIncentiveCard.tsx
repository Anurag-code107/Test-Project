import { useState } from "react";
import { PermissionGate } from "@/components/PermissionGate";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card";
import {
  Calendar,
  TrendingUp,
  CheckCircle2,
  CircleDashed,
  ChevronRight,
  ArrowLeft,
  Layers,
} from "lucide-react";
import type { IncentiveResponse, IncentiveType } from "@/types/incentive.types";
import { cn } from "@/lib/utils";
import {
  getCurrency,
  monetaryCurrencyIds,
  nonMonetaryCurrencyIds,
} from "@/config/currencies";
import { getCurrencyColors } from "@/components/view-incentives/incentive-detail-shared";
import { DocumentsPopover } from "./DocumentsPopover";
import {
  engagementIconMap,
  engagementColors,
  stageTypeColors,
  cardAccent,
  formatIncentiveDate,
  parseRewardMessage,
} from "@/components/incentive-card/incentive-card.config";

// --- Helpers to create icon elements from shared config ---

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

function StageTypeIcon({
  type,
  className,
}: {
  type: string;
  className?: string;
}) {
  const Icon = engagementIconMap[type as IncentiveType];
  return Icon ? <Icon className={className} /> : null;
}

// --- Action button config (partner-specific) ---

const actionLabels: Record<IncentiveType, string> = {
  SALES: "Claim",
  TRAINING: "Take Courses",
  ACTIVITY: "Take Action",
  JOURNEY: "View Journey",
};

// --- Component ---

interface PartnerIncentiveCardProps {
  incentive: IncentiveResponse;
  onClick?: () => void;
  onTakeCourses?: (incentive: IncentiveResponse) => void;
  onTakeAction?: (incentive: IncentiveResponse) => void;
  /**
   * When true, renders only the card *content* without the outer Card wrapper,
   * hover effects, left-border accent, completed ribbon, or journey stage sections.
   * Used to embed the card inside the Journey carousel center slot.
   */
  embedded?: boolean;
  /** Extra action elements rendered in the bottom action bar (e.g. edit button for manage variant) */
  extraActions?: React.ReactNode;
}

export function PartnerIncentiveCard({
  incentive,
  onClick,
  onTakeCourses,
  onTakeAction,
  embedded = false,
  extraActions,
}: PartnerIncentiveCardProps) {
  const [activeStageIndex, setActiveStageIndex] = useState<number | null>(null);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const isCompleted = incentive.userCompleted === true;

  const endDate = incentive.endDate ? new Date(incentive.endDate) : null;

  // Currency sorting using centralized config
  const rewardCurrencyIds = incentive.rewardCurrencies ?? [];
  const monetary = [...monetaryCurrencyIds].filter((id) =>
    rewardCurrencyIds.includes(id),
  );
  const nonMonetary = [...nonMonetaryCurrencyIds].filter((id) =>
    rewardCurrencyIds.includes(id),
  );
  const sortedCurrencyIds = [...monetary, ...nonMonetary];
  const sortedCurrencies = sortedCurrencyIds.map((id) => ({
    ...getCurrency(id),
    id,
  }));
  const primaryCurrencyId = sortedCurrencyIds[0] ?? "cash";
  const primaryColors = getCurrencyColors(primaryCurrencyId);

  // Budget warnings
  const utilization = incentive.budgetUtilizationPercent ?? 0;
  const budgetAlmostCapped = utilization >= 85 && !isCompleted;

  // Progress — for Training, only required courses count toward the total
  const hasProgress =
    (incentive.incentiveType === "TRAINING" ||
      incentive.incentiveType === "ACTIVITY") &&
    incentive.partnerProgressCompleted != null;
  const progressTotal =
    incentive.incentiveType === "TRAINING"
      ? (incentive.trainingRequiredCount ?? incentive.trainingCourseCount ?? 0)
      : (incentive.activityDefinitionCount ?? 0);
  const progressCompleted = incentive.partnerProgressCompleted ?? 0;
  const progressIsComplete =
    progressCompleted >= progressTotal && progressTotal > 0;
  const progressPercent =
    progressTotal > 0
      ? Math.min((progressCompleted / progressTotal) * 100, 100)
      : 0;

  // Journey stages
  const stages = incentive.journeyStages ?? [];
  const hasStages = incentive.incentiveType === "JOURNEY" && stages.length > 0;
  const activeStage =
    activeStageIndex != null ? stages[activeStageIndex] : null;

  const handleStageClick = (index: number, e: React.MouseEvent) => {
    e.stopPropagation();
    if (index === activeStageIndex) return;
    setIsTransitioning(true);
    setTimeout(() => {
      setActiveStageIndex(index);
      setIsTransitioning(false);
    }, 150);
  };

  const handleBackToOverview = (e: React.MouseEvent) => {
    e.stopPropagation();
    setIsTransitioning(true);
    setTimeout(() => {
      setActiveStageIndex(null);
      setIsTransitioning(false);
    }, 150);
  };

  const handleAction = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (incentive.incentiveType === "TRAINING" && onTakeCourses) {
      onTakeCourses(incentive);
      return;
    }
    if (incentive.incentiveType === "ACTIVITY" && onTakeAction) {
      onTakeAction(incentive);
      return;
    }
    onClick?.();
  };

  const rewardMessage = incentive.rewardMessage ?? "";
  const rewardAmountText = parseRewardMessage(rewardMessage);

  const accent = cardAccent[incentive.incentiveType];
  const action = actionLabels[incentive.incentiveType];

  /* ── Inner content (shared between standalone card and embedded mode) ── */
  const cardContent = (
    <div
      className={cn("relative flex flex-col h-full flex-1", embedded && "p-4")}
    >
      {/* Header band — coloured gradient strip with icon + title + badge */}
      <div
        className={cn(
          "relative z-[1] rounded-t-xl px-6 pt-4 pb-3",
          embedded && "-mx-4 -mt-4 px-4 pt-3 pb-2.5",
        )}
        style={{
          background: accent.bandGradient,
          borderBottom: `1px solid ${accent.bandBorder}`,
        }}
      >
        {incentive.incentiveType === "JOURNEY" ? (
          <div className="flex flex-col items-center text-center gap-1.5">
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
                  "h-4 w-4",
                  engagementColors[incentive.incentiveType],
                )}
              />
            </div>
            <h3 className="font-semibold text-base text-foreground leading-tight">
              {incentive.name}
            </h3>
            {!isCompleted && (
              <span className="text-xs px-2.5 py-0.5 rounded-full border bg-success/10 text-success border-success/20">
                Active
              </span>
            )}
          </div>
        ) : (
          <div className="flex items-start justify-between gap-3">
            <div className="flex items-center gap-2.5 flex-1 min-w-0">
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
                    "h-4 w-4",
                    engagementColors[incentive.incentiveType],
                  )}
                />
              </div>
              <h3
                className={cn(
                  "font-semibold text-foreground leading-tight truncate",
                  embedded ? "text-sm" : "text-base",
                )}
              >
                {incentive.name}
              </h3>
            </div>
            {!isCompleted && (
              <span className="shrink-0 text-xs px-2.5 py-0.5 rounded-full border bg-success/10 text-success border-success/20">
                Active
              </span>
            )}
          </div>
        )}
      </div>

      {/* Body content below header band */}
      <div
        className={cn(
          "flex flex-col flex-1",
          embedded ? "px-4 pt-3 pb-4" : "px-6 pt-4 pb-6",
        )}
      >
        {/* Row 2: Reward Banner */}
        {rewardMessage &&
          sortedCurrencies.length > 0 &&
          (() => {
            const extraCurrencies = sortedCurrencies.slice(1);
            const hasMultipleCurrencies = sortedCurrencies.length > 1;

            const bannerContent = (
              <div
                className={cn(
                  "rounded-xl border transition-opacity",
                  embedded ? "mb-3 p-3" : "mb-4 p-3.5",
                  hasMultipleCurrencies && "cursor-help hover:opacity-90",
                  primaryColors.bg,
                  primaryColors.border,
                )}
                onClick={(e) => {
                  if (hasMultipleCurrencies) e.stopPropagation();
                }}
              >
                <div
                  className={cn(
                    "flex items-center justify-center",
                    embedded ? "gap-2" : "gap-3",
                  )}
                >
                  <div
                    className={cn(
                      "flex items-center justify-center rounded-lg shrink-0",
                      embedded ? "h-8 w-8" : "h-10 w-10",
                      primaryColors.iconBg,
                    )}
                  >
                    <span className={primaryColors.text}>
                      {(() => {
                        const Icon = getCurrency(primaryCurrencyId).icon;
                        return (
                          <Icon className={embedded ? "h-4 w-4" : "h-5 w-5"} />
                        );
                      })()}
                    </span>
                  </div>
                  <div className="flex flex-col items-start">
                    <span
                      className={cn(
                        "font-semibold uppercase tracking-wide shrink-0",
                        embedded ? "text-[10px]" : "text-xs",
                        primaryColors.text,
                      )}
                    >
                      {activeStage
                        ? `Stage ${activeStageIndex! + 1} — earn up to`
                        : "Earn up to"}
                    </span>
                    <span
                      className={cn(
                        "font-semibold text-foreground leading-tight",
                        embedded ? "text-lg" : "text-2xl",
                      )}
                    >
                      {rewardAmountText}
                    </span>
                  </div>
                  {extraCurrencies.length > 0 && (
                    <div className="flex items-center gap-1.5 shrink-0">
                      {extraCurrencies.map((c) => {
                        const colors = getCurrencyColors(c.id);
                        const Icon = getCurrency(c.id).icon;
                        return (
                          <div
                            key={c.id}
                            className={cn(
                              "flex items-center justify-center h-7 w-7 rounded-full",
                              colors.iconBg,
                            )}
                          >
                            <span className={colors.text}>
                              <Icon className="h-3.5 w-3.5" />
                            </span>
                          </div>
                        );
                      })}
                      <span className="text-xs text-muted-foreground">
                        +{extraCurrencies.length}
                      </span>
                    </div>
                  )}
                </div>
              </div>
            );

            if (!hasMultipleCurrencies) return bannerContent;

            return (
              <HoverCard openDelay={200} closeDelay={100}>
                <HoverCardTrigger asChild>{bannerContent}</HoverCardTrigger>
                <HoverCardContent
                  className="w-52 p-3"
                  side="top"
                  align="center"
                  sideOffset={8}
                  collisionPadding={16}
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="space-y-2">
                    <span className="text-xs font-semibold text-foreground">
                      Reward Currencies
                    </span>
                    <div className="space-y-1.5">
                      {sortedCurrencies.map((c) => {
                        const colors = getCurrencyColors(c.id);
                        const Icon = getCurrency(c.id).icon;
                        return (
                          <div
                            key={c.id}
                            className="flex items-center gap-2 text-sm"
                          >
                            <div
                              className={cn(
                                "flex items-center justify-center h-6 w-6 rounded-full",
                                colors.iconBg,
                              )}
                            >
                              <span className={colors.text}>
                                <Icon className="h-3.5 w-3.5" />
                              </span>
                            </div>
                            <span className="text-muted-foreground">
                              {c.label}
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </HoverCardContent>
              </HoverCard>
            );
          })()}

        {/* Row 3: Progress bar (Training/Activity only) */}
        {hasProgress && (
          <div
            className={cn(
              "rounded-lg border",
              embedded ? "mb-3 p-2.5" : "mb-4 p-3",
              progressIsComplete
                ? "bg-success/5 border-success/30"
                : "bg-muted/30 border-border",
            )}
          >
            {progressIsComplete ? (
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="h-4 w-4 text-success shrink-0" />
                  <span className="text-sm font-semibold text-success">
                    Completed
                  </span>
                  <span className="text-xs text-success/70 ml-auto">
                    {progressTotal}/{progressTotal}{" "}
                    {incentive.partnerProgressLabel}
                  </span>
                </div>
                <Progress value={100} className="h-1.5" />
              </div>
            ) : (
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <CircleDashed className="h-4 w-4 text-muted-foreground shrink-0" />
                    <span className="text-sm font-medium text-foreground">
                      In Progress
                    </span>
                  </div>
                  <span className="text-xs font-semibold text-foreground">
                    {progressCompleted}/{progressTotal}{" "}
                    {incentive.partnerProgressLabel}
                  </span>
                </div>
                <Progress value={progressPercent} className="h-1.5" />
              </div>
            )}
          </div>
        )}

        {/* Row 4: Journey Progress (Journey type only — hidden in embedded mode) */}
        {hasStages && !embedded && (
          <div className="mb-4 p-3 rounded-lg bg-muted/30 border">
            {/* Header */}
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                <Layers className="h-3 w-3" />
                <span>Journey Progress</span>
              </div>
              {activeStageIndex != null && (
                <button
                  onClick={handleBackToOverview}
                  className="flex items-center gap-1 text-xs text-primary hover:text-primary/80 transition-colors font-medium"
                >
                  <ArrowLeft className="h-3 w-3" />
                  Overview
                </button>
              )}
            </div>

            {/* Stage buttons */}
            <div className="overflow-x-auto -mx-1 px-1 py-1">
              <div className="flex items-center gap-2">
                {stages.map((stage, index) => (
                  <div key={index} className="flex items-center shrink-0">
                    <button
                      onClick={(e) => handleStageClick(index, e)}
                      className={cn(
                        "flex items-center justify-center gap-2 px-6 py-1.5 rounded-md border text-xs font-medium whitespace-nowrap transition-[transform,opacity,box-shadow] min-w-[72px] relative",
                        stage.incentiveType &&
                          stageTypeColors[stage.incentiveType]
                          ? stageTypeColors[stage.incentiveType]
                          : "bg-muted text-muted-foreground border-border",
                        activeStageIndex === index
                          ? "ring-2 ring-offset-2 ring-indigo-500"
                          : "hover:scale-105 cursor-pointer",
                        activeStageIndex != null &&
                          activeStageIndex !== index &&
                          "opacity-50",
                      )}
                    >
                      {stage.incentiveType && (
                        <StageTypeIcon
                          type={stage.incentiveType}
                          className="h-3 w-3"
                        />
                      )}
                      <span>{index + 1}</span>
                    </button>
                    {index < stages.length - 1 && (
                      <ChevronRight className="h-3 w-3 mx-1 shrink-0 text-muted-foreground" />
                    )}
                  </div>
                ))}
              </div>
            </div>

            {/* Current view indicator */}
            <div className="mt-2 pt-2 border-t border-border/50">
              <span className="text-xs text-muted-foreground">
                Viewing:{" "}
                <span className="font-medium text-foreground">
                  {activeStage
                    ? `Stage ${activeStageIndex! + 1} - ${activeStage.incentiveName}`
                    : "Program Overview"}
                </span>
              </span>
            </div>

            {/* Date — shown below journey progress instead of footer */}
            {endDate && (
              <div className="flex items-center justify-center gap-2 text-sm mt-3 pt-2 border-t border-border/50">
                <Calendar className="h-4 w-4 text-muted-foreground" />
                <span className="text-muted-foreground">Ends</span>
                <span className="font-semibold text-foreground">
                  {formatIncentiveDate(endDate)}
                </span>
              </div>
            )}
          </div>
        )}

        {/* Row 5: Content area with transition */}
        <div
          className={cn(
            "transition-[opacity,transform] duration-150",
            isTransitioning
              ? "opacity-0 translate-y-2"
              : "opacity-100 translate-y-0",
          )}
        >
          {activeStage ? (
            !embedded && (
              <div className="space-y-2 mb-4">
                <div className="flex items-center gap-2">
                  {activeStage.incentiveType && (
                    <span
                      className={cn(
                        "shrink-0",
                        stageTypeColors[activeStage.incentiveType]?.split(
                          " ",
                        )[1],
                      )}
                    >
                      <StageTypeIcon
                        type={activeStage.incentiveType}
                        className="h-3 w-3"
                      />
                    </span>
                  )}
                  <h4 className="font-semibold text-sm text-foreground">
                    {activeStage.incentiveName}
                  </h4>
                </div>
                {activeStage.incentiveType && (
                  <Badge
                    variant="outline"
                    className={cn(
                      "text-xs",
                      stageTypeColors[activeStage.incentiveType] ??
                        "bg-muted text-muted-foreground border-border",
                    )}
                  >
                    {activeStage.incentiveType.charAt(0) +
                      activeStage.incentiveType.slice(1).toLowerCase()}{" "}
                    Incentive
                  </Badge>
                )}
              </div>
            )
          ) : (
            <p
              className={cn(
                "text-muted-foreground",
                embedded
                  ? "text-xs line-clamp-1 mb-3"
                  : "text-sm line-clamp-2 mb-4",
                incentive.incentiveType === "JOURNEY" && "text-center",
              )}
            >
              {incentive.description}
            </p>
          )}
        </div>

        {/* Row 6: Budget warning (hidden in embedded mode) */}
        {budgetAlmostCapped && !embedded && (
          <div className="mb-4 h-6 flex items-center">
            <div className="flex items-center gap-1.5 text-xs text-warning">
              <TrendingUp className="h-3.5 w-3.5 shrink-0" />
              <span className="font-medium">
                {utilization >= 95
                  ? "Budget almost capped — act now!"
                  : "Budget filling up — act soon"}
              </span>
            </div>
          </div>
        )}

        {/* Row 7: Footer */}
        <div className={cn("mt-auto border-t", embedded ? "pt-3" : "pt-4")}>
          <div className="flex items-center justify-between">
            <div>
              {endDate && incentive.incentiveType !== "JOURNEY" && (
                <div
                  className={cn(
                    "flex items-center gap-2",
                    embedded ? "text-xs" : "text-sm",
                  )}
                >
                  <Calendar
                    className={cn(
                      embedded ? "h-3.5 w-3.5" : "h-4 w-4",
                      "text-muted-foreground",
                    )}
                  />
                  <span className="text-muted-foreground">Ends</span>
                  <span className="font-semibold text-foreground">
                    {formatIncentiveDate(endDate)}
                  </span>
                </div>
              )}
            </div>
            <div className="flex items-center gap-1">
              {incentive.documents && incentive.documents.length > 0 && (
                <DocumentsPopover
                  documents={incentive.documents}
                  incentiveId={incentive.id}
                />
              )}
              {extraActions}
              {!isCompleted && (
                <PermissionGate permission="action.claim.submit">
                  <Button
                    variant="default"
                    size="sm"
                    className="shrink-0"
                    onClick={handleAction}
                    data-tour="incentive-claim-button"
                  >
                    <TypeIcon
                      type={incentive.incentiveType}
                      className="h-3.5 w-3.5 mr-1.5"
                    />
                    {action}
                  </Button>
                </PermissionGate>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );

  /* ── Embedded mode: return bare content ── */
  if (embedded) return cardContent;

  /* ── Standalone mode: wrap in Card with Option B header band style ── */
  return (
    <Card
      className={cn(
        "group/card relative overflow-hidden cursor-pointer h-full flex flex-col transition-[box-shadow,border-color] duration-200",
        accent.hoverBorder,
        accent.hoverShadow,
        isCompleted && "opacity-75",
      )}
      onClick={onClick}
      data-tour="incentive-card"
    >
      {/* Hover flood — colour bleeds down from top on hover */}
      <div
        className="pointer-events-none absolute top-0 left-0 right-0 h-0 group-hover/card:h-full rounded-xl z-0"
        style={{
          background: accent.hoverWash,
          transition: "height 0.7s cubic-bezier(0.22, 0.61, 0.36, 1)",
        }}
      />

      {/* Completed ribbon */}
      {isCompleted && (
        <div className="absolute top-0 right-0 z-10 overflow-hidden w-32 h-32 pointer-events-none">
          <div className="absolute top-[18px] right-[-34px] w-[150px] text-center rotate-45 bg-green-500 text-white text-xs font-semibold uppercase tracking-wider py-1.5 shadow-sm">
            Completed
          </div>
        </div>
      )}

      <CardContent className="relative flex-1 !p-0">{cardContent}</CardContent>
    </Card>
  );
}
