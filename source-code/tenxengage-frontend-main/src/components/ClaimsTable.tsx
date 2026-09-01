import {
  useState,
  useMemo,
  useRef,
  useCallback,
  useEffect,
  Fragment,
} from "react";
import { PermissionGate } from "@/components/PermissionGate";
import { FeatureGate } from "@/components/FeatureGate";
import { format } from "date-fns";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card";
import {
  CheckCircle,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  Search,
  X,
  ChevronDown,
  ChevronRight,
  AlertCircle,
  CheckCircle2,
  ShieldAlert,
  Download,
  Loader2,
  ClipboardList,
} from "lucide-react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import { useClaimDetail, useClaimDeal } from "@/hooks/useClaimApi";
import type {
  ClaimResponse,
  ClaimStatus,
  ClaimerInfo,
  ClaimSummaryResponse,
} from "@/types/claim.types";
import type { PaginatedResponse } from "@/types/api.types";
import { formatCurrency, formatDate } from "@/utils/formatters";
import { RewardBreakdownHover } from "@/components/RewardBreakdownHover";
import { getCurrency } from "@/config/currencies";
import { ClaimsTableSkeleton } from "@/components/skeletons/ClaimsSkeleton";

// ─── Types ──────────────────────────────────────────────────────────────────

type StatusFilter = "all" | "UNCLAIMED" | "CLAIMED";
type SortField = "date" | "claimer" | "poNumber" | "amount" | "status";
type SortDirection = "asc" | "desc";

const COL_COUNT = 8;

const statusConfig: Record<
  ClaimStatus,
  {
    label: string;
    dot: string;
    bg: string;
    text: string;
  }
> = {
  UNCLAIMED: {
    label: "Unclaimed",
    dot: "bg-warning",
    bg: "bg-warning/10",
    text: "text-warning",
  },
  CLAIMED: {
    label: "Claimed",
    dot: "bg-primary",
    bg: "bg-primary/10",
    text: "text-primary",
  },
};

// ─── Multi Item Hover ────────────────────────────────────────────────────────

function MultiItemHover({
  items,
  children,
}: {
  items: { label: string; sublabel?: string }[];
  children: React.ReactNode;
}) {
  if (items.length <= 1) return <>{children}</>;
  return (
    <HoverCard openDelay={200} closeDelay={100}>
      <HoverCardTrigger asChild>{children}</HoverCardTrigger>
      <HoverCardContent
        className="w-auto min-w-[200px] p-3 rounded-xl border-border"
        align="start"
      >
        <div className="space-y-2">
          {items.map((item, i) => (
            <div key={i} className="flex flex-col">
              <span className="text-xs font-medium text-foreground">
                {item.label}
              </span>
              {item.sublabel && (
                <span className="text-xs text-muted-foreground">
                  {item.sublabel}
                </span>
              )}
            </div>
          ))}
        </div>
      </HoverCardContent>
    </HoverCard>
  );
}

// ─── Animated Expand Wrapper ─────────────────────────────────────────────────

function AnimatedExpandRow({
  isExpanded,
  colSpan,
  children,
}: {
  isExpanded: boolean;
  colSpan: number;
  children: React.ReactNode;
}) {
  const contentRef = useRef<HTMLDivElement>(null);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const [isVisible, setIsVisible] = useState(false);

  // Control mount/unmount lifecycle
  useEffect(() => {
    if (isExpanded) {
      setIsVisible(true);
    } else {
      // Collapse: animate to 0, then unmount after transition
      const wrapper = wrapperRef.current;
      if (wrapper) wrapper.style.height = "0px";
      const timer = setTimeout(() => setIsVisible(false), 300);
      return () => clearTimeout(timer);
    }
  }, [isExpanded]);

  // Handle expand animation + track content size changes (tab switches, data load)
  useEffect(() => {
    if (!isVisible) return;
    const wrapper = wrapperRef.current;
    const content = contentRef.current;
    if (!wrapper || !content) return;

    // 1. Disable transition and set height to 0
    wrapper.style.transition = "none";
    wrapper.style.height = "0px";
    // 2. Force reflow so browser commits height:0 layout
    void wrapper.offsetHeight;
    // 3. Re-enable transition and set real height — browser animates 0 → real
    wrapper.style.transition = "height 300ms cubic-bezier(0.4, 0, 0.2, 1)";
    wrapper.style.height = content.offsetHeight + "px";

    // ResizeObserver for subsequent content changes (tab switches, async data)
    const ro = new ResizeObserver(() => {
      wrapper.style.height = content.offsetHeight + "px";
    });
    ro.observe(content);
    return () => ro.disconnect();
  }, [isVisible]);

  if (!isVisible && !isExpanded) return null;

  return (
    <TableRow className="bg-muted/30 hover:bg-muted/50">
      <TableCell colSpan={colSpan} className="p-0">
        <div
          ref={wrapperRef}
          className="overflow-hidden"
          style={{
            height: 0,
            transition: "height 300ms cubic-bezier(0.4, 0, 0.2, 1)",
          }}
        >
          <div ref={contentRef}>{children}</div>
        </div>
      </TableCell>
    </TableRow>
  );
}

