import { useState } from "react";
import {
  Lock,
  ChevronLeft,
  ChevronRight,
  Calendar,
  Layers,
  CheckCircle2,
  Pencil,
  LayoutGrid,
  GalleryHorizontalEnd,
  AlertCircle,
} from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import { cn } from "@/lib/utils";
import {
  cardAccent,
  getAccentForType,
  engagementColors,
  engagementIconMap,
  formatIncentiveDate,
} from "@/components/incentive-card/incentive-card.config";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PartnerIncentiveCard } from "@/components/view-incentives/PartnerIncentiveCard";
import { ManagedJourneyStageCard } from "@/components/journey/ManagedJourneyStageCard";
import { PermissionGate } from "@/components/PermissionGate";
import type {
  IncentiveResponse,
  JourneyStageSummary,
} from "@/types/incentive.types";

/* ── Stage unavailable placeholder ──────────────────────────────────────
 * Rendered when the full IncentiveResponse for a Journey stage can't be
 * found in allIncentives (e.g. data gap, race, or filter drift). Never
 * falls back to the parent Journey — that silently repaints the parent
 * in every stage slot (see BUG-019).
 */
function StageUnavailablePlaceholder({
  stage,
}: {
  stage: JourneyStageSummary;
}) {
  const Icon = engagementIconMap[stage.incentiveType];
  return (
    <div
      data-testid="stage-unavailable-placeholder"
      className="flex flex-col items-center justify-center gap-3 px-5 py-8 text-center min-h-[220px]"
    >
      <div className="flex items-center justify-center h-10 w-10 rounded-lg bg-muted">
        {Icon ? (
          <Icon className="h-5 w-5 text-muted-foreground" />
        ) : (
          <AlertCircle className="h-5 w-5 text-muted-foreground" />
        )}
      </div>
      <div className="space-y-1">
        <h4 className="font-semibold text-sm text-foreground line-clamp-2">
          {stage.incentiveName}
        </h4>
        <p className="text-xs text-muted-foreground">
          Stage details unavailable
        </p>
      </div>
    </div>
  );
}

/* ── Step indicator ─────────────────────────────────────────────────────── */

const MAX_VISIBLE_STEPS = 4;

