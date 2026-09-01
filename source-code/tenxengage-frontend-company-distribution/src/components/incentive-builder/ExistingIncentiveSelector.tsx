import { useState, useMemo } from "react";
import { FeatureGate } from "@/components/FeatureGate";
import {
  ArrowLeft,
  ArrowRight,
  Search,
  Download,
  Loader2,
  Copy,
  FileText,
} from "lucide-react";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useIncentives } from "@/hooks/useIncentiveApi";
import {
  INCENTIVE_TYPE_LABELS,
  INCENTIVE_STATUS_LABELS,
} from "@/types/incentive.types";
import type {
  IncentiveResponse,
  IncentiveType,
  IncentiveStatus,
} from "@/types/incentive.types";
import { getIncentiveById } from "@/services/incentive.service";
import { exportIncentiveToExcel } from "@/utils/excelExporter";
import { cn } from "@/lib/utils";

/* ── Left-border accent + hover colour wash per incentive type ─────────── */

const cardAccent: Record<
  IncentiveType,
  {
    borderColor: string;
    washGradient: string;
    hoverBorder: string;
    hoverShadow: string;
    tagBg: string;
    tagText: string;
  }
> = {
  SALES: {
    borderColor: "hsl(217 91% 60%)",
    washGradient:
      "linear-gradient(to right, hsl(217 91% 60% / 0.08) 0%, hsl(217 91% 60% / 0.03) 40%, transparent 80%)",
    hoverBorder: "hover:border-[hsl(217_91%_60%/0.30)]",
    hoverShadow: "hover:shadow-[0_4px_20px_hsl(217_60%_55%/0.08)]",
    tagBg: "bg-[hsl(217_91%_60%/0.08)]",
    tagText: "text-[hsl(217_91%_48%)]",
  },
  TRAINING: {
    borderColor: "hsl(38 92% 50%)",
    washGradient:
      "linear-gradient(to right, hsl(38 92% 50% / 0.08) 0%, hsl(38 92% 50% / 0.03) 40%, transparent 80%)",
    hoverBorder: "hover:border-[hsl(38_80%_50%/0.30)]",
    hoverShadow: "hover:shadow-[0_4px_20px_hsl(38_60%_50%/0.08)]",
    tagBg: "bg-[hsl(38_80%_50%/0.08)]",
    tagText: "text-[hsl(38_80%_38%)]",
  },
  ACTIVITY: {
    borderColor: "hsl(200 80% 50%)",
    washGradient:
      "linear-gradient(to right, hsl(200 80% 50% / 0.08) 0%, hsl(200 80% 50% / 0.03) 40%, transparent 80%)",
    hoverBorder: "hover:border-[hsl(200_80%_50%/0.30)]",
    hoverShadow: "hover:shadow-[0_4px_20px_hsl(200_60%_50%/0.08)]",
    tagBg: "bg-[hsl(200_80%_50%/0.08)]",
    tagText: "text-[hsl(200_80%_38%)]",
  },
  JOURNEY: {
    borderColor: "hsl(245 58% 58%)",
    washGradient:
      "linear-gradient(to right, hsl(245 58% 58% / 0.08) 0%, hsl(245 58% 58% / 0.03) 40%, transparent 80%)",
    hoverBorder: "hover:border-[hsl(245_58%_58%/0.30)]",
    hoverShadow: "hover:shadow-[0_4px_20px_hsl(245_45%_50%/0.08)]",
    tagBg: "bg-[hsl(245_58%_58%/0.08)]",
    tagText: "text-[hsl(245_58%_48%)]",
  },
};

interface ExistingIncentiveSelectorProps {
  onSelect: (incentive: IncentiveResponse) => void;
  onBack: () => void;
}

