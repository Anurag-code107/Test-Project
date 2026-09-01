import { useState } from "react";
import { PermissionGate } from "@/components/PermissionGate";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Calendar,
  User,
  TrendingUp,
  Pencil,
  Send,
  Zap,
  RotateCcw,
  Eye,
  Trash2,
  ChevronDown,
  ChevronRight,
  Layers,
  ArrowLeft,
} from "lucide-react";
import type {
  IncentiveResponse,
  IncentiveStatus,
  IncentiveType,
} from "@/types/incentive.types";
import { INCENTIVE_STATUS_LABELS } from "@/types/incentive.types";
import { cn } from "@/lib/utils";
import {
  engagementIconMap,
  engagementColors,
  stageTypeColors,
  cardAccent,
  formatIncentiveDate,
} from "@/components/incentive-card/incentive-card.config";

import { Progress } from "@/components/ui/progress";
import { DocumentsPopover } from "@/components/view-incentives/DocumentsPopover";
import { RewardBreakdownHover as SharedRewardBreakdownHover } from "@/components/RewardBreakdownHover";
import { getCurrency } from "@/config/currencies";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";

/* ── Helper components to render icons from shared config ────────────── */

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

const statusStyles: Record<IncentiveStatus, string> = {
  DRAFT: "bg-muted text-muted-foreground",
  PENDING_APPROVAL:
    "bg-[hsl(38_90%_50%/0.12)] text-amber-600 ring-1 ring-amber-500/20",
  DENIED: "bg-[hsl(0_65%_50%/0.08)] text-[hsl(0_65%_50%)]",
  ACTIVE: "bg-[hsl(95_55%_42%/0.08)] text-[hsl(95_55%_42%)]",
  INACTIVE: "bg-[hsl(0_65%_50%/0.08)] text-[hsl(0_65%_50%)]",
};

function BudgetBreakdownHover({
  budgets,
  monetaryTotal,
  label,
  suffix,
}: {
  budgets: Record<string, string | number>;
  monetaryTotal: number;
  label: string;
  align?: "start" | "center" | "end";
  suffix?: string;
}) {
  // animate=false: each animated SlotDropNumber renders ~230 spans of slot-reel
  // markup; with two breakdown hovers per card and a full grid of incentives
  // that's the single largest DOM contributor on /manage-incentives, and it
  // dominates style-recalc / INP on every drawer open. The plain-span
  // fallback is documented for exactly this case in RewardBreakdownHover.
  return (
    <SharedRewardBreakdownHover
      label={label}
      entries={budgets}
      monetaryTotal={monetaryTotal}
      suffix={suffix}
      animate={false}
    />
  );
}

function getAllowedStatuses(currentStatus: IncentiveStatus): IncentiveStatus[] {
  switch (currentStatus) {
    case "ACTIVE":
      return ["ACTIVE", "INACTIVE"];
    case "INACTIVE":
      return ["INACTIVE", "ACTIVE"];
    case "DRAFT":
      return ["DRAFT", "INACTIVE"];
    case "PENDING_APPROVAL":
      return ["PENDING_APPROVAL", "INACTIVE"];
    default:
      return [currentStatus];
  }
}

interface ManagedIncentiveCardProps {
  incentive: IncentiveResponse;
  onStatusChange: (id: string, newStatus: IncentiveStatus) => void;
  onDelete: (id: string) => void;
  onEdit: () => void;
  onSubmitForApproval?: (id: string) => void;
  onResubmitForApproval?: (id: string) => void;
  onClick?: () => void;
}

