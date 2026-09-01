import { useState, useMemo, useRef, useCallback, memo } from "react";
import { usePermissions } from "@/hooks/usePermissions";
import { useFeatures } from "@/hooks/useFeatures";
import {
  Search,
  Megaphone,
  GraduationCap,
  FileCheck,
  Layers,
  Route,
  BookOpen,
  ArrowUpDown,
  Eye,
  EyeOff,
} from "lucide-react";
import { PageBanner } from "@/components/PageBanner";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { PartnerIncentiveCard } from "@/components/view-incentives/PartnerIncentiveCard";
import { IncentiveGridSkeleton } from "@/components/skeletons/IncentiveGridSkeleton";
import { JourneyIncentiveCard } from "@/components/journey/JourneyIncentiveCard";
import { IncentiveDetailDrawer } from "@/components/view-incentives/IncentiveDetailDrawer";
import { useIncentives } from "@/hooks/useIncentiveApi";
import { cn } from "@/lib/utils";
import type { IncentiveType, IncentiveResponse } from "@/types/incentive.types";

// --- Type/color/icon maps (matching ManageIncentivesPage) ---

type DisplayType = IncentiveType | "ENABLEMENT";

const engagementIcons: Record<DisplayType, React.ReactNode> = {
  JOURNEY: <Layers className="h-5 w-5" />,
  SALES: <Megaphone className="h-5 w-5" />,
  TRAINING: <GraduationCap className="h-5 w-5" />,
  ACTIVITY: <FileCheck className="h-5 w-5" />,
  ENABLEMENT: <BookOpen className="h-5 w-5" />,
};

const engagementColors: Record<DisplayType, string> = {
  JOURNEY: "text-indigo-500",
  SALES: "text-[hsl(217_91%_55%)]",
  TRAINING: "text-amber-500",
  ACTIVITY: "text-blue-500",
  ENABLEMENT: "text-emerald-500",
};

const engagementGradients: Record<DisplayType, string> = {
  SALES:
    "from-[hsl(217_91%_60%/0.08)] via-[hsl(217_91%_60%/0.04)] to-transparent border-[hsl(217_91%_60%/0.15)]",
  TRAINING:
    "from-[hsl(38_90%_50%/0.08)] via-[hsl(38_90%_50%/0.04)] to-transparent border-[hsl(38_90%_50%/0.15)]",
  ACTIVITY:
    "from-[hsl(217_80%_55%/0.08)] via-[hsl(217_80%_55%/0.04)] to-transparent border-[hsl(217_80%_55%/0.15)]",
  JOURNEY:
    "from-[hsl(245_58%_55%/0.08)] via-[hsl(245_58%_55%/0.04)] to-transparent border-[hsl(245_58%_55%/0.15)]",
  ENABLEMENT:
    "from-[hsl(160_55%_42%/0.08)] via-[hsl(160_55%_42%/0.04)] to-transparent border-[hsl(160_55%_42%/0.15)]",
};

const engagementIconBg: Record<DisplayType, string> = {
  SALES: "bg-[hsl(217_91%_60%/0.1)]",
  TRAINING: "bg-[hsl(38_90%_50%/0.1)]",
  ACTIVITY: "bg-[hsl(217_80%_55%/0.1)]",
  JOURNEY: "bg-[hsl(245_58%_55%/0.1)]",
  ENABLEMENT: "bg-[hsl(160_55%_42%/0.1)]",
};

const engagementCountBg: Record<DisplayType, string> = {
  SALES: "bg-[hsl(217_91%_60%/0.1)] text-[hsl(217_91%_50%)]",
  TRAINING: "bg-[hsl(38_90%_50%/0.1)] text-[hsl(38_80%_40%)]",
  ACTIVITY: "bg-[hsl(217_80%_55%/0.1)] text-[hsl(217_80%_45%)]",
  JOURNEY: "bg-[hsl(245_58%_55%/0.1)] text-[hsl(245_58%_45%)]",
  ENABLEMENT: "bg-[hsl(160_55%_42%/0.1)] text-[hsl(160_55%_35%)]",
};

const sectionLabels: Record<DisplayType, string> = {
  SALES: "Sales Incentives",
  TRAINING: "Training Incentives",
  ACTIVITY: "Activity Incentives",
  JOURNEY: "Journeys",
  ENABLEMENT: "Enablement Incentives",
};

const enablementSubTypes: IncentiveType[] = ["TRAINING", "ACTIVITY"];

type SortOption = "name-asc" | "name-desc" | "end-date-asc" | "end-date-desc";

// --- Tab definition (matching ManageIncentivesPage style) ---

