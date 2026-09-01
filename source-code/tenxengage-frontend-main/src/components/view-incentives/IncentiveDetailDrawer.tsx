import { useState, useRef, useEffect } from "react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import {
  Calendar,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  FileText,
  Loader2,
  User,
  TrendingUp,
  Download,
  Plus,
  Upload,
  X,
  Megaphone,
  GraduationCap,
  FileCheck,
  Lock,
} from "lucide-react";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import {
  useIncentive,
  useUploadIncentiveDocuments,
} from "@/hooks/useIncentiveApi";
import { usePermissions } from "@/hooks/usePermissions";
import { toast } from "sonner";
import {
  downloadDocument,
  viewDocument,
  validateFiles,
} from "@/services/incentive.service";
import {
  getCurrency,
  monetaryCurrencyIds,
  nonMonetaryCurrencyIds,
} from "@/config/currencies";
import type {
  IncentiveResponse,
  IncentiveDetailResponse,
  IncentiveType,
  DocumentSummary,
  JourneyStageSummary,
} from "@/types/incentive.types";
import {
  engagementIcons,
  engagementColors,
  statusColors,
  statusLabels,
  typeLabels,
  getCurrencyColors,
  formatCurrencyAmount,
  formatDate,
  ActivityDefinitionsList,
  TrainingCoursesList,
  BudgetBreakdownSection,
  DocumentRow,
} from "./incentive-detail-shared";
import { ApprovalStatusSection } from "./ApprovalStatusSection";

interface IncentiveDetailDrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  incentiveId: string | null;
  /** List-level incentive data used as fallback when the detail API omits fields (budget, documents, etc.) */
  listIncentive?: IncentiveResponse;
  /** @deprecated Use listIncentive instead */
  fallbackDocuments?: DocumentSummary[];
  /** When true, shows a "This step is locked" banner — used for locked journey stages */
  isLockedJourneyStage?: boolean;
}

// --- Reward Display (Earn Up To badge — shown for partner roles only) ---

function RewardDisplay({ incentive }: { incentive: IncentiveDetailResponse }) {
  const rewardCurrencyIds = incentive.rewardCurrencies ?? [];
  if (rewardCurrencyIds.length === 0 && !incentive.rewardMessage) return null;

  const monetary = [...monetaryCurrencyIds].filter((id) =>
    rewardCurrencyIds.includes(id),
  );
  const nonMonetary = [...nonMonetaryCurrencyIds].filter((id) =>
    rewardCurrencyIds.includes(id),
  );
  const sortedIds = [...monetary, ...nonMonetary];
  const primaryId = sortedIds[0];
  if (!primaryId) return null;

  const cfg = getCurrency(primaryId);
  const Icon = cfg.icon;
  const colors = getCurrencyColors(primaryId);

  return (
    <div
      className={cn(
        "flex items-center gap-2.5 px-4 py-3 rounded-xl border shrink-0",
        colors.bg,
        colors.border,
      )}
    >
      <div
        className={cn(
          "flex items-center justify-center h-8 w-8 rounded-lg",
          colors.iconBg,
        )}
      >
        <span className={cfg.iconClass}>
          <Icon className="h-4 w-4" />
        </span>
      </div>
      <div className="flex flex-col items-end">
        <span
          className={cn(
            "text-xs font-semibold uppercase tracking-wider leading-none",
            cfg.iconClass,
          )}
        >
          Earn up to
        </span>
        <span className="font-semibold text-lg text-foreground leading-tight">
          {(() => {
            const amt = incentive.rewardAmounts?.[primaryId];
            return amt
              ? formatCurrencyAmount(primaryId, amt)
              : (incentive.rewardMessage ?? "");
          })()}
        </span>
      </div>
    </div>
  );
}

// --- Journey Stages ---

const stageIcons: Record<string, React.ReactNode> = {
  SALES: <Megaphone className="h-4 w-4" />,
  TRAINING: <GraduationCap className="h-4 w-4" />,
  ACTIVITY: <FileCheck className="h-4 w-4" />,
};

const stageColors: Record<string, string> = {
  SALES: "bg-primary/20 text-primary border-primary/30",
  TRAINING: "bg-amber-500/20 text-amber-600 border-amber-500/30",
  ACTIVITY: "bg-blue-500/20 text-blue-500 border-blue-500/30",
};