function StepIndicator({
  stages,
  viewingIndex,
  progressionIndex,
  onStepClick,
  variant = "view",
}: {
  stages: JourneyStageSummary[];
  /** Which step the carousel is currently showing */
  viewingIndex: number;
  /** The first incomplete step (actual progression state) */
  progressionIndex: number;
  onStepClick: (index: number) => void;
  variant?: "manage" | "view";
}) {
  const isManage = variant === "manage";
  // Sliding window: show at most MAX_VISIBLE_STEPS, keeping viewingIndex in view
  const total = stages.length;
  const maxVis = Math.min(MAX_VISIBLE_STEPS, total);
  const windowStart = Math.max(0, Math.min(viewingIndex - 1, total - maxVis));
  const windowEnd = windowStart + maxVis;
  const visibleStages = stages.slice(windowStart, windowEnd);
  const needsWindow = total > MAX_VISIBLE_STEPS;

  return (
    <div className="flex items-center gap-1">
      {/* Count pill + dash showing hidden steps before the window */}
      {needsWindow && windowStart > 0 && (
        <div className="flex items-center">
          <button
            onClick={(e) => {
              e.stopPropagation();
              onStepClick(windowStart - 1);
            }}
            className="flex items-center justify-center h-6 min-w-[1.5rem] px-1.5 rounded-full text-[10px] font-semibold bg-muted text-muted-foreground hover:bg-muted/80 cursor-pointer transition-colors shrink-0"
            title={`${windowStart} more stage${windowStart > 1 ? "s" : ""} before`}
          >
            +{windowStart}
          </button>
          <div className="flex items-center gap-1 mx-2">
            <div className="h-0.5 w-1.5 rounded-full bg-border" />
            <div className="h-0.5 w-1.5 rounded-full bg-border" />
            <div className="h-0.5 w-1.5 rounded-full bg-border" />
          </div>
        </div>
      )}

      <AnimatePresence mode="popLayout" initial={false}>
        {visibleStages.map((stage, vi) => {
          const i = windowStart + vi; // real index
          const isCompleted = !isManage && stage.userCompleted === true;
          const isCurrent = isManage
            ? i === viewingIndex
            : i === progressionIndex;
          const isLocked = !isManage && !isCompleted && i > progressionIndex;
          const isViewing = i === viewingIndex;
          const isLastInWindow = vi === visibleStages.length - 1;

          return (
            <motion.div
              key={i}
              className="flex items-center"
              layout
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.8 }}
              transition={{ duration: 0.2 }}
            >
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onStepClick(i);
                }}
                className={cn(
                  "relative flex items-center justify-center h-8 w-8 rounded-full text-xs font-semibold transition-all duration-200 shrink-0",
                  isManage
                    ? isViewing
                      ? "bg-primary text-primary-foreground shadow-md shadow-primary/30"
                      : "bg-muted text-muted-foreground hover:bg-muted/80"
                    : isCompleted
                      ? "bg-success text-primary-foreground"
                      : isCurrent
                        ? "bg-primary text-primary-foreground shadow-md shadow-primary/30"
                        : isLocked
                          ? "bg-muted text-muted-foreground"
                          : "bg-muted text-muted-foreground hover:bg-muted/80",
                  "cursor-pointer",
                  isViewing && "ring-2 ring-primary ring-offset-2",
                )}
              >
                {isManage ? (
                  i + 1
                ) : isCompleted ? (
                  <CheckCircle2 className="h-4 w-4" />
                ) : isLocked ? (
                  <Lock className="h-3 w-3" />
                ) : (
                  i + 1
                )}
              </button>
              {!isLastInWindow && (
                <div
                  className={cn(
                    "h-0.5 w-8 md:w-12 mx-2 transition-colors duration-200",
                    isManage
                      ? "bg-border"
                      : isCompleted
                        ? "bg-success"
                        : "bg-border",
                  )}
                />
              )}
            </motion.div>
          );
        })}
      </AnimatePresence>

      {/* Dash + count pill showing hidden steps after the window */}
      {needsWindow && windowEnd < total && (
        <div className="flex items-center">
          <div className="flex items-center gap-1 mx-2">
            <div className="h-0.5 w-1.5 rounded-full bg-border" />
            <div className="h-0.5 w-1.5 rounded-full bg-border" />
            <div className="h-0.5 w-1.5 rounded-full bg-border" />
          </div>
          <button
            onClick={(e) => {
              e.stopPropagation();
              onStepClick(windowEnd);
            }}
            className="flex items-center justify-center h-6 min-w-[1.5rem] px-1.5 rounded-full text-[10px] font-semibold bg-muted text-muted-foreground hover:bg-muted/80 cursor-pointer transition-colors shrink-0"
            title={`${total - windowEnd} more stage${total - windowEnd > 1 ? "s" : ""} ahead`}
          >
            +{total - windowEnd}
          </button>
        </div>
      )}
    </div>
  );
}

/* ── Main Journey Incentive Card ────────────────────────────────────────── */

interface JourneyIncentiveCardProps {
  incentive: IncentiveResponse;
  variant: "manage" | "view";
  onClick?: () => void;
  onAction?: () => void;
  /** Called when the user clicks on a stage's center card to view its details */
  onStageClick?: (childIncentive: IncentiveResponse, isLocked: boolean) => void;
  /** Called when the user clicks the edit button on a stage card (manage variant) */
  onStageEdit?: (childIncentive: IncentiveResponse) => void;
  /** Manage-specific slots rendered in the top-right of the header */
  headerActions?: React.ReactNode;
  /** Full list of incentives so we can look up child stage data (counts, rewards, etc.) */
  allIncentives?: IncentiveResponse[];
}