const STATUS_COLORS: Record<IncentiveStatus, string> = {
  DRAFT: "bg-muted text-muted-foreground",
  PENDING_APPROVAL: "bg-warning/10 text-warning",
  DENIED: "bg-destructive/10 text-destructive",
  ACTIVE: "bg-success/10 text-success",
  INACTIVE: "bg-muted text-muted-foreground",
};

function formatBudget(total: string | undefined): string {
  if (!total) return "";
  const num = parseFloat(total);
  if (num >= 1000) return `$${Math.round(num / 1000)}K`;
  return `$${num}`;
}

function formatDate(iso: string | undefined): string {
  if (!iso) return "";
  return iso.slice(0, 10);
}

export function ExistingIncentiveSelector({
  onSelect,
  onBack,
}: ExistingIncentiveSelectorProps) {
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState<
    IncentiveType | "ALL" | "ENABLEMENT"
  >("ALL");

  const apiTypeParam =
    typeFilter === "ALL" || typeFilter === "ENABLEMENT"
      ? undefined
      : typeFilter;

  const apiParams = {
    type: apiTypeParam,
    search: search || undefined,
    pageSize: 50,
  };
  const { data: apiData, isLoading } = useIncentives(apiParams);

  const incentives = useMemo(() => {
    const source = apiData ? apiData.data : [];
    return source.filter((inc) => {
      if (inc.status === "INACTIVE" || inc.status === "DENIED") return false;
      if (typeFilter === "ENABLEMENT") {
        if (
          inc.incentiveType !== "TRAINING" &&
          inc.incentiveType !== "ACTIVITY"
        )
          return false;
      } else if (typeFilter !== "ALL") {
        if (inc.incentiveType !== typeFilter) return false;
      }
      if (
        search &&
        !inc.name.toLowerCase().includes(search.toLowerCase()) &&
        !(inc.description ?? "").toLowerCase().includes(search.toLowerCase())
      )
        return false;
      return true;
    });
  }, [apiData, search, typeFilter]);

  const FILTER_LABELS: Record<string, string> = {
    ALL: "All Incentives",
    SALES: "Sales Incentives",
    ENABLEMENT: "Enablement Incentives",
    JOURNEY: "Journey Incentives",
  };

  const typeFilterLabel = FILTER_LABELS[typeFilter] ?? "All Incentives";

  return (
    <div className="relative flex flex-col h-[calc(100vh-64px)] overflow-hidden">
      {/* Pinned header area */}
      <div className="relative z-10 shrink-0 max-w-5xl mx-auto w-full pt-12">
        {/* Top bar: back */}
        <div className="flex items-center mb-4">
          <button
            type="button"
            onClick={onBack}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            <span>Back</span>
          </button>
        </div>

        {/* Hero heading */}
        <div className="text-center mb-10">
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full border border-[hsl(160_60%_40%/0.15)] bg-[hsl(160_60%_40%/0.04)] mb-6">
            <Copy className="h-3.5 w-3.5 text-[hsl(160_60%_38%)]" />
            <span className="text-xs font-medium text-[hsl(160_60%_32%)]">
              Create From Existing
            </span>
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">
            Choose an Existing Incentive
          </h1>
          <p className="text-base text-muted-foreground mt-3 mx-auto">
            Select an incentive to use as a starting point for your new program
          </p>
        </div>

        {/* Search + Filter */}
        <div className="flex gap-3 mb-6">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
            <Input
              placeholder="Search incentives..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9 h-9 text-sm border-border bg-background/60 focus-visible:ring-[hsl(160_60%_40%/0.3)]"
            />
          </div>
          <Select
            value={typeFilter}
            onValueChange={(v) => setTypeFilter(v as IncentiveType | "ALL")}
          >
            <SelectTrigger className="w-[200px] h-9 text-sm border-border bg-background/60">
              <SelectValue>{typeFilterLabel}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Incentives</SelectItem>
              <SelectItem value="SALES">Sales Incentives</SelectItem>
              <SelectItem value="ENABLEMENT">Enablement Incentives</SelectItem>
              <FeatureGate feature="journey_incentives">
                <SelectItem value="JOURNEY">Journey Incentives</SelectItem>
              </FeatureGate>
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* Scrollable card area */}
      <div
        className="relative z-10 flex-1 min-h-0 overflow-y-auto max-w-5xl mx-auto w-full"
        style={{ scrollbarWidth: "thin" }}
      >
        <div className="pb-12">
          {isLoading ? (
            <ExistingIncentiveCardGridSkeleton />
          ) : incentives.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20">
              <div className="flex items-center justify-center w-14 h-14 rounded-2xl bg-background/70 backdrop-blur-sm border border-border mb-4">
                <FileText className="h-6 w-6 text-muted-foreground" />
              </div>
              <p className="text-sm text-muted-foreground mb-1">
                No incentives found
              </p>
              <p className="text-xs text-muted-foreground">
                Try adjusting your search or filter
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {incentives.map((inc) => (
                <IncentiveCard
                  key={inc.id}
                  incentive={inc}
                  onClick={() => onSelect(inc)}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function IncentiveCard({
  incentive,
  onClick,
}: {
  incentive: IncentiveResponse;
  onClick: () => void;
}) {
  const [isExporting, setIsExporting] = useState(false);

  const typeLabel =
    incentive.incentiveType === "TRAINING" ||
    incentive.incentiveType === "ACTIVITY"
      ? incentive.incentiveType.charAt(0) +
        incentive.incentiveType.slice(1).toLowerCase()
      : INCENTIVE_TYPE_LABELS[incentive.incentiveType].replace(
          " Incentive",
          "",
        );

  const handleDownload = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (isExporting) return;
    setIsExporting(true);
    try {
      const detail = await getIncentiveById(incentive.id);
      await exportIncentiveToExcel(detail);
    } catch (err) {
      console.error("Failed to export incentive:", err);
    } finally {
      setIsExporting(false);
    }
  };

  const accent = cardAccent[incentive.incentiveType];

  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "relative rounded-2xl border border-border bg-background/70 backdrop-blur-sm p-5 text-left transition-[transform,border-color,box-shadow] duration-300 cursor-pointer group overflow-hidden hover:-translate-y-0.5",
        accent.hoverBorder,
        accent.hoverShadow,
      )}
    >
      {/* Left accent bar — fades on hover as wash takes over */}
      <div
        className="absolute left-0 top-0 bottom-0 w-[3px] rounded-l-2xl transition-opacity duration-300 group-hover:opacity-0"
        style={{ background: accent.borderColor }}
      />
      {/* Colour wash — fades in from left on hover */}
      <div
        className="absolute inset-0 rounded-2xl opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none"
        style={{ background: accent.washGradient }}
      />

      {/* Top row: name + type badge */}
      <div className="relative z-10 flex items-start justify-between gap-3 mb-1.5">
        <h3 className="font-semibold text-sm text-foreground leading-tight">
          {incentive.name}
        </h3>
        <div className="flex items-center gap-2 flex-shrink-0">
          {isExporting ? (
            <Loader2 className="h-3.5 w-3.5 text-muted-foreground animate-spin" />
          ) : (
            <button
              type="button"
              onClick={handleDownload}
              className="p-1 rounded hover:bg-muted transition-colors"
            >
              <Download className="h-3.5 w-3.5 text-muted-foreground hover:text-primary" />
            </button>
          )}
          <span
            className={cn(
              "inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium",
              accent.tagBg,
              accent.tagText,
            )}
          >
            {typeLabel}
          </span>
        </div>
      </div>

      {/* Description */}
      {incentive.description && (
        <p className="relative z-10 text-sm text-muted-foreground line-clamp-2 mb-3 leading-relaxed">
          {incentive.description}
        </p>
      )}

      {/* Divider */}
      <div className="relative z-10 h-px bg-border mb-3" />

      {/* Bottom: budget, dates, status + arrow */}
      <div className="relative z-10 flex items-center gap-4 text-sm">
        {incentive.budgetTotal && (
          <div>
            <p className="font-semibold text-foreground tabular-nums">
              {formatBudget(incentive.budgetTotal)}
            </p>
            <p className="text-xs text-muted-foreground">Budget</p>
          </div>
        )}
        {incentive.startDate && (
          <div>
            <p className="font-medium text-foreground tabular-nums">
              {formatDate(incentive.startDate)}
            </p>
            <p className="text-xs text-muted-foreground">Start</p>
          </div>
        )}
        {incentive.endDate && (
          <div>
            <p className="font-medium text-foreground tabular-nums">
              {formatDate(incentive.endDate)}
            </p>
            <p className="text-xs text-muted-foreground">End</p>
          </div>
        )}
        <div className="ml-auto flex items-center gap-2">
          <span
            className={`inline-flex items-center rounded-md px-2.5 py-1 text-xs font-medium ${STATUS_COLORS[incentive.status]}`}
          >
            {INCENTIVE_STATUS_LABELS[incentive.status]}
          </span>
          <ArrowRight className="h-3.5 w-3.5 text-muted-foreground group-hover:text-[hsl(160_60%_38%)] group-hover:translate-x-0.5 transition-[color,transform]" />
        </div>
      </div>
    </button>
  );
}

function SkeletonBlock({ className }: { className?: string }) {
  return <div className={cn("skeleton-shimmer", className)} />;
}

function ExistingIncentiveCardSkeleton() {
  return (
    <div className="relative rounded-2xl border border-border bg-background/70 backdrop-blur-sm p-5 overflow-hidden">
      {/* Left accent bar placeholder */}
      <div className="absolute left-0 top-0 bottom-0 w-[3px] rounded-l-2xl bg-muted" />

      {/* Top row: name + actions/badge */}
      <div className="flex items-start justify-between gap-3 mb-1.5">
        <SkeletonBlock className="h-4 w-3/5 rounded-md" />
        <div className="flex items-center gap-2 flex-shrink-0">
          <SkeletonBlock className="h-3.5 w-3.5 rounded" />
          <SkeletonBlock className="h-5 w-16 rounded-md" />
        </div>
      </div>

      {/* Description (2 lines) */}
      <div className="space-y-1.5 mb-3">
        <SkeletonBlock className="h-3 w-full rounded" />
        <SkeletonBlock className="h-3 w-4/5 rounded" />
      </div>

      {/* Divider */}
      <div className="h-px bg-border mb-3" />

      {/* Bottom row: budget / start / end / status + arrow */}
      <div className="flex items-center gap-4">
        <div className="space-y-1.5">
          <SkeletonBlock className="h-4 w-12 rounded" />
          <SkeletonBlock className="h-3 w-10 rounded" />
        </div>
        <div className="space-y-1.5">
          <SkeletonBlock className="h-4 w-16 rounded" />
          <SkeletonBlock className="h-3 w-8 rounded" />
        </div>
        <div className="space-y-1.5">
          <SkeletonBlock className="h-4 w-16 rounded" />
          <SkeletonBlock className="h-3 w-8 rounded" />
        </div>
        <div className="ml-auto flex items-center gap-2">
          <SkeletonBlock className="h-5 w-16 rounded-md" />
          <SkeletonBlock className="h-3.5 w-3.5 rounded" />
        </div>
      </div>
    </div>
  );
}

function ExistingIncentiveCardGridSkeleton() {
  return (
    <div
      className="grid grid-cols-1 md:grid-cols-2 gap-4 animate-in fade-in duration-300"
      aria-busy="true"
      aria-live="polite"
    >
      {Array.from({ length: 6 }).map((_, i) => (
        <ExistingIncentiveCardSkeleton key={i} />
      ))}
    </div>
  );
}