function JourneyStagesSection({ stages }: { stages: JourneyStageSummary[] }) {
  const [activeIndex, setActiveIndex] = useState(0);
  const totalStages = stages.length;
  const activeStage = stages[activeIndex];

  return (
    <div className="rounded-xl border border-border p-4 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
          Stages ({totalStages})
        </h3>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            onClick={() => setActiveIndex((i) => i - 1)}
            disabled={activeIndex === 0}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-sm font-medium min-w-[60px] text-center">
            {activeIndex + 1} / {totalStages}
          </span>
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            onClick={() => setActiveIndex((i) => i + 1)}
            disabled={activeIndex === totalStages - 1}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Stage indicators */}
      <div className="flex gap-2 overflow-x-auto pb-2 -mx-1 px-1 py-1">
        {stages.map((stage, index) => (
          <button
            key={index}
            onClick={() => setActiveIndex(index)}
            className={cn(
              "flex items-center gap-2 px-4 py-2 rounded-lg border text-sm font-medium whitespace-nowrap transition-[opacity,box-shadow]",
              stageColors[stage.incentiveType] ??
                "bg-muted text-muted-foreground border-border",
              activeIndex === index
                ? "ring-2 ring-offset-2 ring-indigo-500"
                : "opacity-60 hover:opacity-100",
            )}
          >
            {stageIcons[stage.incentiveType]}
            <span>Stage {index + 1}</span>
          </button>
        ))}
      </div>

      {/* Active stage details */}
      {activeStage && (
        <div className="bg-muted/30 rounded-lg p-4 space-y-3">
          <div className="flex items-center gap-2">
            <span
              className={stageColors[activeStage.incentiveType]?.split(" ")[1]}
            >
              {stageIcons[activeStage.incentiveType]}
            </span>
            <h4 className="font-semibold text-foreground">
              {activeStage.incentiveName}
            </h4>
          </div>
          <Badge
            variant="outline"
            className={cn("text-xs", stageColors[activeStage.incentiveType])}
          >
            {activeStage.incentiveType.charAt(0) +
              activeStage.incentiveType.slice(1).toLowerCase()}{" "}
            Incentive
          </Badge>
          {activeStage.incentiveDescription && (
            <p className="text-sm text-muted-foreground leading-relaxed">
              {activeStage.incentiveDescription}
            </p>
          )}
          {activeStage.incentiveStatus && (
            <Badge
              variant="outline"
              className={cn(
                "text-xs",
                statusColors[activeStage.incentiveStatus],
              )}
            >
              {statusLabels[activeStage.incentiveStatus]}
            </Badge>
          )}
        </div>
      )}
    </div>
  );
}

// --- Add Document Popover ---

const documentCategories = [
  { value: "terms-conditions", label: "Terms & Conditions" },
  { value: "eligible-products", label: "Eligible Products" },
  { value: "program-rules", label: "Program Rules" },
  { value: "faq", label: "FAQ" },
];

const noEligibleProductsTypes: IncentiveType[] = ["TRAINING", "ACTIVITY"];

interface AddDocumentPopoverProps {
  incentiveType?: IncentiveType;
  disabled?: boolean;
  onAdd: (category: string) => void;
  container?: HTMLElement | null;
}