export function JourneyIncentiveCard({
  incentive,
  variant,
  onClick,
  onAction,
  onStageClick,
  onStageEdit,
  headerActions,
  allIncentives = [],
}: JourneyIncentiveCardProps) {
  const isManage = variant === "manage";
  const stages = [...(incentive.journeyStages ?? [])].sort(
    (a, b) => a.sortOrder - b.sortOrder,
  );

  // Determine which stage is "active" — first incomplete stage, or last
  // In manage mode, default to first stage (no progression concept)
  const firstIncompleteIndex = stages.findIndex(
    (s) => s.userCompleted !== true,
  );
  const defaultActiveIndex = isManage
    ? 0
    : firstIncompleteIndex >= 0
      ? firstIncompleteIndex
      : stages.length - 1;
  const [topIndex, setTopIndex] = useState(defaultActiveIndex);
  const [descExpanded, setDescExpanded] = useState(false);
  const [layout, setLayout] = useState<"carousel" | "grid">("carousel");

  const endDate = incentive.endDate ? new Date(incentive.endDate) : null;

  if (stages.length === 0) return null;

  // Status is based on actual progression, NOT the carousel's current view position
  // In manage mode, all stages are treated as "active" (no locked/completed concept)
  const getStageStatus = (stage: JourneyStageSummary, index: number) => {
    if (isManage) return "active" as const;
    if (stage.userCompleted === true) return "completed" as const;
    if (index === firstIncompleteIndex) return "active" as const;
    return "locked" as const;
  };

  const journeyAccent = cardAccent.JOURNEY;

  return (
    <Card className="relative rounded-xl overflow-hidden">
      {/* Header band — coloured gradient strip */}
      <div
        className={cn(
          "relative z-[1] rounded-t-xl px-6 pt-5 pb-3",
          onClick && "cursor-pointer",
        )}
        onClick={onClick}
        style={{
          background: journeyAccent.bandGradient,
          borderBottom: `1px solid ${journeyAccent.bandBorder}`,
        }}
      >
        <div className="flex flex-col items-center text-center gap-2">
          {/* Title + description — centered */}
          <div className="flex items-center justify-center gap-2.5">
            <div
              className={cn(
                "flex items-center justify-center h-7 w-7 rounded-lg shrink-0",
                engagementColors.JOURNEY.replace("text-", "bg-") + "/10",
              )}
            >
              <Layers className={cn("h-4 w-4", engagementColors.JOURNEY)} />
            </div>
            <h3 className="font-semibold text-base text-foreground leading-tight">
              {incentive.name}
            </h3>
          </div>
          {incentive.description && (
            <div className="max-w-lg">
              <p
                className={cn(
                  "text-sm text-muted-foreground",
                  !descExpanded && "line-clamp-1",
                )}
              >
                {incentive.description}
              </p>
              <div className="flex items-center justify-center gap-2 mt-0.5">
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    setDescExpanded(!descExpanded);
                  }}
                  className="text-xs font-medium text-primary hover:text-primary/80 transition-colors"
                >
                  {descExpanded ? "See less" : "See more"}
                </button>
                {/* Layout toggle — below description for view variant */}
                {!isManage && (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="shrink-0 h-6 w-6 p-0"
                    onClick={(e) => {
                      e.stopPropagation();
                      setLayout((prev) =>
                        prev === "carousel" ? "grid" : "carousel",
                      );
                    }}
                    title={
                      layout === "carousel"
                        ? "Switch to grid view"
                        : "Switch to carousel view"
                    }
                  >
                    {layout === "carousel" ? (
                      <LayoutGrid className="h-3.5 w-3.5" />
                    ) : (
                      <GalleryHorizontalEnd className="h-3.5 w-3.5" />
                    )}
                  </Button>
                )}
              </div>
            </div>
          )}
          {/* No description fallback — layout toggle for view variant */}
          {!incentive.description && !isManage && (
            <Button
              variant="ghost"
              size="sm"
              className="shrink-0 h-6 w-6 p-0"
              onClick={(e) => {
                e.stopPropagation();
                setLayout((prev) =>
                  prev === "carousel" ? "grid" : "carousel",
                );
              }}
              title={
                layout === "carousel"
                  ? "Switch to grid view"
                  : "Switch to carousel view"
              }
            >
              {layout === "carousel" ? (
                <LayoutGrid className="h-3.5 w-3.5" />
              ) : (
                <GalleryHorizontalEnd className="h-3.5 w-3.5" />
              )}
            </Button>
          )}
          {headerActions && (
            <div className="flex items-center gap-3">
              {headerActions}
              {/* Layout toggle — next to edit icon for manage variant */}
              {isManage && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="shrink-0 h-7 w-7 p-0"
                  onClick={(e) => {
                    e.stopPropagation();
                    setLayout((prev) =>
                      prev === "carousel" ? "grid" : "carousel",
                    );
                  }}
                  title={
                    layout === "carousel"
                      ? "Switch to grid view"
                      : "Switch to carousel view"
                  }
                >
                  {layout === "carousel" ? (
                    <LayoutGrid className="h-3.5 w-3.5" />
                  ) : (
                    <GalleryHorizontalEnd className="h-3.5 w-3.5" />
                  )}
                </Button>
              )}
            </div>
          )}
          {/* Manage variant without headerActions — standalone layout toggle */}
          {!headerActions && isManage && (
            <Button
              variant="ghost"
              size="sm"
              className="shrink-0 h-7 w-7 p-0"
              onClick={(e) => {
                e.stopPropagation();
                setLayout((prev) =>
                  prev === "carousel" ? "grid" : "carousel",
                );
              }}
              title={
                layout === "carousel"
                  ? "Switch to grid view"
                  : "Switch to carousel view"
              }
            >
              {layout === "carousel" ? (
                <LayoutGrid className="h-3.5 w-3.5" />
              ) : (
                <GalleryHorizontalEnd className="h-3.5 w-3.5" />
              )}
            </Button>
          )}
        </div>
      </div>

      {/* Body content below header band */}
      <div className="relative z-[1] px-6 pt-3">
        {/* Horizontal stepper — hidden in grid mode */}
        <AnimatePresence mode="wait">
          {layout === "carousel" && (
            <motion.div
              key="stepper"
              className="flex items-center justify-center py-2"
              onClick={(e) => e.stopPropagation()}
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: "auto" }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.2 }}
            >
              <StepIndicator
                stages={stages}
                viewingIndex={topIndex}
                progressionIndex={
                  firstIncompleteIndex >= 0
                    ? firstIncompleteIndex
                    : stages.length - 1
                }
                onStepClick={(i) => setTopIndex(i)}
                variant={variant}
              />
            </motion.div>
          )}
        </AnimatePresence>

        {/* Date — below stepper */}
        {endDate && (
          <div className="flex items-center justify-center gap-1.5 pb-2 text-xs text-muted-foreground">
            <Calendar className="h-3.5 w-3.5" />
            <span>Ends {formatIncentiveDate(endDate)}</span>
          </div>
        )}
      </div>

      {/* ── Layout switch: Carousel or Grid ─────────────────────── */}
      <AnimatePresence mode="wait" initial={false}>
        {layout === "carousel" ? (
          <motion.div
            key="carousel"
            initial={{ opacity: 0, scale: 0.97 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.97 }}
            transition={{ duration: 0.25, ease: "easeInOut" }}
          >
            {/* ── Stacked card carousel with side arrows ─────────────────── */}
            <div
              className="relative z-[1] px-5 pb-5 pt-2"
              onClick={(e) => e.stopPropagation()}
            >
              {/* Wrapper: arrow | cards | arrow */}
              <div className="flex items-center gap-2">
                {/* Left arrow */}
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    setTopIndex((prev) => Math.max(0, prev - 1));
                  }}
                  disabled={topIndex === 0}
                  className="shrink-0 flex items-center justify-center w-8 h-8 rounded-full bg-background border border-border shadow-sm hover:shadow-md text-foreground transition-shadow disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer z-10"
                >
                  <ChevronLeft className="h-4 w-4" />
                </button>

                {/* Carousel container — CSS Grid: all cards in one cell, tallest card drives height naturally */}
                <div
                  className="flex-1 min-w-0"
                  style={{
                    display: "grid",
                    gridTemplateColumns: "1fr",
                    gridTemplateRows: "1fr",
                    perspective: 800,
                  }}
                >
                  {stages.map((stage, i) => {
                    const childInc = allIncentives.find(
                      (inc) =>
                        inc.name === stage.incentiveName &&
                        inc.incentiveType === stage.incentiveType,
                    );
                    const status = getStageStatus(stage, i);
                    const accent = getAccentForType(stage.incentiveType);

                    const pos = i - topIndex;
                    const absPos = Math.abs(pos);
                    const isActive = pos === 0;

                    if (absPos > 3) return null;

                    /*
                      Card width = 46% of the carousel track.
                      Centre: left = 27% → centred with breathing room for arrows
                      ±1 shift by 17%, ±2 shift by 29%, ±3 shift by 38% (ghost)
                    */
                    const centreLeft = 27;
                    let leftPct = centreLeft;
                    let scale = 1;
                    let zIndex = 5;
                    let opacity = 1;
                    let blur = 0;

                    if (absPos === 1) {
                      leftPct = pos < 0 ? centreLeft - 17 : centreLeft + 17;
                      scale = 0.92;
                      zIndex = 4;
                      opacity = 0.7;
                      blur = 1;
                    } else if (absPos === 2) {
                      leftPct = pos < 0 ? centreLeft - 29 : centreLeft + 29;
                      scale = 0.84;
                      zIndex = 3;
                      opacity = 0.25;
                      blur = 4;
                    } else if (absPos === 3) {
                      leftPct = pos < 0 ? centreLeft - 38 : centreLeft + 38;
                      scale = 0.76;
                      zIndex = 2;
                      opacity = 0.06;
                      blur = 6;
                    }

                    return (
                      <motion.div
                        key={i}
                        className="relative"
                        style={{
                          gridRow: "1 / -1",
                          gridColumn: "1 / -1",
                          width: "46%",
                          zIndex,
                        }}
                        initial={false}
                        animate={{
                          left: `${leftPct}%`,
                          scale,
                        }}
                        transition={{
                          type: "spring",
                          stiffness: 300,
                          damping: 28,
                        }}
                      >
                        {/* Card shell — fills grid row height (tallest card wins) */}
                        <div
                          className="relative h-full cursor-pointer"
                          onClick={(e) => {
                            e.stopPropagation();
                            if (!isActive) {
                              setTopIndex(i);
                            } else if (onStageClick && childInc) {
                              onStageClick(childInc, status === "locked");
                            }
                          }}
                        >
                          {/* Card content — opacity + blur applied here so overlays stay crisp */}
                          <div
                            className={cn(
                              "group/stage flex flex-col bg-card rounded-xl border overflow-hidden transition-[box-shadow,border-color] duration-200 h-full",
                              isActive && (isManage || status === "active")
                                ? cn(
                                    "border-border shadow-lg",
                                    accent.hoverBorder,
                                    accent.hoverShadow,
                                  )
                                : isActive
                                  ? "border-border shadow-lg"
                                  : "border-border shadow-md cursor-pointer pointer-events-none",
                            )}
                            style={{
                              opacity,
                              filter: blur > 0 ? `blur(${blur}px)` : undefined,
                              transition: "opacity 0.4s ease, filter 0.4s ease",
                            }}
                          >
                            {/* Hover flood (active + unlocked card only — no hover for locked/completed; always on for manage) */}
                            {isActive && (isManage || status === "active") && (
                              <div
                                className="pointer-events-none absolute top-0 left-0 right-0 h-0 group-hover/stage:h-full rounded-xl z-0"
                                style={{
                                  background: accent.hoverWash,
                                  transition:
                                    "height 0.7s cubic-bezier(0.22, 0.61, 0.36, 1)",
                                }}
                              />
                            )}

                            {/* Card content — manage uses a stripped-down stage
                                card (description + creator), view uses the full
                                participant-facing PartnerIncentiveCard.
                                If the stage's full incentive isn't in allIncentives (data gap),
                                render a placeholder — never fall back to the parent Journey. */}
                            <div className="relative flex-1 flex flex-col overflow-hidden">
                              {childInc ? (
                                isManage ? (
                                  <ManagedJourneyStageCard
                                    incentive={childInc}
                                    extraActions={
                                      <PermissionGate permission="action.incentive.edit">
                                        <Button
                                          variant="ghost"
                                          size="sm"
                                          className="shrink-0 h-8 w-8 p-0"
                                          onClick={(e) => {
                                            e.stopPropagation();
                                            onStageEdit?.(childInc);
                                          }}
                                        >
                                          <Pencil className="h-4 w-4" />
                                        </Button>
                                      </PermissionGate>
                                    }
                                  />
                                ) : (
                                  <PartnerIncentiveCard
                                    incentive={childInc}
                                    embedded
                                    onClick={
                                      isActive && status === "active"
                                        ? onAction
                                        : undefined
                                    }
                                    onTakeCourses={
                                      isActive &&
                                      status === "active" &&
                                      onAction
                                        ? () => onAction()
                                        : undefined
                                    }
                                    onTakeAction={
                                      isActive &&
                                      status === "active" &&
                                      onAction
                                        ? () => onAction()
                                        : undefined
                                    }
                                  />
                                )
                              ) : (
                                <StageUnavailablePlaceholder stage={stage} />
                              )}
                            </div>
                          </div>

                          {/* Inactive card LOCKED overlay — icon/text shifted toward exposed edge.
                              Overlay fades out with distance so distant cards don't have a heavy white wash. */}
                          {!isManage &&
                            !isActive &&
                            status === "locked" &&
                            (() => {
                              const overlayOpacity =
                                absPos === 1 ? 0.7 : absPos === 2 ? 0.4 : 0.15;
                              const contentOpacity =
                                absPos === 1 ? 1 : absPos === 2 ? 0.6 : 0.2;
                              return (
                                <div
                                  className={cn(
                                    "absolute inset-0 z-10 rounded-xl flex flex-col justify-center pointer-events-none",
                                    pos > 0
                                      ? "items-end pr-[18%]"
                                      : "items-start pl-[18%]",
                                  )}
                                  style={{
                                    backgroundColor: `rgba(255, 255, 255, ${overlayOpacity})`,
                                    backdropFilter:
                                      absPos <= 2 ? "blur(2px)" : undefined,
                                    transition:
                                      "background-color 0.4s ease, backdrop-filter 0.4s ease",
                                  }}
                                >
                                  <div
                                    style={{
                                      opacity: contentOpacity,
                                      transition: "opacity 0.4s ease",
                                    }}
                                  >
                                    <Lock className="h-10 w-10 mb-1.5 text-muted-foreground" />
                                    <span className="text-xs font-extrabold uppercase tracking-wide text-foreground">
                                      Step {i + 1}
                                    </span>
                                  </div>
                                </div>
                              );
                            })()}

                          {/* Inactive card COMPLETED overlay — green tint + corner ribbon */}
                          {!isManage &&
                            !isActive &&
                            status === "completed" &&
                            (() => {
                              const overlayOpacity =
                                absPos === 1 ? 0.55 : absPos === 2 ? 0.3 : 0.1;
                              const contentOpacity =
                                absPos === 1 ? 1 : absPos === 2 ? 0.6 : 0.2;
                              const ribbonOpacity =
                                absPos === 1 ? 1 : absPos === 2 ? 0.5 : 0.15;
                              return (
                                <div
                                  className={cn(
                                    "absolute inset-0 z-10 rounded-xl flex flex-col justify-center pointer-events-none overflow-hidden",
                                    pos > 0
                                      ? "items-end pr-[18%]"
                                      : "items-start pl-[18%]",
                                  )}
                                  style={{
                                    backgroundColor: `hsla(130, 50%, 92%, ${overlayOpacity})`,
                                    backdropFilter:
                                      absPos <= 2 ? "blur(1px)" : undefined,
                                    transition:
                                      "background-color 0.4s ease, backdrop-filter 0.4s ease",
                                  }}
                                >
                                  {/* Corner ribbon */}
                                  <div
                                    className="absolute top-0 right-0"
                                    style={{
                                      opacity: ribbonOpacity,
                                      transition: "opacity 0.4s ease",
                                      width: 90,
                                      height: 90,
                                      overflow: "hidden",
                                    }}
                                  >
                                    <div
                                      className="absolute text-center text-[9px] font-semibold uppercase tracking-widest text-white"
                                      style={{
                                        backgroundColor: "hsl(var(--success))",
                                        width: 130,
                                        top: 18,
                                        right: -32,
                                        padding: "4px 0",
                                        transform: "rotate(45deg)",
                                        boxShadow: "0 1px 3px rgba(0,0,0,0.2)",
                                      }}
                                    >
                                      Completed
                                    </div>
                                  </div>
                                  {/* Center icon */}
                                  <div
                                    style={{
                                      opacity: contentOpacity,
                                      transition: "opacity 0.4s ease",
                                    }}
                                  >
                                    <CheckCircle2 className="h-10 w-10 mb-1.5 text-success" />
                                    <span className="text-xs font-extrabold uppercase tracking-wide text-success">
                                      Step {i + 1}
                                    </span>
                                  </div>
                                </div>
                              );
                            })()}

                          {/* Active card locked overlay — heavier because it's the focused card */}
                          {!isManage && status === "locked" && isActive && (
                            <div className="absolute inset-0 bg-background/70 z-10 rounded-xl flex items-center justify-center">
                              <div className="text-center">
                                <Lock className="h-10 w-10 text-muted-foreground mx-auto mb-1.5" />
                                <p className="text-xs font-extrabold text-foreground uppercase tracking-wide">
                                  Step {i + 1} — Locked
                                </p>
                                <p className="text-xs text-muted-foreground mt-1">
                                  Complete step {i} to unlock
                                </p>
                              </div>
                            </div>
                          )}

                          {/* Active card completed overlay — heavy green wash like locked card's white wash */}
                          {!isManage && status === "completed" && isActive && (
                            <div
                              className="absolute inset-0 z-10 rounded-xl flex items-center justify-center overflow-hidden"
                              style={{
                                backgroundColor: "hsla(130, 50%, 92%, 0.75)",
                              }}
                            >
                              {/* Corner ribbon */}
                              <div
                                className="absolute top-0 right-0"
                                style={{
                                  width: 100,
                                  height: 100,
                                  overflow: "hidden",
                                }}
                              >
                                <div
                                  className="absolute text-center text-[10px] font-semibold uppercase tracking-widest text-white"
                                  style={{
                                    backgroundColor: "hsl(var(--success))",
                                    width: 150,
                                    top: 22,
                                    right: -38,
                                    padding: "5px 0",
                                    transform: "rotate(45deg)",
                                    boxShadow: "0 2px 6px rgba(0,0,0,0.2)",
                                  }}
                                >
                                  Completed
                                </div>
                              </div>
                              <div className="text-center">
                                <CheckCircle2 className="h-10 w-10 text-success mx-auto mb-1.5" />
                                <p className="text-xs font-extrabold text-success uppercase tracking-wide">
                                  Step {i + 1} — Completed
                                </p>
                              </div>
                            </div>
                          )}
                        </div>
                      </motion.div>
                    );
                  })}
                </div>

                {/* Right arrow */}
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    setTopIndex((prev) =>
                      Math.min(stages.length - 1, prev + 1),
                    );
                  }}
                  disabled={topIndex === stages.length - 1}
                  className="shrink-0 flex items-center justify-center w-8 h-8 rounded-full bg-background border border-border shadow-sm hover:shadow-md text-foreground transition-shadow disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer z-10"
                >
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>
            </div>
          </motion.div>
        ) : (
          <motion.div
            key="grid"
            initial={{ opacity: 0, scale: 0.97 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.97 }}
            transition={{ duration: 0.25, ease: "easeInOut" }}
          >
            {/* ── Grid layout — 2-column grid of stage cards ─────────── */}
            <div
              className="relative z-[1] px-5 pb-5 pt-2"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="grid grid-cols-2 gap-4">
                {stages.map((stage, i) => {
                  const childInc = allIncentives.find(
                    (inc) =>
                      inc.name === stage.incentiveName &&
                      inc.incentiveType === stage.incentiveType,
                  );
                  const status = getStageStatus(stage, i);
                  const accent = getAccentForType(stage.incentiveType);

                  return (
                    <motion.div
                      key={i}
                      className="h-full"
                      initial={{ opacity: 0, y: 12 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ duration: 0.2, delay: i * 0.04 }}
                    >
                      <div
                        className={cn(
                          "group/stage relative flex flex-col bg-card rounded-xl border overflow-hidden transition-[box-shadow,border-color] duration-200 h-full",
                          "border-border shadow-md",
                          accent.hoverBorder,
                          accent.hoverShadow,
                        )}
                      >
                        {/* Hover flood */}
                        {(isManage || status === "active") && (
                          <div
                            className="pointer-events-none absolute top-0 left-0 right-0 h-0 group-hover/stage:h-full rounded-xl z-0"
                            style={{
                              background: accent.hoverWash,
                              transition:
                                "height 0.7s cubic-bezier(0.22, 0.61, 0.36, 1)",
                            }}
                          />
                        )}

                        {/* Step badge — centered at top, always above overlays */}
                        <div className="absolute top-2.5 left-1/2 -translate-x-1/2 z-20">
                          <span
                            className={cn(
                              "inline-flex items-center justify-center h-8 w-8 rounded-full text-xs font-bold shadow-sm ring-2 ring-background",
                              status === "completed"
                                ? "bg-success text-primary-foreground"
                                : status === "active"
                                  ? "bg-primary text-primary-foreground shadow-md shadow-primary/30"
                                  : "bg-muted/80 text-muted-foreground",
                            )}
                          >
                            {status === "completed" ? (
                              <CheckCircle2 className="h-4 w-4" />
                            ) : (
                              i + 1
                            )}
                          </span>
                        </div>

                        {/* Card content — manage uses a stripped-down stage
                            card (description + creator), view uses the full
                            participant-facing PartnerIncentiveCard.
                            Placeholder if stage's full incentive is missing
                            from allIncentives (never fall back to parent Journey — BUG-019). */}
                        <div className="relative flex-1 flex flex-col overflow-hidden">
                          {childInc ? (
                            isManage ? (
                              <ManagedJourneyStageCard
                                incentive={childInc}
                                extraActions={
                                  <PermissionGate permission="action.incentive.edit">
                                    <Button
                                      variant="ghost"
                                      size="sm"
                                      className="shrink-0 h-8 w-8 p-0"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        onStageEdit?.(childInc);
                                      }}
                                    >
                                      <Pencil className="h-4 w-4" />
                                    </Button>
                                  </PermissionGate>
                                }
                              />
                            ) : (
                              <PartnerIncentiveCard
                                incentive={childInc}
                                embedded
                              />
                            )
                          ) : (
                            <StageUnavailablePlaceholder stage={stage} />
                          )}
                        </div>

                        {/* Locked overlay — grid version */}
                        {!isManage && status === "locked" && (
                          <div className="absolute inset-0 bg-background/70 z-10 rounded-xl flex items-center justify-center">
                            <div className="text-center">
                              <Lock className="h-8 w-8 text-muted-foreground mx-auto mb-1" />
                              <p className="text-xs font-extrabold text-foreground uppercase tracking-wide">
                                Step {i + 1} — Locked
                              </p>
                            </div>
                          </div>
                        )}

                        {/* Completed overlay — grid version */}
                        {!isManage && status === "completed" && (
                          <div
                            className="absolute inset-0 z-10 rounded-xl flex items-center justify-center overflow-hidden"
                            style={{
                              backgroundColor: "hsla(130, 50%, 92%, 0.75)",
                            }}
                          >
                            {/* Corner ribbon */}
                            <div
                              className="absolute top-0 right-0"
                              style={{
                                width: 90,
                                height: 90,
                                overflow: "hidden",
                              }}
                            >
                              <div
                                className="absolute text-center text-[9px] font-semibold uppercase tracking-widest text-white"
                                style={{
                                  backgroundColor: "hsl(var(--success))",
                                  width: 130,
                                  top: 18,
                                  right: -32,
                                  padding: "4px 0",
                                  transform: "rotate(45deg)",
                                  boxShadow: "0 1px 3px rgba(0,0,0,0.2)",
                                }}
                              >
                                Completed
                              </div>
                            </div>
                            <div className="text-center">
                              <CheckCircle2 className="h-8 w-8 text-success mx-auto mb-1" />
                              <p className="text-xs font-extrabold text-success uppercase tracking-wide">
                                Step {i + 1} — Done
                              </p>
                            </div>
                          </div>
                        )}
                      </div>
                    </motion.div>
                  );
                })}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </Card>
  );
}