interface TabDef {
  id: string;
  label: string;
  icon: React.ReactNode;
}

const tabs: TabDef[] = [
  { id: "sales", label: "Sales", icon: <Megaphone className="h-3.5 w-3.5" /> },
  {
    id: "enablement",
    label: "Enablement",
    icon: <BookOpen className="h-3.5 w-3.5" />,
  },
  {
    id: "journeys",
    label: "Journeys",
    icon: <Route className="h-3.5 w-3.5" />,
  },
];

// --- PartnerGridSection ---

interface PartnerGridSectionProps {
  type: DisplayType;
  incentives: IncentiveResponse[];
  showCompletedToggle?: boolean;
  showCompleted?: boolean;
  completedCount?: number;
  onToggleCompleted?: () => void;
  onCardClick?: (incentive: IncentiveResponse) => void;
  onTakeCourses?: (incentive: IncentiveResponse) => void;
  onTakeAction?: (incentive: IncentiveResponse) => void;
}

export const PartnerGridSection = memo(function PartnerGridSection({
  type,
  incentives,
  showCompletedToggle,
  showCompleted,
  completedCount = 0,
  onToggleCompleted,
  onCardClick,
  onTakeCourses,
  onTakeAction,
}: PartnerGridSectionProps) {
  const activeCount = incentives.filter((i) => i.userCompleted !== true).length;

  return (
    <section className="flex flex-col flex-1 min-h-0">
      {/* Gradient banner title strip — matching Manage Incentives */}
      <div
        className={cn(
          "flex items-center justify-between px-5 py-3.5 rounded-xl border bg-gradient-to-r mb-4 shrink-0",
          engagementGradients[type],
        )}
      >
        <div className="flex items-center gap-3">
          <div
            className={cn(
              "flex items-center justify-center h-8 w-8 rounded-lg",
              engagementIconBg[type],
            )}
          >
            <span className={engagementColors[type]}>
              {engagementIcons[type]}
            </span>
          </div>
          <h2 className="text-xl font-semibold text-foreground tracking-tight">
            {sectionLabels[type]}
          </h2>
          <span
            className={cn(
              "text-xs font-semibold tabular-nums px-2 py-0.5 rounded-full",
              engagementCountBg[type],
            )}
          >
            {activeCount}
            {showCompleted && completedCount > 0 ? ` + ${completedCount}` : ""}
          </span>
        </div>

        {showCompletedToggle && completedCount > 0 && (
          <Button
            variant={showCompleted ? "secondary" : "ghost"}
            size="sm"
            className="h-8 gap-1.5 text-xs"
            onClick={onToggleCompleted}
          >
            {showCompleted ? (
              <EyeOff className="h-3.5 w-3.5" />
            ) : (
              <Eye className="h-3.5 w-3.5" />
            )}
            {showCompleted
              ? "Hide Completed"
              : `Show Completed (${completedCount})`}
          </Button>
        )}
      </div>

      {/* Grid */}
      <div
        className="overflow-y-auto flex-1"
        style={{ scrollbarWidth: "thin" }}
      >
        <div className="grid grid-cols-2 gap-4">
          {incentives.map((incentive) => (
            <div key={incentive.id}>
              <PartnerIncentiveCard
                incentive={incentive}
                onClick={() => onCardClick?.(incentive)}
                onTakeCourses={onTakeCourses}
                onTakeAction={onTakeAction}
              />
            </div>
          ))}
        </div>
      </div>
    </section>
  );
});

// --- EmptySection ---

function EmptySection({ type }: { type: DisplayType }) {
  return (
    <section className="flex flex-col flex-1 min-h-0">
      {/* Gradient banner title strip */}
      <div
        className={cn(
          "flex items-center justify-between px-5 py-3.5 rounded-xl border bg-gradient-to-r mb-4 shrink-0",
          engagementGradients[type],
        )}
      >
        <div className="flex items-center gap-3">
          <div
            className={cn(
              "flex items-center justify-center h-8 w-8 rounded-lg",
              engagementIconBg[type],
            )}
          >
            <span className={engagementColors[type]}>
              {engagementIcons[type]}
            </span>
          </div>
          <h2 className="text-xl font-semibold text-foreground tracking-tight">
            {sectionLabels[type]}
          </h2>
          <span
            className={cn(
              "text-xs font-semibold tabular-nums px-2 py-0.5 rounded-full",
              engagementCountBg[type],
            )}
          >
            0
          </span>
        </div>
      </div>

      {/* Empty state */}
      <div className="flex flex-col items-center justify-center py-20 rounded-xl border border-dashed border-border">
        <div className={cn("mb-3", engagementColors[type])}>
          {engagementIcons[type]}
        </div>
        <p className="text-sm text-muted-foreground">
          No {sectionLabels[type].toLowerCase()} available
        </p>
      </div>
    </section>
  );
}