// ─── Expanded Row ────────────────────────────────────────────────────────────

function ExpandedClaimRowContent({ claimId }: { claimId: string }) {
  const { data: detail, isLoading } = useClaimDetail(claimId);
  const [view, setView] = useState<"eligible" | "ineligible">("eligible");

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-8 gap-2 text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" />
        <span className="text-xs">Loading incentive details...</span>
      </div>
    );
  }

  if (!detail) return null;

  const eligible = detail.eligibleIncentives;
  const ineligible = detail.ineligibleIncentives;

  return (
    <div className="flex justify-center py-4">
      <div className="w-full max-w-2xl space-y-3">
        <div className="flex items-center gap-1 w-fit mx-auto">
          <button
            onClick={() => setView("eligible")}
            className={cn(
              "inline-flex items-center gap-1.5 h-8 px-3 rounded-lg text-xs font-medium transition-[background-color,color,box-shadow] duration-150",
              view === "eligible"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground hover:bg-muted",
            )}
          >
            <CheckCircle2 className="h-3 w-3" />
            Eligible ({eligible.length})
          </button>
          <button
            onClick={() => setView("ineligible")}
            className={cn(
              "inline-flex items-center gap-1.5 h-8 px-3 rounded-lg text-xs font-medium transition-[background-color,color,box-shadow] duration-150",
              view === "ineligible"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground hover:bg-muted",
            )}
          >
            <ShieldAlert className="h-3 w-3" />
            Ineligible ({ineligible.length})
          </button>
        </div>

        <div>
          {view === "eligible" ? (
            <div className="space-y-1.5">
              {eligible.map((inc) => {
                const monetaryEntries = Object.entries(
                  inc.rewardBreakdown.monetary,
                );
                const nonMonetaryEntries = Object.entries(
                  inc.rewardBreakdown.nonMonetary,
                );
                return (
                  <div
                    key={inc.incentiveId}
                    className="flex items-start justify-between rounded-lg border border-success/30 bg-success/5 px-3 py-2"
                  >
                    <div className="flex items-center gap-2 self-center">
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-success/10 text-xs font-medium text-success">
                        <CheckCircle2 className="h-2.5 w-2.5" />
                        Eligible
                      </span>
                      <span className="text-sm font-medium text-foreground">
                        {inc.incentiveName}
                      </span>
                    </div>
                    <div className="flex flex-col items-end gap-0.5">
                      <span className="text-sm font-semibold text-success tabular-nums">
                        {formatCurrency(inc.totalReward)}
                      </span>
                      <div className="flex items-center gap-2 text-xs text-muted-foreground">
                        {monetaryEntries.map(([key, value]) => {
                          const config = getCurrency(key);
                          const Icon = config.icon;
                          return (
                            <span key={key} className="flex items-center gap-1">
                              <Icon
                                className={cn("h-2.5 w-2.5", config.iconClass)}
                              />
                              {config.format(value)}
                            </span>
                          );
                        })}
                        {nonMonetaryEntries.map(([key, value]) => {
                          const config = getCurrency(key);
                          const Icon = config.icon;
                          return (
                            <span key={key} className="flex items-center gap-1">
                              <Icon
                                className={cn("h-2.5 w-2.5", config.iconClass)}
                              />
                              {config.format(value)}
                            </span>
                          );
                        })}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          ) : ineligible.length === 0 ? (
            <div className="flex items-center justify-center py-6 text-xs text-muted-foreground">
              No ineligible incentives to show for this PO.
            </div>
          ) : (
            <div className="space-y-1.5">
              {ineligible.map((inc) => (
                <div
                  key={inc.incentiveId}
                  className="flex items-start gap-2.5 rounded-lg border border-border bg-muted/50 px-3 py-2"
                >
                  <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-muted text-xs font-medium text-muted-foreground mt-0.5 shrink-0">
                    Ineligible
                  </span>
                  <div className="flex flex-col gap-0.5">
                    <span className="text-sm font-medium text-muted-foreground">
                      {inc.incentiveName}
                    </span>
                    <span className="text-xs text-muted-foreground flex items-center gap-1.5">
                      <AlertCircle className="h-2.5 w-2.5 shrink-0" />
                      {inc.reason}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── Claims Table Props ──────────────────────────────────────────────────────

export interface ClaimsTableProps {
  claimsPage: PaginatedResponse<ClaimResponse> | undefined;
  claimsLoading: boolean;
  summary: ClaimSummaryResponse | undefined;
  currentUserId: string | undefined;
  cardDescription: string;
}

// ─── Claims Table Component ──────────────────────────────────────────────────

export function ClaimsTable({
  claimsPage,
  claimsLoading,
  summary,
  currentUserId,
  cardDescription,
}: ClaimsTableProps) {
  const claimDeal = useClaimDeal();

  const [expandedClaims, setExpandedClaims] = useState<Set<string>>(new Set());
  const [showAllPOs, setShowAllPOs] = useState(false);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [poSearch, setPoSearch] = useState("");
  const [sortState, setSortState] = useState<{
    field: SortField;
    direction: SortDirection;
  }>({ field: "date", direction: "desc" });
  const [scrollProgress, setScrollProgress] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null);

  const claims = useMemo(() => claimsPage?.data ?? [], [claimsPage?.data]);

  const filteredClaims = useMemo(() => {
    let result = claims;
    if (statusFilter !== "all") {
      result = result.filter((c) => c.status === statusFilter);
    }
    if (poSearch.trim()) {
      const q = poSearch.trim().toLowerCase();
      result = result.filter(
        (c) =>
          c.orderNumber.toLowerCase().includes(q) ||
          c.primaryIncentiveName?.toLowerCase().includes(q) ||
          c.claimers.some((cl) => cl.name.toLowerCase().includes(q)),
      );
    }
    return result;
  }, [claims, statusFilter, poSearch]);

  const sortedClaims = useMemo(() => {
    const statusOrder: Record<ClaimStatus, number> = {
      UNCLAIMED: 0,
      CLAIMED: 1,
    };
    return [...filteredClaims].sort((a, b) => {
      let comparison = 0;
      switch (sortState.field) {
        case "date":
          comparison =
            new Date(a.orderDate).getTime() - new Date(b.orderDate).getTime();
          break;
        case "claimer": {
          const aName = a.claimers[0]?.name ?? "";
          const bName = b.claimers[0]?.name ?? "";
          comparison = aName.localeCompare(bName);
          break;
        }
        case "poNumber":
          comparison = a.orderNumber.localeCompare(b.orderNumber);
          break;
        case "amount":
          comparison = a.totalMonetaryReward - b.totalMonetaryReward;
          break;
        case "status":
          comparison = statusOrder[a.status] - statusOrder[b.status];
          break;
      }
      return sortState.direction === "asc" ? comparison : -comparison;
    });
  }, [filteredClaims, sortState]);

  const displayedClaims = useMemo(() => {
    if (showAllPOs) return sortedClaims;
    return sortedClaims.filter((c) => c.eligibleIncentiveCount > 0);
  }, [sortedClaims, showAllPOs]);

  const handleScroll = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    const { scrollTop, scrollHeight, clientHeight } = el;
    const maxScroll = scrollHeight - clientHeight;
    setScrollProgress(maxScroll > 0 ? scrollTop / maxScroll : 0);
  }, []);

  const toggleExpanded = (id: string) => {
    setExpandedClaims((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleSort = (field: SortField) => {
    setSortState((prev) =>
      prev.field === field
        ? { field, direction: prev.direction === "asc" ? "desc" : "asc" }
        : { field, direction: "asc" },
    );
  };

  const getSortIcon = (field: SortField) => {
    if (sortState.field !== field)
      return <ArrowUpDown className="ml-1 h-3 w-3 opacity-40" />;
    return sortState.direction === "asc" ? (
      <ArrowUp className="ml-1 h-3 w-3" />
    ) : (
      <ArrowDown className="ml-1 h-3 w-3" />
    );
  };

  const isCurrentUser = (userId: string) => currentUserId === userId;

  const handleClaimNow = (claim: ClaimResponse) => {
    claimDeal.mutate(claim.id, {
      onSuccess: (data) => {
        const awarded = data?.totalMonetaryReward ?? claim.totalMonetaryReward;
        toast.success("Claimed!", {
          description: `${claim.orderNumber} claimed successfully — you earned ${formatCurrency(awarded)} in rewards`,
        });
      },
      onError: () => {
        toast.error("Claim failed", {
          description:
            "Something went wrong while claiming this deal. Please try again.",
        });
      },
    });
  };

  const handleExportCSV = useCallback(() => {
    const headers = [
      "Order #",
      "Booking Date",
      "Claimer(s)",
      "Incentive",
      "Total Monetary Reward",
      "Status",
    ];
    const rows = displayedClaims.map((claim) => [
      claim.orderNumber,
      formatDate(claim.orderDate),
      claim.claimers.map((c) => c.name).join("; ") || "",
      claim.primaryIncentiveName ?? "",
      claim.totalMonetaryReward,
      statusConfig[claim.status].label,
    ]);
    const csvContent = [headers, ...rows]
      .map((row) => row.map((cell) => `"${cell}"`).join(","))
      .join("\n");
    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `claims-export-${format(new Date(), "yyyy-MM-dd")}.csv`;
    link.click();
    URL.revokeObjectURL(url);
    toast.success("Exported!", {
      description: `${displayedClaims.length} claims exported to CSV.`,
    });
  }, [displayedClaims]);

  return (
    <div
      className="rounded-xl border border-border bg-background overflow-hidden"
      data-tour="claims-table"
    >
      {/* Header */}
      <div className="px-5 py-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-6">
            <div className="flex items-center gap-3">
              <ClipboardList className="h-6 w-6 text-muted-foreground" />
              <div>
                <h3 className="text-2xl font-semibold text-foreground leading-tight">
                  All Claims
                </h3>
                <p className="text-sm text-muted-foreground mt-0.5">
                  {cardDescription}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2 bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-800 rounded-lg px-4 py-2">
              <span className="text-sm font-medium text-emerald-700 dark:text-emerald-300">
                Total Earnings:
              </span>
              <span className="text-lg font-semibold text-emerald-600 dark:text-emerald-400">
                <RewardBreakdownHover
                  label="Total Earnings"
                  entries={summary?.currencyBreakdown ?? {}}
                  monetaryTotal={
                    summary
                      ? Object.entries(summary.currencyBreakdown)
                          .filter(([k]) => getCurrency(k).type === "monetary")
                          .reduce((sum, [, v]) => sum + Number(v), 0)
                      : 0
                  }
                />
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {/* Deals Scope Toggle */}
            <div className="flex items-center gap-1">
              <button
                onClick={() => setShowAllPOs(false)}
                className={cn(
                  "inline-flex items-center gap-1.5 h-9 px-3.5 rounded-lg text-sm font-medium transition-[background-color,color,box-shadow] duration-150 whitespace-nowrap",
                  !showAllPOs
                    ? "bg-primary text-primary-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground hover:bg-muted",
                )}
              >
                Eligible Deals
              </button>
              <button
                onClick={() => setShowAllPOs(true)}
                className={cn(
                  "inline-flex items-center gap-1.5 h-9 px-3.5 rounded-lg text-sm font-medium transition-[background-color,color,box-shadow] duration-150 whitespace-nowrap",
                  showAllPOs
                    ? "bg-primary text-primary-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground hover:bg-muted",
                )}
              >
                All Deals
              </button>
            </div>

            {/* PO# Search */}
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              <Input
                placeholder="Search PO#..."
                value={poSearch}
                onChange={(e) => setPoSearch(e.target.value)}
                className="pl-9 pr-8 w-[150px] h-8 text-sm border-border focus-visible:ring-primary/30"
              />
              {poSearch && (
                <button
                  onClick={() => setPoSearch("")}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                >
                  <X className="h-3 w-3" />
                </button>
              )}
            </div>

            {/* Status Filter */}
            <Select
              value={statusFilter}
              onValueChange={(v) => setStatusFilter(v as StatusFilter)}
            >
              <SelectTrigger className="w-[130px] h-8 text-sm border-border">
                <SelectValue placeholder="All Statuses" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Statuses</SelectItem>
                <SelectItem value="UNCLAIMED">Unclaimed</SelectItem>
                <SelectItem value="CLAIMED">Claimed</SelectItem>
              </SelectContent>
            </Select>

            {/* Export */}
            <PermissionGate permission="action.claim.export">
              <FeatureGate feature="export_reports">
                <Button
                  variant="outline"
                  size="sm"
                  className="gap-1.5 h-8 text-sm border-border text-foreground hover:bg-muted"
                  onClick={handleExportCSV}
                  disabled={displayedClaims.length === 0}
                >
                  <Download className="h-3.5 w-3.5" />
                  Export
                </Button>
              </FeatureGate>
            </PermissionGate>
          </div>
        </div>
      </div>

      {/* Content */}
      <div>
        {claimsLoading || !summary ? (
          <ClaimsTableSkeleton />
        ) : displayedClaims.length === 0 ? (
          <div className="text-center py-16">
            <p className="text-sm text-muted-foreground">
              No claims found for the selected filters.
            </p>
          </div>
        ) : (
          <div className="overflow-hidden">
            <Table className="table-fixed">
              <TableHeader className="sticky top-0 z-10 bg-background">
                <TableRow className="border-b border-border">
                  <TableHead className="w-[40px]"></TableHead>
                  <TableHead className="w-[12%] text-xs font-medium text-muted-foreground">
                    <button
                      onClick={() => handleSort("date")}
                      className="flex items-center hover:text-foreground transition-colors"
                    >
                      Booking Date {getSortIcon("date")}
                    </button>
                  </TableHead>
                  <TableHead className="w-[15%] text-xs font-medium text-muted-foreground">
                    <button
                      onClick={() => handleSort("claimer")}
                      className="flex items-center hover:text-foreground transition-colors"
                    >
                      Claimer(s) {getSortIcon("claimer")}
                    </button>
                  </TableHead>
                  <TableHead className="w-[15%] text-xs font-medium text-muted-foreground">
                    <button
                      onClick={() => handleSort("poNumber")}
                      className="flex items-center hover:text-foreground transition-colors"
                    >
                      PO# {getSortIcon("poNumber")}
                    </button>
                  </TableHead>
                  <TableHead className="w-[20%] text-xs font-medium text-muted-foreground">
                    Incentive
                  </TableHead>
                  <TableHead className="w-[14%] text-xs font-medium text-muted-foreground">
                    <button
                      onClick={() => handleSort("amount")}
                      className="flex items-center hover:text-foreground transition-colors"
                    >
                      Total Rewards {getSortIcon("amount")}
                    </button>
                  </TableHead>
                  <TableHead className="w-[12%] text-xs font-medium text-muted-foreground">
                    <button
                      onClick={() => handleSort("status")}
                      className="flex items-center hover:text-foreground transition-colors"
                    >
                      Status {getSortIcon("status")}
                    </button>
                  </TableHead>
                  <TableHead className="w-[12%]"></TableHead>
                </TableRow>
              </TableHeader>
            </Table>
            <div
              ref={scrollRef}
              onScroll={handleScroll}
              className="max-h-[calc(100vh-420px)] min-h-[200px] overflow-y-auto"
            >
              <Table className="table-fixed">
                <TableBody>
                  {displayedClaims.map((claim) => {
                    const sc = statusConfig[claim.status];
                    const isClaimable =
                      claim.status === "UNCLAIMED" &&
                      claim.eligibleIncentiveCount > 0 &&
                      !claim.claimers.some((c) => isCurrentUser(c.userId));
                    const isIneligible =
                      claim.status === "UNCLAIMED" &&
                      claim.eligibleIncentiveCount === 0;
                    const isExpanded = expandedClaims.has(claim.id);
                    const firstClaimer = claim.claimers[0] as
                      | ClaimerInfo
                      | undefined;
                    const isCurrent = claim.claimers.some((c) =>
                      isCurrentUser(c.userId),
                    );

                    const claimerItems = claim.claimers.map((c) => ({
                      label: isCurrentUser(c.userId) ? "You" : c.name,
                      sublabel: `Claimed ${formatDate(c.claimedAt)}`,
                    }));

                    return (
                      <Fragment key={claim.id}>
                        <TableRow
                          className={cn(
                            "hover:bg-muted/50 cursor-pointer transition-colors",
                            isExpanded && "bg-muted/30",
                            claim.status !== "UNCLAIMED" &&
                              isCurrent &&
                              "bg-primary/5 hover:bg-primary/10 border-l-2 border-l-primary/30",
                          )}
                          onClick={() => toggleExpanded(claim.id)}
                        >
                          <TableCell className="px-2 w-[40px]">
                            <button className="p-1 rounded hover:bg-muted transition-colors">
                              {isExpanded ? (
                                <ChevronDown className="h-3.5 w-3.5 text-muted-foreground" />
                              ) : (
                                <ChevronRight className="h-3.5 w-3.5 text-muted-foreground" />
                              )}
                            </button>
                          </TableCell>
                          <TableCell className="text-xs text-muted-foreground w-[12%] tabular-nums">
                            {formatDate(claim.orderDate)}
                          </TableCell>
                          <TableCell className="text-xs text-foreground w-[15%]">
                            {claim.claimers.length === 0 ? (
                              <span className="text-muted-foreground">
                                &mdash;
                              </span>
                            ) : (
                              <MultiItemHover items={claimerItems}>
                                <div className="flex items-center gap-1.5 cursor-default">
                                  <div>
                                    {firstClaimer &&
                                    isCurrentUser(firstClaimer.userId) ? (
                                      <span className="font-medium text-primary">
                                        You
                                      </span>
                                    ) : (
                                      <span className="font-medium">
                                        {firstClaimer?.name}
                                      </span>
                                    )}
                                    <div className="text-xs text-muted-foreground leading-tight mt-0.5">
                                      Claimed{" "}
                                      {firstClaimer
                                        ? formatDate(firstClaimer.claimedAt)
                                        : ""}
                                    </div>
                                  </div>
                                  {claim.claimers.length > 1 && (
                                    <span className="inline-flex items-center px-1.5 py-0.5 rounded-md bg-muted text-xs font-medium text-muted-foreground shrink-0">
                                      +{claim.claimers.length - 1}
                                    </span>
                                  )}
                                </div>
                              </MultiItemHover>
                            )}
                          </TableCell>
                          <TableCell className="font-mono text-xs text-muted-foreground w-[15%] tabular-nums">
                            {claim.orderNumber}
                          </TableCell>
                          <TableCell className="text-xs text-muted-foreground w-[20%]">
                            {claim.eligibleIncentiveCount === 0 ? (
                              <span className="text-muted-foreground italic text-xs">
                                No eligible incentives
                              </span>
                            ) : claim.eligibleIncentiveCount > 1 ? (
                              <HoverCard openDelay={200} closeDelay={100}>
                                <HoverCardTrigger asChild>
                                  <div className="flex items-center gap-1.5 cursor-help">
                                    <span className="truncate">
                                      {claim.primaryIncentiveName}
                                    </span>
                                    <span className="inline-flex items-center px-1.5 py-0.5 rounded-md bg-muted text-xs font-medium text-muted-foreground shrink-0">
                                      +{claim.eligibleIncentiveCount - 1}
                                    </span>
                                  </div>
                                </HoverCardTrigger>
                                <HoverCardContent
                                  className="w-auto min-w-[200px] p-3 rounded-xl border-border"
                                  align="start"
                                >
                                  <div className="space-y-1.5">
                                    {claim.eligibleIncentiveNames.map(
                                      (name, idx) => (
                                        <div
                                          key={idx}
                                          className="text-xs font-medium text-foreground"
                                        >
                                          {name}
                                        </div>
                                      ),
                                    )}
                                  </div>
                                </HoverCardContent>
                              </HoverCard>
                            ) : (
                              <div className="truncate">
                                {claim.primaryIncentiveName}
                              </div>
                            )}
                          </TableCell>
                          <TableCell className="text-xs font-semibold text-foreground w-[14%] tabular-nums">
                            <RewardBreakdownHover
                              label="Rewards"
                              entries={{
                                ...claim.rewardBreakdown.monetary,
                                ...claim.rewardBreakdown.nonMonetary,
                              }}
                              monetaryTotal={claim.totalMonetaryReward}
                              animate={false}
                            />
                          </TableCell>
                          <TableCell className="w-[12%]">
                            <span
                              className={cn(
                                "inline-flex items-center gap-1.5 px-2 py-1 rounded-md text-xs font-medium",
                                sc.bg,
                                sc.text,
                              )}
                            >
                              <span
                                className={cn(
                                  "w-1.5 h-1.5 rounded-full",
                                  sc.dot,
                                )}
                              />
                              {sc.label}
                            </span>
                          </TableCell>
                          <TableCell
                            className="w-[12%]"
                            onClick={(e) => e.stopPropagation()}
                          >
                            <PermissionGate permission="action.claim.submit">
                              {isIneligible ? (
                                <HoverCard openDelay={200} closeDelay={100}>
                                  <HoverCardTrigger asChild>
                                    <span className="inline-block">
                                      <Button
                                        size="sm"
                                        disabled
                                        variant="outline"
                                        className="gap-1.5 w-[100px] justify-center opacity-40 pointer-events-none h-7 text-xs border-border"
                                      >
                                        Claim Now
                                      </Button>
                                    </span>
                                  </HoverCardTrigger>
                                  <HoverCardContent
                                    className="w-auto p-3 text-xs rounded-xl border-border"
                                    align="end"
                                    side="top"
                                  >
                                    <p className="text-muted-foreground">
                                      This PO# has no eligible incentives worth
                                      any rewards
                                    </p>
                                  </HoverCardContent>
                                </HoverCard>
                              ) : (
                                <Button
                                  size="sm"
                                  onClick={
                                    isClaimable
                                      ? () => handleClaimNow(claim)
                                      : undefined
                                  }
                                  disabled={!isClaimable || claimDeal.isPending}
                                  variant={isClaimable ? "default" : "outline"}
                                  className={cn(
                                    "gap-1.5 w-[100px] justify-center h-7 text-xs",
                                    isClaimable
                                      ? "bg-primary hover:bg-primary/90"
                                      : "border-border text-muted-foreground",
                                  )}
                                  {...(isClaimable
                                    ? { "data-tour": "claim-button" }
                                    : {})}
                                >
                                  {claimDeal.isPending &&
                                  claimDeal.variables === claim.id ? (
                                    <Loader2 className="h-3 w-3 animate-spin" />
                                  ) : !isClaimable ? (
                                    <CheckCircle className="h-3 w-3" />
                                  ) : null}
                                  {isClaimable ? "Claim Now" : "Claimed"}
                                </Button>
                              )}
                            </PermissionGate>
                          </TableCell>
                        </TableRow>
                        <AnimatedExpandRow
                          isExpanded={isExpanded}
                          colSpan={COL_COUNT}
                        >
                          <ExpandedClaimRowContent claimId={claim.id} />
                        </AnimatedExpandRow>
                      </Fragment>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
            {/* Row count indicator */}
            <div className="flex items-center justify-between px-4 py-2 border-t border-border bg-muted/30 text-xs text-muted-foreground">
              <span>
                Showing {displayedClaims.length}
                {claimsPage ? ` of ${claimsPage.totalElements}` : ""} claim
                {displayedClaims.length !== 1 ? "s" : ""}
              </span>
              <span className="tabular-nums">
                {Math.round(scrollProgress * 100)}% scrolled
              </span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