export function ManagedIncentiveCard({
  incentive,
  onStatusChange,
  onDelete,
  onEdit,
  onSubmitForApproval,
  onResubmitForApproval,
  onClick,
}: ManagedIncentiveCardProps) {
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [activateDialogOpen, setActivateDialogOpen] = useState(false);
  const [submitDialogOpen, setSubmitDialogOpen] = useState(false);
  const [activeStageIndex, setActiveStageIndex] = useState<number | null>(null);
  const [isTransitioning, setIsTransitioning] = useState(false);

  const startDate = incentive.startDate ? new Date(incentive.startDate) : null;
  const endDate = incentive.endDate ? new Date(incentive.endDate) : null;

  const handleEdit = (e: React.MouseEvent) => {
    e.stopPropagation();
    onEdit();
  };

  const handleStatusChange = (newStatus: IncentiveStatus) => {
    onStatusChange(incentive.id, newStatus);
  };

  const handleDelete = () => {
    onDelete(incentive.id);
    setDeleteDialogOpen(false);
  };

  const handleActivate = (e: React.MouseEvent) => {
    e.stopPropagation();
    setActivateDialogOpen(true);
  };

  const confirmActivate = () => {
    onStatusChange(incentive.id, "ACTIVE");
    setActivateDialogOpen(false);
  };

  const handleSubmitForApproval = (e: React.MouseEvent) => {
    e.stopPropagation();
    setSubmitDialogOpen(true);
  };

  const confirmSubmitForApproval = () => {
    onSubmitForApproval?.(incentive.id);
    setSubmitDialogOpen(false);
  };

  const showActivateButton =
    incentive.status === "DRAFT" && !incentive.requiresApproval;
  const showSubmitButton =
    incentive.status === "DRAFT" && incentive.requiresApproval === true;
  const showApprovalsButton = incentive.status === "PENDING_APPROVAL";
  const showResubmitButton = incentive.status === "DENIED";

  const handleViewApprovals = (e: React.MouseEvent) => {
    e.stopPropagation();
    onClick?.();
  };

  const handleResubmit = (e: React.MouseEvent) => {
    e.stopPropagation();
    onResubmitForApproval?.(incentive.id);
  };

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

  const accent = cardAccent[incentive.incentiveType];

  return (
    <Card
      className={cn(
        "relative cursor-pointer rounded-xl flex flex-col h-full transition-[box-shadow,border-color,transform] duration-300 group overflow-hidden hover:-translate-y-0.5",
        accent.hoverBorder,
        accent.hoverShadow,
      )}
      onClick={onClick}
      data-tour="incentive-card"
    >
      {/* Hover flood — colour bleeds down from top on hover */}
      <div
        className="pointer-events-none absolute top-0 left-0 right-0 h-0 group-hover:h-full rounded-xl z-0"
        style={{
          background: accent.hoverWash,
          transition: "height 0.7s cubic-bezier(0.22, 0.61, 0.36, 1)",
        }}
      />

      {/* Header band — type icon + name | status */}
      <div
        className="relative z-[1] rounded-t-xl px-5 pt-4 pb-3"
        style={{
          background: accent.bandGradient,
          borderBottom: `1px solid ${accent.bandBorder}`,
        }}
      >
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-2 flex-1 min-w-0">
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

          <DropdownMenu>
            <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
              <button
                className={cn(
                  "shrink-0 text-xs font-medium px-2 py-0.5 rounded-md inline-flex items-center gap-1 transition-opacity hover:opacity-80",
                  statusStyles[incentive.status],
                )}
              >
                {INCENTIVE_STATUS_LABELS[incentive.status]}
                <ChevronDown className="h-2.5 w-2.5 opacity-50" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent
              align="end"
              className="w-48 rounded-xl"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="px-2.5 py-1.5 text-xs font-medium text-muted-foreground">
                Change Status
              </div>
              {getAllowedStatuses(incentive.status).map((status) => (
                <DropdownMenuItem
                  key={status}
                  onClick={() => handleStatusChange(status)}
                  disabled={status === incentive.status}
                  className="cursor-pointer text-sm"
                >
                  {INCENTIVE_STATUS_LABELS[status]}
                  {status === incentive.status && (
                    <span className="ml-auto text-xs text-muted-foreground">
                      current
                    </span>
                  )}
                </DropdownMenuItem>
              ))}
              <PermissionGate permission="action.incentive.delete">
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  className="text-destructive focus:text-destructive cursor-pointer text-sm"
                  onClick={() => setDeleteDialogOpen(true)}
                >
                  <Trash2 className="h-3.5 w-3.5 mr-2" />
                  Delete Incentive
                </DropdownMenuItem>
              </PermissionGate>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {/* Body content below header band */}
      <div className="relative z-[1] flex flex-col flex-1 px-5 pt-3 pb-5">
        {/* Journey stages */}
        {hasStages && (
          <div className="relative z-10 mb-3 p-3 rounded-lg bg-primary/5 border border-primary/15 shadow-sm group-hover:border-primary/30 group-hover:shadow-md transition-[border-color,box-shadow] duration-300">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                <Layers className="h-3 w-3" />
                <span>{stages.length} stages</span>
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
            <div className="overflow-x-auto -mx-1 px-1 py-1">
              <div className="flex items-center gap-1.5">
                {stages
                  .sort((a, b) => a.sortOrder - b.sortOrder)
                  .map((stage, index) => (
                    <div key={index} className="flex items-center shrink-0">
                      <button
                        onClick={(e) => handleStageClick(index, e)}
                        className={cn(
                          "flex items-center justify-center gap-1.5 px-3 py-1 rounded-md border text-xs font-medium whitespace-nowrap transition-[transform,opacity,box-shadow]",
                          stageTypeColors[stage.incentiveType] ??
                            "bg-muted text-muted-foreground border-border",
                          activeStageIndex === index
                            ? "ring-2 ring-offset-1 ring-indigo-500/50"
                            : "hover:scale-[1.03] cursor-pointer",
                          activeStageIndex != null &&
                            activeStageIndex !== index &&
                            "opacity-40",
                        )}
                      >
                        <StageTypeIcon
                          type={stage.incentiveType}
                          className="h-3 w-3"
                        />
                        <span>{index + 1}</span>
                      </button>
                      {index < stages.length - 1 && (
                        <ChevronRight className="h-2.5 w-2.5 mx-0.5 shrink-0 text-muted-foreground" />
                      )}
                    </div>
                  ))}
              </div>
            </div>
            <div className="mt-2 pt-2 border-t border-border">
              <span className="text-xs text-muted-foreground">
                {activeStage
                  ? `Stage ${activeStageIndex! + 1} — ${activeStage.incentiveName}`
                  : "Program Overview"}
              </span>
            </div>
          </div>
        )}

        {/* Description / Stage detail */}
        <div
          className={cn(
            "transition-[opacity,transform] duration-150",
            isTransitioning
              ? "opacity-0 translate-y-1"
              : "opacity-100 translate-y-0",
          )}
        >
          {activeStage ? (
            <div className="space-y-1.5 mb-3">
              <div className="flex items-center gap-2">
                {activeStage.incentiveType && (
                  <span
                    className={cn(
                      "shrink-0",
                      stageTypeColors[activeStage.incentiveType]?.split(" ")[1],
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
                <span
                  className={cn(
                    "inline-flex items-center gap-1 px-2 py-0.5 rounded-md border text-xs font-medium",
                    stageTypeColors[activeStage.incentiveType] ??
                      "bg-muted text-muted-foreground border-border",
                  )}
                >
                  {activeStage.incentiveType.charAt(0) +
                    activeStage.incentiveType.slice(1).toLowerCase()}{" "}
                  Incentive
                </span>
              )}
              {activeStage.incentiveDescription && (
                <p className="text-xs text-muted-foreground line-clamp-2">
                  {activeStage.incentiveDescription}
                </p>
              )}
            </div>
          ) : (
            incentive.description && (
              <p className="text-sm text-muted-foreground line-clamp-2 mb-3 leading-relaxed">
                {incentive.description}
              </p>
            )
          )}
        </div>

        {/* Budget Utilization */}
        {incentive.budgetTotal &&
          (() => {
            const totalNum = parseFloat(incentive.budgetTotal);
            const utilPercent = incentive.budgetUtilizationPercent ?? 0;
            const utilizedNum = totalNum * (utilPercent / 100);

            // Build per-currency budget entries from the API response
            const budgetEntries: Record<string, string | number> = {};
            const usedEntries: Record<string, string | number> = {};
            let monetaryTotal = 0;
            let monetaryUsed = 0;
            if (incentive.budgets && incentive.budgets.length > 0) {
              for (const b of incentive.budgets) {
                const amt = parseFloat(b.totalBudget) || 0;
                budgetEntries[b.currencyId] = amt;
                usedEntries[b.currencyId] = Math.round(
                  amt * (utilPercent / 100),
                );
                // Only monetary currencies count toward the budget total
                const currencyType = getCurrency(b.currencyId).type;
                if (currencyType === "monetary") {
                  monetaryTotal += amt;
                  monetaryUsed += amt * (utilPercent / 100);
                }
              }
            } else {
              // Fallback: single budget entry from budgetTotal/budgetCurrency
              const currency = incentive.budgetCurrency || "cash";
              budgetEntries[currency] = totalNum;
              usedEntries[currency] = utilizedNum;
              // Only count toward monetary total if the fallback currency is monetary
              if (getCurrency(currency).type === "monetary") {
                monetaryTotal = totalNum;
                monetaryUsed = utilizedNum;
              }
            }

            return (
              <div className="space-y-2 mb-4">
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
                  <BudgetBreakdownHover
                    budgets={usedEntries}
                    monetaryTotal={monetaryUsed}
                    label="Used"
                    align="start"
                    suffix="used"
                  />
                  <BudgetBreakdownHover
                    budgets={budgetEntries}
                    monetaryTotal={monetaryTotal}
                    label="Budget"
                    align="end"
                    suffix="total"
                  />
                </div>
              </div>
            );
          })()}

        {/* Footer */}
        <div className="mt-auto pt-3 border-t border-border">
          <div className="flex items-center justify-between">
            <div className="space-y-1">
              {startDate && endDate && (
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <Calendar className="h-3 w-3" />
                  <span>
                    {formatIncentiveDate(startDate)} –{" "}
                    {formatIncentiveDate(endDate)}
                  </span>
                </div>
              )}
              {incentive.createdByName && (
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <User className="h-3 w-3" />
                  <span>{incentive.createdByName}</span>
                </div>
              )}
            </div>
            <div className="flex items-center gap-1" data-tour="card-actions">
              {incentive.documents && incentive.documents.length > 0 && (
                <DocumentsPopover
                  documents={incentive.documents}
                  incentiveId={incentive.id}
                />
              )}
              {showActivateButton && (
                <PermissionGate permission="action.incentive.activate">
                  <Button
                    variant="default"
                    size="sm"
                    className="shrink-0 h-7 text-xs px-2.5"
                    onClick={handleActivate}
                    data-tour="activate-button"
                  >
                    <Zap className="h-3 w-3 mr-1" />
                    Activate
                  </Button>
                </PermissionGate>
              )}
              {showSubmitButton && (
                <PermissionGate permission="action.incentive.submit_approval">
                  <Button
                    variant="default"
                    size="sm"
                    className="shrink-0 h-7 text-xs px-2.5"
                    onClick={handleSubmitForApproval}
                    data-tour="submit-button"
                  >
                    <Send className="h-3 w-3 mr-1" />
                    Submit
                  </Button>
                </PermissionGate>
              )}
              {showApprovalsButton && (
                <Button
                  variant="secondary"
                  size="sm"
                  className="shrink-0 h-7 text-xs px-2.5"
                  onClick={handleViewApprovals}
                  data-tour="approvals-button"
                >
                  <Eye className="h-3 w-3 mr-1" />
                  Approvals
                </Button>
              )}
              {showResubmitButton && (
                <Button
                  variant="outline"
                  size="sm"
                  className="shrink-0 h-7 text-xs px-2.5 border-amber-500/30 text-amber-600 hover:bg-amber-500/5"
                  onClick={handleResubmit}
                  data-tour="resubmit-button"
                >
                  <RotateCcw className="h-3 w-3 mr-1" />
                  Resubmit
                </Button>
              )}
              <PermissionGate permission="action.incentive.edit">
                <Button
                  variant="ghost"
                  size="sm"
                  className="shrink-0 h-8 w-8 p-0"
                  onClick={handleEdit}
                  data-tour="edit-button"
                >
                  <Pencil className="h-4 w-4" />
                </Button>
              </PermissionGate>
            </div>
          </div>
        </div>
      </div>

      {/* Dialogs */}
      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent onClick={(e) => e.stopPropagation()}>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Incentive</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete &quot;{incentive.name}&quot;? This
              action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog
        open={activateDialogOpen}
        onOpenChange={setActivateDialogOpen}
      >
        <AlertDialogContent onClick={(e) => e.stopPropagation()}>
          <AlertDialogHeader>
            <AlertDialogTitle>Activate Incentive</AlertDialogTitle>
            <AlertDialogDescription>
              This will activate &quot;{incentive.name}&quot; immediately. The
              incentive will become visible and available to participants.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={confirmActivate}
              className="bg-success text-success-foreground hover:bg-success/90"
            >
              Activate
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={submitDialogOpen} onOpenChange={setSubmitDialogOpen}>
        <AlertDialogContent onClick={(e) => e.stopPropagation()}>
          <AlertDialogHeader>
            <AlertDialogTitle>Submit for Approval</AlertDialogTitle>
            <AlertDialogDescription>
              This will submit &quot;{incentive.name}&quot; for approval and
              send email notifications to all configured approvers. The status
              will change to Pending Approval.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={confirmSubmitForApproval}
              className="bg-primary text-primary-foreground hover:bg-primary/90"
            >
              Submit
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  );
}