// --- Journey Partner Section ---

interface JourneyPartnerSectionProps {
  incentives: IncentiveResponse[];
  allIncentives: IncentiveResponse[];
  onCardClick?: (incentive: IncentiveResponse, isLocked?: boolean) => void;
  onTakeCourses?: (incentive: IncentiveResponse) => void;
  onTakeAction?: (incentive: IncentiveResponse) => void;
}

export const JourneyPartnerSection = memo(function JourneyPartnerSection({
  incentives,
  allIncentives,
  onCardClick,
  onTakeCourses,
  onTakeAction,
}: JourneyPartnerSectionProps) {
  return (
    <section className="flex flex-col flex-1 min-h-0">
      {/* Gradient banner title strip */}
      <div
        className={cn(
          "flex items-center justify-between px-5 py-3.5 rounded-xl border bg-gradient-to-r mb-4 shrink-0",
          engagementGradients.JOURNEY,
        )}
      >
        <div className="flex items-center gap-3">
          <div
            className={cn(
              "flex items-center justify-center h-8 w-8 rounded-lg",
              engagementIconBg.JOURNEY,
            )}
          >
            <span className={engagementColors.JOURNEY}>
              {engagementIcons.JOURNEY}
            </span>
          </div>
          <h2 className="text-xl font-semibold text-foreground tracking-tight">
            {sectionLabels.JOURNEY}
          </h2>
          <span
            className={cn(
              "text-xs font-semibold tabular-nums px-2 py-0.5 rounded-full",
              engagementCountBg.JOURNEY,
            )}
          >
            {incentives.length}
          </span>
        </div>
      </div>

      {/* Journey cards — stacked vertically, full width */}
      <div
        className="overflow-y-auto flex-1 space-y-4"
        style={{ scrollbarWidth: "thin" }}
      >
        {incentives.map((incentive) => (
          <JourneyIncentiveCard
            key={incentive.id}
            incentive={incentive}
            variant="view"
            allIncentives={allIncentives}
            onClick={() => onCardClick?.(incentive)}
            onStageClick={(childIncentive, isLocked) =>
              onCardClick?.(childIncentive, isLocked)
            }
            onAction={() => {
              if (incentive.incentiveType === "JOURNEY") {
                // Find the active stage and dispatch appropriate action
                const stages = incentive.journeyStages ?? [];
                const activeStage = stages.find(
                  (s) => s.userCompleted !== true,
                );
                if (
                  activeStage?.incentiveType === "TRAINING" &&
                  onTakeCourses
                ) {
                  onTakeCourses(incentive);
                } else if (
                  activeStage?.incentiveType === "ACTIVITY" &&
                  onTakeAction
                ) {
                  onTakeAction(incentive);
                } else {
                  onCardClick?.(incentive);
                }
              }
            }}
          />
        ))}
      </div>
    </section>
  );
});

// --- Main Page ---