function AddDocumentPopover({
  incentiveType,
  disabled,
  onAdd,
  container,
}: AddDocumentPopoverProps) {
  const [popoverOpen, setPopoverOpen] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState("");
  const [customCategory, setCustomCategory] = useState("");
  const [showCustomInput, setShowCustomInput] = useState(false);

  const filteredCategories = documentCategories.filter(
    (cat) =>
      !(
        incentiveType &&
        noEligibleProductsTypes.includes(incentiveType) &&
        cat.value === "eligible-products"
      ),
  );

  const resolvedCategory = showCustomInput
    ? customCategory.trim()
    : selectedCategory;

  const handleReset = () => {
    setSelectedCategory("");
    setCustomCategory("");
    setShowCustomInput(false);
  };

  return (
    <Popover
      open={popoverOpen}
      onOpenChange={(o) => {
        setPopoverOpen(o);
        if (!o) handleReset();
      }}
    >
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" disabled={disabled}>
          {disabled ? (
            <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
          ) : (
            <Plus className="h-3.5 w-3.5 mr-1.5" />
          )}
          Add
        </Button>
      </PopoverTrigger>
      <PopoverContent
        className="w-[220px] p-3"
        side="left"
        align="start"
        container={container}
      >
        <div className="space-y-3">
          <span className="text-sm font-medium">Document Category</span>
          {showCustomInput ? (
            <div className="flex gap-1.5">
              <Input
                className="h-9 text-sm flex-1"
                placeholder="Category name..."
                value={customCategory}
                onChange={(e) => setCustomCategory(e.target.value)}
                autoFocus
              />
              <Button
                variant="ghost"
                size="icon"
                className="h-9 w-9 shrink-0"
                onClick={() => {
                  setShowCustomInput(false);
                  setCustomCategory("");
                }}
              >
                <X className="h-3.5 w-3.5" />
              </Button>
            </div>
          ) : (
            <div className="space-y-1.5">
              <Select
                value={selectedCategory}
                onValueChange={setSelectedCategory}
              >
                <SelectTrigger className="h-9 text-sm">
                  <SelectValue placeholder="Select category..." />
                </SelectTrigger>
                <SelectContent>
                  {filteredCategories.map((cat) => (
                    <SelectItem key={cat.value} value={cat.value}>
                      {cat.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <button
                type="button"
                className="text-xs text-primary hover:underline cursor-pointer"
                onClick={() => {
                  setShowCustomInput(true);
                  setSelectedCategory("");
                }}
              >
                + New category
              </button>
            </div>
          )}
          <Button
            size="sm"
            className="w-full"
            disabled={!resolvedCategory}
            onClick={() => {
              const categoryValue = showCustomInput
                ? customCategory.trim().toLowerCase().replace(/\s+/g, "-")
                : selectedCategory;
              onAdd(categoryValue);
              setPopoverOpen(false);
              handleReset();
            }}
          >
            <Upload className="h-3.5 w-3.5 mr-1.5" />
            Choose File
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}

// --- Main Drawer ---

export function IncentiveDetailDrawer({
  open,
  onOpenChange,
  incentiveId,
  listIncentive,
  fallbackDocuments,
  isLockedJourneyStage,
}: IncentiveDetailDrawerProps) {
  const { can } = usePermissions();
  const isClientAdmin = can("module.manage_incentives");

  const { data: detailData, isLoading } = useIncentive(incentiveId ?? "");
  const uploadDocsMutation = useUploadIncentiveDocuments();
  const [canScrollDown, setCanScrollDown] = useState(false);
  const [loadingDocId, setLoadingDocId] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isDownloadingAll, setIsDownloadingAll] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const sheetRef = useRef<HTMLDivElement>(null);

  const incentive = detailData as IncentiveDetailResponse | undefined;

  // Defer the heavy drawer body to the frame *after* `open` flips so the
  // first paint after the click is just the spinner. Without this, when the
  // detail is already in the TanStack Query cache (true after the first ever
  // open of an incentive), the entire body — header, badges, budget
  // breakdown, audience rules, eligibility, journey stages, documents,
  // approvals — renders synchronously into the same first frame as the
  // Radix Sheet slide-in. On `/manage-incentives` the payload is much richer
  // than `/incentives` (more rules, more documents, full audit data), so
  // that synchronous body work shows up directly in INP. The one-frame
  // requestAnimationFrame defer keeps INP at the slide-in floor and pushes
  // the body paint to the next frame (~16 ms later), invisible to the user.
  const [bodyReady, setBodyReady] = useState(false);
  useEffect(() => {
    setBodyReady(false);
    if (!open) return;
    const raf = requestAnimationFrame(() => setBodyReady(true));
    return () => cancelAnimationFrame(raf);
  }, [open, incentiveId]);

  // Resolve fields: prefer detail API, fall back to list-level data
  const resolvedDocuments =
    incentive?.documents && incentive.documents.length > 0
      ? incentive.documents
      : (listIncentive?.documents ?? fallbackDocuments ?? []);

  // Prefer the sum of monetary entries on `incentive.budgets[]` (the canonical
  // multi-currency source — same approach `ManagedIncentiveCard` uses for its
  // hover popover). Fall back to the legacy singular `budget.totalBudget` /
  // `budgetTotal` rollups only when `budgets[]` is empty, so older payloads
  // keep rendering. (BUG-069)
  const monetaryBudgetsTotal = incentive?.budgets?.length
    ? incentive.budgets.reduce((sum, b) => {
        const n = parseFloat(b.totalBudget);
        if (isNaN(n)) return sum;
        return getCurrency(b.currencyId).type === "monetary" ? sum + n : sum;
      }, 0)
    : 0;
  const resolvedBudgetTotal =
    monetaryBudgetsTotal > 0
      ? String(monetaryBudgetsTotal)
      : (incentive?.budget?.totalBudget ??
        incentive?.budgetTotal ??
        listIncentive?.budgetTotal);
  const resolvedUtilPercent =
    incentive?.budgetUtilizationPercent ??
    listIncentive?.budgetUtilizationPercent ??
    0;

  // Scroll-down indicator. Each `checkScroll` reads `scrollHeight` /
  // `clientHeight` / `scrollTop`, which forces synchronous layout — and on a
  // page with ~7k DOM elements that's a ~80 ms forced reflow per call. The
  // previous implementation called `checkScroll` three times during the open
  // sequence (immediate + setTimeout 100ms + setTimeout 250ms) which Chrome
  // flagged as the top forced-reflow contributor for INP. A single
  // `ResizeObserver` on the scroll element catches the cases the timeouts
  // were guarding (content height settling after fonts / layout / drawer
  // open) without paying the layout cost up front.
  useEffect(() => {
    if (!open) return;

    let el: HTMLDivElement | null = null;
    let rafId = 0;
    let resizeObserver: ResizeObserver | null = null;

    const checkScroll = () => {
      const scrollElement = el ?? scrollRef.current;
      if (!scrollElement) return;
      const { scrollTop, scrollHeight, clientHeight } = scrollElement;
      const isScrollable = scrollHeight - clientHeight > 16;
      const atBottom = Math.ceil(scrollTop + clientHeight) >= scrollHeight - 8;
      setCanScrollDown(isScrollable && !atBottom);
    };

    const attachWhenReady = () => {
      el = scrollRef.current;
      if (!el) {
        rafId = requestAnimationFrame(attachWhenReady);
        return;
      }
      el.addEventListener("scroll", checkScroll, { passive: true });
      window.addEventListener("resize", checkScroll);
      // ResizeObserver fires when the scroll element's content box changes —
      // covers the "content height grows after async paint" case the old
      // setTimeout pair was trying to catch, but only when a real layout
      // change happens (not on every drawer open).
      resizeObserver = new ResizeObserver(checkScroll);
      resizeObserver.observe(el);
    };

    attachWhenReady();

    return () => {
      if (rafId) cancelAnimationFrame(rafId);
      if (resizeObserver) resizeObserver.disconnect();
      if (el) el.removeEventListener("scroll", checkScroll);
      window.removeEventListener("resize", checkScroll);
    };
  }, [open, incentive]);

  const scrollToBottom = () => {
    scrollRef.current?.scrollTo({
      top: scrollRef.current.scrollHeight,
      behavior: "smooth",
    });
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        ref={sheetRef}
        className="w-full sm:max-w-2xl flex flex-col p-0"
      >
        {isLoading || !incentive || !bodyReady ? (
          <div className="flex-1 flex items-center justify-center">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <>
            <div ref={scrollRef} className="flex-1 overflow-y-auto p-6">
              {/* Header */}
              <SheetHeader className="pb-6 pr-8">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1 min-w-0 space-y-3">
                    <div className="flex items-center gap-3">
                      <span
                        className={cn(
                          "shrink-0",
                          engagementColors[incentive.incentiveType],
                        )}
                      >
                        {engagementIcons[incentive.incentiveType]}
                      </span>
                      <SheetTitle className="text-xl text-foreground leading-tight">
                        {incentive.name}
                      </SheetTitle>
                    </div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <Badge
                        variant="outline"
                        className={cn(
                          "text-xs",
                          statusColors[incentive.status],
                        )}
                      >
                        {statusLabels[incentive.status]}
                      </Badge>
                      <Badge variant="outline" className="text-xs">
                        {typeLabels[incentive.incentiveType]}
                      </Badge>
                    </div>
                  </div>
                  {!isClientAdmin && <RewardDisplay incentive={incentive} />}
                </div>
              </SheetHeader>

              <div className="space-y-4">
                {/* Locked journey stage banner */}
                {isLockedJourneyStage && (
                  <div className="flex items-start gap-3 rounded-xl border border-warning/30 bg-warning/5 p-4">
                    <Lock className="h-5 w-5 text-warning shrink-0 mt-0.5" />
                    <div>
                      <p className="text-sm font-semibold text-foreground">
                        This step is locked
                      </p>
                      <p className="text-sm text-muted-foreground">
                        Complete the previous step to unlock actions. You can
                        still view documents.
                      </p>
                    </div>
                  </div>
                )}

                {/* Approval Status */}
                {incentive.approvalStatus && incentiveId && (
                  <ApprovalStatusSection
                    incentiveId={incentiveId}
                    approvalStatus={incentive.approvalStatus}
                  />
                )}

                {/* Description */}
                {incentive.description && (
                  <div className="rounded-xl border border-border p-4 space-y-2">
                    <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
                      Description
                    </h3>
                    <p className="text-sm text-foreground leading-relaxed">
                      {incentive.description}
                    </p>
                  </div>
                )}

                {/* Journey Stages */}
                {incentive.incentiveType === "JOURNEY" &&
                  listIncentive?.journeyStages &&
                  listIncentive.journeyStages.length > 0 && (
                    <JourneyStagesSection
                      stages={listIncentive.journeyStages}
                    />
                  )}

                {/* Activity Definitions */}
                {incentive.incentiveType === "ACTIVITY" &&
                  incentive.activityDefinitions &&
                  incentive.activityDefinitions.length > 0 && (
                    <ActivityDefinitionsList
                      definitions={incentive.activityDefinitions}
                    />
                  )}

                {/* Training Courses */}
                {incentive.incentiveType === "TRAINING" &&
                  incentive.trainingCourses &&
                  incentive.trainingCourses.length > 0 && (
                    <TrainingCoursesList courses={incentive.trainingCourses} />
                  )}

                {/* Duration + Created By */}
                {incentive.startDate && incentive.endDate && (
                  <div className="rounded-xl border border-border p-4 space-y-3">
                    <div className="space-y-2">
                      <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
                        Duration
                      </h3>
                      <div className="flex items-center gap-2 text-sm">
                        <Calendar className="h-4 w-4 text-muted-foreground" />
                        <span>
                          {formatDate(incentive.startDate)} -{" "}
                          {formatDate(incentive.endDate)}
                        </span>
                      </div>
                    </div>
                    {incentive.createdByName && (
                      <div className="space-y-2 pt-2 border-t border-border/50">
                        <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
                          Created By
                        </h3>
                        <div className="flex items-center gap-2 text-sm">
                          <User className="h-4 w-4 text-muted-foreground" />
                          <span>{incentive.createdByName}</span>
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {/* Budget — Client Admin only */}
                {isClientAdmin &&
                  (() => {
                    if (!resolvedBudgetTotal) return null;
                    const totalNum = parseFloat(resolvedBudgetTotal);
                    const utilPercent = resolvedUtilPercent;
                    const utilizedNum = totalNum * (utilPercent / 100);
                    return (
                      <div className="rounded-xl border border-border p-4 space-y-4">
                        <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
                          Budget
                        </h3>
                        <div className="space-y-2">
                          <div className="flex items-center justify-between text-sm">
                            <div className="flex items-center gap-1.5 text-muted-foreground">
                              <TrendingUp className="h-4 w-4" />
                              <span>Budget Utilization</span>
                            </div>
                            <span className="font-medium">{utilPercent}%</span>
                          </div>
                          <Progress value={utilPercent} className="h-2" />
                        </div>
                        <div className="grid gap-3">
                          <BudgetBreakdownSection
                            incentive={incentive}
                            label="Budget Utilized"
                            amount={utilizedNum}
                          />
                          <BudgetBreakdownSection
                            incentive={incentive}
                            label="Total Budget"
                            amount={totalNum}
                          />
                        </div>
                      </div>
                    );
                  })()}

                {/* Documents */}
                <div className="rounded-xl border border-border p-4 space-y-4">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
                      Documents ({resolvedDocuments.length})
                    </h3>
                    <div className="flex items-center gap-2">
                      {isClientAdmin && (
                        <AddDocumentPopover
                          incentiveType={incentive?.incentiveType}
                          disabled={isUploading}
                          container={sheetRef.current}
                          onAdd={(category) => {
                            const input = document.createElement("input");
                            input.type = "file";
                            input.accept = ".pdf,.xlsx,.xls,.docx,.doc";
                            input.multiple = true;
                            input.onchange = async (e) => {
                              const files = (e.target as HTMLInputElement)
                                .files;
                              if (!files || files.length === 0 || !incentiveId)
                                return;

                              const uploads = Array.from(files).map((file) => ({
                                file,
                                category,
                              }));

                              const validationError = validateFiles(uploads);
                              if (validationError) {
                                toast.error("Validation Error", {
                                  description: validationError,
                                });
                                return;
                              }

                              setIsUploading(true);
                              try {
                                await uploadDocsMutation.mutateAsync({
                                  incentiveId,
                                  files: uploads,
                                });
                                toast.success("Document Uploaded", {
                                  description: `${files.length} document${files.length > 1 ? "s" : ""} added successfully.`,
                                });
                              } catch (err) {
                                const msg =
                                  err instanceof Error
                                    ? err.message
                                    : "Upload failed";
                                toast.error("Upload Failed", {
                                  description: msg,
                                });
                              } finally {
                                setIsUploading(false);
                              }
                            };
                            input.click();
                          }}
                        />
                      )}
                      {resolvedDocuments.length > 0 && (
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={isDownloadingAll}
                          onClick={async () => {
                            if (!incentiveId || isDownloadingAll) return;
                            setIsDownloadingAll(true);
                            toast.info("Downloading All", {
                              description: `Downloading ${resolvedDocuments.length} documents...`,
                            });
                            try {
                              for (const doc of resolvedDocuments) {
                                if (doc.downloadUrl) {
                                  await downloadDocument(
                                    incentiveId,
                                    doc.id,
                                    doc.name,
                                  );
                                }
                              }
                            } catch (err) {
                              const msg =
                                err instanceof Error
                                  ? err.message
                                  : "Download failed";
                              toast.error("Download Failed", {
                                description: msg,
                              });
                            } finally {
                              setIsDownloadingAll(false);
                            }
                          }}
                        >
                          {isDownloadingAll ? (
                            <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" />
                          ) : (
                            <Download className="h-3.5 w-3.5 mr-1.5" />
                          )}
                          Download All
                        </Button>
                      )}
                    </div>
                  </div>
                  {resolvedDocuments.length > 0 ? (
                    <div className="space-y-2">
                      {resolvedDocuments.map((doc) => (
                        <DocumentRow
                          key={doc.id}
                          doc={doc}
                          isLoading={loadingDocId === doc.id}
                          isDisabled={!doc.downloadUrl || !!loadingDocId}
                          onView={async (d) => {
                            if (!d.downloadUrl || !incentiveId || loadingDocId)
                              return;
                            setLoadingDocId(d.id);
                            try {
                              await viewDocument(incentiveId, d.id);
                            } finally {
                              setLoadingDocId(null);
                            }
                          }}
                          onDownload={async (d) => {
                            if (!d.downloadUrl || !incentiveId || loadingDocId)
                              return;
                            setLoadingDocId(d.id);
                            try {
                              await downloadDocument(incentiveId, d.id, d.name);
                            } finally {
                              setLoadingDocId(null);
                            }
                          }}
                        />
                      ))}
                    </div>
                  ) : (
                    <div className="flex flex-col items-center justify-center py-6 text-muted-foreground">
                      <FileText className="h-8 w-8 mb-2 opacity-50" />
                      <p className="text-sm">No documents attached</p>
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* Scroll indicator */}
            {canScrollDown && (
              <button
                onClick={scrollToBottom}
                className="absolute bottom-4 left-1/2 -translate-x-1/2 flex flex-col items-center gap-1 text-primary animate-bounce cursor-pointer"
                aria-label="Scroll down for more"
              >
                <span className="text-xs font-medium text-muted-foreground">
                  Scroll for more
                </span>
                <div className="bg-primary/10 border border-primary/30 rounded-full p-1.5">
                  <ChevronDown className="h-4 w-4" />
                </div>
              </button>
            )}
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