function ViewIncentivesPage() {
  const { can } = usePermissions();
  const { has } = useFeatures();
  const [searchQuery, setSearchQuery] = useState("");
  const [sortOption, setSortOption] = useState<SortOption>("name-asc");
  const [showCompleted, setShowCompleted] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedIncentiveId, setSelectedIncentiveId] = useState<string | null>(
    null,
  );
  const [isLockedJourneyStage, setIsLockedJourneyStage] = useState(false);
  const contentRef = useRef<HTMLDivElement>(null);

  const permittedTabs = useMemo(() => {
    const tabPermissions: Record<string, string> = {
      sales: "module.incentives.sales",
      enablement: "module.incentives.enablement",
      journeys: "module.incentives.journeys",
    };
    // Tabs additionally gated by a tier-level feature flag are listed here.
    // The journeys tab is hidden when the tenant's tier doesn't include
    // journey_incentives, regardless of permission grant.
    const tabFeatures: Record<string, string> = {
      journeys: "journey_incentives",
    };
    return tabs.filter((tab) => {
      const perm = tabPermissions[tab.id];
      if (!perm || !can(perm)) return false;
      const feature = tabFeatures[tab.id];
      if (feature && !has(feature)) return false;
      return true;
    });
  }, [can, has]);

  const [activeTab, setActiveTab] = useState(permittedTabs[0]?.id ?? "sales");

  const handleCardClick = useCallback(
    (incentive: IncentiveResponse, locked = false) => {
      setSelectedIncentiveId(incentive.id);
      setIsLockedJourneyStage(locked);
      setDrawerOpen(true);
    },
    [],
  );

  const handleTakeCourses = useCallback((incentive: IncentiveResponse) => {
    setSelectedIncentiveId(incentive.id);
    setDrawerOpen(true);
  }, []);

  const handleTakeAction = useCallback((incentive: IncentiveResponse) => {
    setSelectedIncentiveId(incentive.id);
    setDrawerOpen(true);
  }, []);

  const handleToggleCompleted = useCallback(
    () => setShowCompleted((prev) => !prev),
    [],
  );

  const handleTabChange = (tab: string) => {
    setActiveTab(tab);
    const el = contentRef.current;
    if (el && !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      el.classList.remove("animate-route-in");
      void el.offsetWidth;
      el.classList.add("animate-route-in");
    }
  };

  const { data: apiData, isLoading } = useIncentives({ pageSize: 100 });

  const allIncentives: IncentiveResponse[] = useMemo(() => {
    return apiData ? apiData.data : [];
  }, [apiData]);

  // Only show ACTIVE to partners
  const partnerIncentives = useMemo(() => {
    return allIncentives.filter((inc) => inc.status === "ACTIVE");
  }, [allIncentives]);

  const filteredIncentives = useMemo(() => {
    return partnerIncentives
      .filter((inc) => {
        const matchesSearch =
          !searchQuery ||
          inc.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
          (inc.description ?? "")
            .toLowerCase()
            .includes(searchQuery.toLowerCase());
        return matchesSearch;
      })
      .sort((a, b) => {
        switch (sortOption) {
          case "name-asc":
            return a.name.localeCompare(b.name);
          case "name-desc":
            return b.name.localeCompare(a.name);
          case "end-date-asc": {
            const aDate = a.endDate ? new Date(a.endDate).getTime() : Infinity;
            const bDate = b.endDate ? new Date(b.endDate).getTime() : Infinity;
            return aDate - bDate;
          }
          case "end-date-desc": {
            const aDate = a.endDate ? new Date(a.endDate).getTime() : 0;
            const bDate = b.endDate ? new Date(b.endDate).getTime() : 0;
            return bDate - aDate;
          }
          default:
            return 0;
        }
      });
  }, [partnerIncentives, searchQuery, sortOption]);

  // Sales: only active
  const salesActive = useMemo(
    () =>
      filteredIncentives.filter(
        (inc) => inc.incentiveType === "SALES" && inc.status === "ACTIVE",
      ),
    [filteredIncentives],
  );

  // Enablement: active + optionally completed
  const enablementAll = useMemo(
    () =>
      filteredIncentives.filter((inc) =>
        enablementSubTypes.includes(inc.incentiveType),
      ),
    [filteredIncentives],
  );
  const enablementActive = useMemo(
    () => enablementAll.filter((inc) => inc.userCompleted !== true),
    [enablementAll],
  );
  const enablementCompleted = useMemo(
    () => enablementAll.filter((inc) => inc.userCompleted === true),
    [enablementAll],
  );
  const enablementDisplay = useMemo(
    () =>
      showCompleted
        ? [...enablementActive, ...enablementCompleted]
        : enablementActive,
    [showCompleted, enablementActive, enablementCompleted],
  );

  // Journeys
  const journeysAll = useMemo(
    () => filteredIncentives.filter((inc) => inc.incentiveType === "JOURNEY"),
    [filteredIncentives],
  );
  const journeysActive = useMemo(
    () => journeysAll.filter((inc) => inc.status === "ACTIVE"),
    [journeysAll],
  );

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="shrink-0 mb-6">
        <PageBanner
          theme="view-incentives"
          title="View Incentives"
          subtitle="Browse available incentive programs"
        />

        {/* Filters row — tab pills left, search + sort right */}
        <div className="flex items-center justify-between mt-5">
          {/* Tab pills (left side) */}
          <div className="flex items-center gap-1">
            {permittedTabs.map((tab) => (
              <button
                key={tab.id}
                onClick={() => handleTabChange(tab.id)}
                className={cn(
                  "inline-flex items-center gap-1.5 h-9 px-3.5 rounded-lg text-sm font-medium transition-[background-color,color,box-shadow] duration-150",
                  activeTab === tab.id
                    ? "bg-primary text-primary-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground hover:bg-muted",
                )}
                data-tour={`tab-${tab.id}`}
              >
                {tab.icon}
                {tab.label}
              </button>
            ))}
          </div>

          {/* Search + Sort (right side) */}
          <div className="flex items-center gap-3">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              <input
                placeholder="Search incentives..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="h-9 w-[220px] rounded-lg border border-input bg-background pl-9 pr-3 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/40 transition-[border-color,box-shadow]"
              />
            </div>

            <div className="w-px h-5 bg-border" />

            <Select
              value={sortOption}
              onValueChange={(v) => setSortOption(v as SortOption)}
            >
              <SelectTrigger className="w-[180px] h-9 rounded-lg text-sm text-muted-foreground">
                <ArrowUpDown className="h-3.5 w-3.5 mr-2 shrink-0" />
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="name-asc">Name (A→Z)</SelectItem>
                <SelectItem value="name-desc">Name (Z→A)</SelectItem>
                <SelectItem value="end-date-asc">End Date (Soonest)</SelectItem>
                <SelectItem value="end-date-desc">End Date (Latest)</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>

      {/* Content */}
      <div ref={contentRef} className="flex-1 min-h-0 flex flex-col">
        {isLoading ? (
          <IncentiveGridSkeleton bannerActionWidth="w-32" />
        ) : activeTab === "sales" ? (
          <div
            data-tour="sales-section"
            data-state={activeTab === "sales" ? "active" : "inactive"}
            className="flex-1 min-h-0 flex flex-col"
          >
            {salesActive.length === 0 ? (
              <EmptySection type="SALES" />
            ) : (
              <PartnerGridSection
                type="SALES"
                incentives={salesActive}
                onCardClick={handleCardClick}
                onTakeCourses={handleTakeCourses}
                onTakeAction={handleTakeAction}
              />
            )}
          </div>
        ) : activeTab === "enablement" ? (
          <div
            data-tour="enablement-section"
            data-state={activeTab === "enablement" ? "active" : "inactive"}
            className="flex-1 min-h-0 flex flex-col"
          >
            {enablementDisplay.length === 0 &&
            enablementCompleted.length === 0 ? (
              <EmptySection type="ENABLEMENT" />
            ) : enablementDisplay.length === 0 ? (
              <section className="flex flex-col flex-1 min-h-0">
                <div
                  className={cn(
                    "flex items-center justify-between px-5 py-3.5 rounded-xl border bg-gradient-to-r mb-4 shrink-0",
                    engagementGradients.ENABLEMENT,
                  )}
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={cn(
                        "flex items-center justify-center h-8 w-8 rounded-lg",
                        engagementIconBg.ENABLEMENT,
                      )}
                    >
                      <span className={engagementColors.ENABLEMENT}>
                        {engagementIcons.ENABLEMENT}
                      </span>
                    </div>
                    <h2 className="text-xl font-semibold text-foreground tracking-tight">
                      {sectionLabels.ENABLEMENT}
                    </h2>
                    <span
                      className={cn(
                        "text-xs font-semibold tabular-nums px-2 py-0.5 rounded-full",
                        engagementCountBg.ENABLEMENT,
                      )}
                    >
                      0
                    </span>
                  </div>
                  {enablementCompleted.length > 0 && (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-8 gap-1.5 text-xs"
                      onClick={() => setShowCompleted((prev) => !prev)}
                    >
                      <Eye className="h-3.5 w-3.5" />
                      Show Completed ({enablementCompleted.length})
                    </Button>
                  )}
                </div>
              </section>
            ) : (
              <PartnerGridSection
                type="ENABLEMENT"
                incentives={enablementDisplay}
                showCompletedToggle
                showCompleted={showCompleted}
                completedCount={enablementCompleted.length}
                onToggleCompleted={handleToggleCompleted}
                onCardClick={handleCardClick}
                onTakeCourses={handleTakeCourses}
                onTakeAction={handleTakeAction}
              />
            )}
          </div>
        ) : activeTab === "journeys" ? (
          <div
            data-tour="journey-section"
            data-state={activeTab === "journeys" ? "active" : "inactive"}
            className="flex-1 min-h-0 flex flex-col"
          >
            {journeysActive.length === 0 ? (
              <EmptySection type="JOURNEY" />
            ) : (
              <JourneyPartnerSection
                incentives={journeysActive}
                allIncentives={allIncentives}
                onCardClick={handleCardClick}
                onTakeCourses={handleTakeCourses}
                onTakeAction={handleTakeAction}
              />
            )}
          </div>
        ) : null}
      </div>

      <IncentiveDetailDrawer
        open={drawerOpen}
        onOpenChange={setDrawerOpen}
        incentiveId={selectedIncentiveId}
        listIncentive={allIncentives.find((i) => i.id === selectedIncentiveId)}
        isLockedJourneyStage={isLockedJourneyStage}
      />
    </div>
  );
}

export default ViewIncentivesPage;
