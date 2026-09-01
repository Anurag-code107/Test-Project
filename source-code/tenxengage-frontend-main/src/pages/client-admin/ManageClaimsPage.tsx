import {
  useState,
  useMemo,
  useRef,
  useEffect,
  useLayoutEffect,
  useCallback,
  useDeferredValue,
  memo,
} from "react";
import { PermissionGate } from "@/components/PermissionGate";
import { format, subDays, startOfQuarter, startOfYear } from "date-fns";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Calendar } from "@/components/ui/calendar";
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  CheckCircle,
  DollarSign,
  Pencil,
  CalendarIcon,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  Coins,
  Gift,
  Award,
  ClipboardList,
  Building2,
  Search,
  X,
  ChevronDown,
  ChevronRight,
  CheckCircle2,
  ShieldAlert,
  AlertCircle,
  Loader2,
  UserMinus,
} from "lucide-react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import {
  useClaims,
  useClaimDetail,
  useUnclaimDeal,
  useUpdateClaim,
  useClaimSummary,
} from "@/hooks/useClaimApi";
import type {
  ClaimResponse,
  ClaimStatus,
  ClaimListParams,
  RewardBreakdown,
  EligibleIncentive,
  IneligibleIncentive,
} from "@/types/claim.types";
import { formatCurrency, formatDate } from "@/utils/formatters";
import { getCurrency } from "@/config/currencies";
import { RewardBreakdownHover as SharedRewardBreakdownHover } from "@/components/RewardBreakdownHover";
import { PageBanner } from "@/components/PageBanner";
import { LocationFilter } from "@/components/LocationFilter";
import {
  PartnerGroupedClaimsSkeleton,
  ClaimDetailSkeleton,
} from "@/components/skeletons/ClaimsSkeleton";

type DateFilter = "recent" | "quarter" | "year" | "custom";
type StatusFilter = "all" | ClaimStatus;
type RegionFilter = string;
type SortField = "date" | "claimer" | "orderNumber" | "amount" | "status";
type SortDirection = "asc" | "desc";

const statusConfig: Record<
  ClaimStatus,
  {
    label: string;
    variant: "default" | "secondary";
    icon: typeof CheckCircle;
    className?: string;
  }
> = {
  CLAIMED: { label: "Claimed", variant: "default", icon: CheckCircle },
  UNCLAIMED: {
    label: "Unclaimed",
    variant: "secondary",
    icon: CheckCircle,
    className:
      "border-amber-600 text-amber-700 bg-amber-100 dark:bg-amber-900/50 dark:text-amber-300 dark:border-amber-500",
  },
};

// Reward breakdown hover — delegates to shared component. Memoized so
// re-rendering the page (e.g. on filter change) doesn't force the shared
// component (and its SlotDropNumber) to re-render on every row when inputs
// haven't changed. animate={false} keeps row-level amounts as static numbers
// — the slot animation is reserved for the "Total Earnings" banner above
// where emphasis matters. With 50+ rows this alone removes the dominant
// per-filter-change work from the commit.
const RewardBreakdownHover = memo(function RewardBreakdownHover({
  breakdown,
  totalMonetary,
}: {
  breakdown: RewardBreakdown;
  totalMonetary: number;
}) {
  const entries: Record<string, string | number> = {
    ...breakdown.monetary,
    ...breakdown.nonMonetary,
  };

  return (
    <SharedRewardBreakdownHover
      label="Rewards"
      entries={entries}
      monetaryTotal={totalMonetary}
      animate={false}
    />
  );
});

const COL_COUNT = 8;

// ─── Animated Expand Wrapper ─────────────────────────────────────────────────

export function AnimatedExpandRow({
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
  const [shouldRender, setShouldRender] = useState(false);

  // Single synchronous effect — runs between DOM commit and first paint, so the
  // animation is programmed into the same frame as the click's render.
  // ResizeObserver stays in place so tab switches inside the expanded row
  // (Eligible ↔ Ineligible, skeleton → loaded) continue to animate to the new
  // content height.
  useLayoutEffect(() => {
    const wrapper = wrapperRef.current;
    const content = contentRef.current;

    if (isExpanded) {
      setShouldRender(true);
      if (!wrapper || !content) return;

      wrapper.style.transition = "none";
      wrapper.style.height = "0px";
      void wrapper.offsetHeight;
      wrapper.style.transition = "height 300ms cubic-bezier(0.4, 0, 0.2, 1)";
      wrapper.style.height = content.offsetHeight + "px";

      const ro = new ResizeObserver(() => {
        wrapper.style.height = content.offsetHeight + "px";
      });
      ro.observe(content);
      return () => ro.disconnect();
    }

    if (!wrapper) return;
    wrapper.style.height = "0px";
    const timer = setTimeout(() => setShouldRender(false), 300);
    return () => clearTimeout(timer);
  }, [isExpanded]);

  if (!shouldRender && !isExpanded) return null;

  return (
    <TableRow className="bg-muted/20 hover:bg-muted/30">
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

// ─── Expanded Row Content ────────────────────────────────────────────────────

export const ExpandedClaimRowContent = ({
  claimId,
}: {
  claimId: string;
}) => {
  const { data: detail, isLoading } = useClaimDetail(claimId);
  const [view, setView] = useState<"eligible" | "ineligible">("eligible");

  if (isLoading) {
    return <ClaimDetailSkeleton />;
  }

  if (!detail) return null;

  const eligibleIncentives = detail.eligibleIncentives ?? [];
  const ineligibleIncentives = detail.ineligibleIncentives ?? [];

  return (
    <div className="flex justify-center py-4">
      <div className="w-full max-w-2xl space-y-3">
        <div className="flex items-center gap-1 w-fit mx-auto">
          <button
            onClick={() => setView("eligible")}
            className={cn(
              "inline-flex items-center gap-1.5 h-9 px-3.5 rounded-lg text-sm font-medium transition-[background-color,color,box-shadow] duration-150",
              view === "eligible"
                ? "bg-primary text-primary-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground hover:bg-muted",
            )}
          >
            <CheckCircle2 className="h-3.5 w-3.5" />
            Eligible ({eligibleIncentives.length})
          </button>
          {ineligibleIncentives.length > 0 && (
            <button
              onClick={() => setView("ineligible")}
              className={cn(
                "inline-flex items-center gap-1.5 h-9 px-3.5 rounded-lg text-sm font-medium transition-[background-color,color,box-shadow] duration-150",
                view === "ineligible"
                  ? "bg-primary text-primary-foreground shadow-sm"
                  : "text-muted-foreground hover:text-foreground hover:bg-muted",
              )}
            >
              <ShieldAlert className="h-3.5 w-3.5" />
              Ineligible ({ineligibleIncentives.length})
            </button>
          )}
        </div>

        <div>
          {view === "eligible" ? (
            <div className="space-y-1.5">
              {eligibleIncentives.map((inc) => {
                const monetaryEntries = Object.entries(
                  inc.rewardBreakdown.monetary,
                );
                const nonMonetaryEntries = Object.entries(
                  inc.rewardBreakdown.nonMonetary,
                );
                return (
                  <div
                    key={inc.incentiveId}
                    className="flex items-start justify-between rounded-lg border border-emerald-200 dark:border-emerald-800 bg-emerald-50/50 dark:bg-emerald-950/20 px-3 py-2"
                  >
                    <div className="flex items-center gap-2 self-center">
                      <Badge
                        variant="outline"
                        className="border-emerald-300 bg-emerald-100 text-emerald-700 dark:border-emerald-700 dark:bg-emerald-900/50 dark:text-emerald-400 text-xs"
                      >
                        Eligible
                      </Badge>
                      <span className="text-sm font-medium text-foreground">
                        {inc.incentiveName}
                      </span>
                    </div>
                    <div className="flex flex-col items-end gap-0.5">
                      <span className="text-sm font-semibold text-emerald-700 dark:text-emerald-400">
                        {formatCurrency(inc.totalReward)}
                      </span>
                      <div className="flex items-center gap-2 text-xs text-muted-foreground">
                        {[...monetaryEntries, ...nonMonetaryEntries].map(
                          ([key, value]) => {
                            const config = getCurrency(key);
                            const Icon = config.icon;
                            return (
                              <span
                                key={key}
                                className="flex items-center gap-1"
                              >
                                <Icon
                                  className={cn("h-3 w-3", config.iconClass)}
                                />
                                {config.format(value)}
                              </span>
                            );
                          },
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="space-y-1.5">
              {ineligibleIncentives.map((inc) => (
                <div
                  key={inc.incentiveId}
                  className="flex items-start gap-2.5 rounded-lg border border-border bg-muted/30 px-3 py-2"
                >
                  <Badge
                    variant="outline"
                    className="border-muted-foreground/30 text-muted-foreground text-xs mt-0.5 shrink-0"
                  >
                    Ineligible
                  </Badge>
                  <div className="flex flex-col gap-0.5">
                    <span className="text-sm font-medium text-muted-foreground">
                      {inc.incentiveName}
                    </span>
                    <span className="text-xs text-muted-foreground/80 flex items-center gap-1.5">
                      <AlertCircle className="h-3 w-3 shrink-0" />
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
};

// Claim row component — memoized so toggling one row does not re-render every
// other row in every partner group.
const ClaimRow = memo(function ClaimRow({
  claim,
  isExpanded,
  toggleExpanded,
  handleOpenEdit,
}: {
  claim: ClaimResponse;
  isExpanded: boolean;
  toggleExpanded: (id: string) => void;
  handleOpenEdit: (c: ClaimResponse) => void;
}) {
  const StatusIcon = statusConfig[claim.status].icon;
  return (
    <>
      <TableRow
        className={cn(
          "hover:bg-muted/30 cursor-pointer",
          isExpanded && "bg-muted/20",
        )}
        onClick={() => toggleExpanded(claim.id)}
      >
        <TableCell className="w-[40px] px-2">
          <button className="p-1 rounded hover:bg-muted transition-colors">
            {isExpanded ? (
              <ChevronDown className="h-4 w-4 text-muted-foreground" />
            ) : (
              <ChevronRight className="h-4 w-4 text-muted-foreground" />
            )}
          </button>
        </TableCell>
        <TableCell className="w-[12%] text-xs text-muted-foreground">
          {formatDate(claim.orderDate)}
        </TableCell>
        <TableCell className="w-[15%] text-xs text-foreground">
          {claim.claimers.length === 0 ? (
            <span className="text-muted-foreground/50">&mdash;</span>
          ) : (
            <HoverCard openDelay={200} closeDelay={100}>
              <HoverCardTrigger asChild>
                <div className="flex items-center gap-1.5 cursor-default">
                  <div>
                    {claim.claimers[0]!.name}
                    <div className="text-[10px] text-muted-foreground leading-tight mt-0.5">
                      Claimed {formatDate(claim.claimers[0]!.claimedAt)}
                    </div>
                  </div>
                  {claim.claimers.length > 1 && (
                    <Badge variant="secondary" className="text-xs shrink-0">
                      +{claim.claimers.length - 1}
                    </Badge>
                  )}
                </div>
              </HoverCardTrigger>
              {claim.claimers.length > 1 && (
                <HoverCardContent
                  className="w-auto min-w-[200px] p-3"
                  align="start"
                >
                  <div className="space-y-2">
                    {claim.claimers.map((c, i) => (
                      <div key={i} className="flex flex-col">
                        <span className="text-xs font-medium text-foreground">
                          {c.name}
                        </span>
                        <span className="text-[10px] text-muted-foreground">
                          Claimed {formatDate(c.claimedAt)}
                        </span>
                      </div>
                    ))}
                  </div>
                </HoverCardContent>
              )}
            </HoverCard>
          )}
        </TableCell>
        <TableCell className="w-[20%] text-xs text-muted-foreground">
          <div className="flex items-center gap-1.5">
            <span className="truncate">
              {claim.primaryIncentiveName ?? "—"}
            </span>
            {claim.eligibleIncentiveCount > 1 && (
              <Badge variant="secondary" className="text-xs shrink-0">
                +{claim.eligibleIncentiveCount - 1}
              </Badge>
            )}
          </div>
        </TableCell>
        <TableCell className="w-[15%] font-mono text-xs text-muted-foreground">
          {claim.orderNumber}
        </TableCell>
        <TableCell className="w-[14%] text-xs font-semibold text-foreground">
          <RewardBreakdownHover
            breakdown={claim.rewardBreakdown}
            totalMonetary={claim.totalMonetaryReward}
          />
        </TableCell>
        <TableCell className="w-[12%]">
          <Badge
            variant={statusConfig[claim.status].variant}
            className={`gap-1 ${statusConfig[claim.status].className || ""}`}
          >
            <StatusIcon className="h-3 w-3" />
            {statusConfig[claim.status].label}
          </Badge>
        </TableCell>
        <TableCell className="w-[12%]" onClick={(e) => e.stopPropagation()}>
          <PermissionGate permission="action.claim.edit">
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleOpenEdit(claim)}
              className="gap-1.5"
            >
              <Pencil className="h-3.5 w-3.5" />
              Edit
            </Button>
          </PermissionGate>
        </TableCell>
      </TableRow>
      <AnimatedExpandRow isExpanded={isExpanded} colSpan={COL_COUNT}>
        <ExpandedClaimRowContent claimId={claim.id} />
      </AnimatedExpandRow>
    </>
  );
});

// Scrollable claims table component with progress indicator
const ScrollableClaimsTable = ({
  claims,
  expandedClaims,
  onToggleExpand,
  handleOpenEdit,
}: {
  claims: ClaimResponse[];
  expandedClaims: Set<string>;
  onToggleExpand: (id: string) => void;
  handleOpenEdit: (claim: ClaimResponse) => void;
}) => {
  const maxHeight = 5 * 53;

  return (
    <div className="relative">
      <div className="overflow-y-auto" style={{ maxHeight: `${maxHeight}px` }}>
        <Table className="table-fixed">
          <TableBody>
            {claims.map((claim) => (
              <ClaimRow
                key={claim.id}
                claim={claim}
                isExpanded={expandedClaims.has(claim.id)}
                toggleExpanded={onToggleExpand}
                handleOpenEdit={handleOpenEdit}
              />
            ))}
          </TableBody>
        </Table>
      </div>
      <div className="text-xs text-muted-foreground text-center py-2 border-t border-border bg-muted/30">
        Showing {claims.length} claims -- Scroll to view all
      </div>
    </div>
  );
};

// Detail drawer for editing a claim
const ClaimEditDrawer = ({
  claim,
  onClose,
}: {
  claim: ClaimResponse;
  onClose: () => void;
}) => {
  const { data: detail, isLoading: detailLoading } = useClaimDetail(claim.id);
  const unclaimMutation = useUnclaimDeal();
  const updateMutation = useUpdateClaim();

  const [rewardAdjustments, setRewardAdjustments] = useState<
    Record<string, string>
  >({});
  const [adminComment, setAdminComment] = useState("");
  const [drawerBreakdownOpen, setDrawerBreakdownOpen] = useState(false);

  // Populate reward adjustments from claim's current breakdown
  useEffect(() => {
    const initial: Record<string, string> = {};
    for (const [key, value] of Object.entries(claim.rewardBreakdown.monetary)) {
      initial[key] = value;
    }
    setRewardAdjustments(initial);
  }, [claim]);

  // Populate admin comment from detail if available
  useEffect(() => {
    if (detail?.adminComment) {
      setAdminComment(detail.adminComment);
    }
  }, [detail]);

  const handleUnclaim = () => {
    if (!adminComment.trim()) {
      toast.error("Comment Required", {
        description: "Please provide a comment for unclaiming this deal.",
      });
      return;
    }
    unclaimMutation.mutate(
      { id: claim.id, comment: adminComment },
      {
        onSuccess: () => {
          toast.success("Deal Unclaimed", {
            description: `Order ${claim.orderNumber} has been unclaimed.`,
          });
          onClose();
        },
        onError: () => {
          toast.error("Failed to unclaim", {
            description: "An error occurred. Please try again.",
          });
        },
      },
    );
  };

  const handleSaveChanges = () => {
    if (!adminComment.trim()) {
      toast.error("Comment Required", {
        description: "Please provide a comment for this update.",
      });
      return;
    }
    updateMutation.mutate(
      {
        id: claim.id,
        data: {
          rewardAdjustments,
          comment: adminComment,
        },
      },
      {
        onSuccess: () => {
          toast.success("Claim Updated", {
            description: `Order ${claim.orderNumber} has been updated successfully.`,
          });
          onClose();
        },
        onError: () => {
          toast.error("Failed to update", {
            description: "An error occurred. Please try again.",
          });
        },
      },
    );
  };

  const eligibleIncentives: EligibleIncentive[] =
    detail?.eligibleIncentives ?? [];
  const ineligibleIncentives: IneligibleIncentive[] =
    detail?.ineligibleIncentives ?? [];

  return (
    <SheetContent className="sm:max-w-2xl overflow-y-auto">
      <SheetHeader>
        <SheetTitle className="text-foreground">Claim Details</SheetTitle>
        <SheetDescription>Order {claim.orderNumber}</SheetDescription>
      </SheetHeader>
      <div className="space-y-6 mt-6">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <Label className="text-xs text-muted-foreground">Order Date</Label>
            <p className="font-medium text-foreground">
              {formatDate(claim.orderDate)}
            </p>
          </div>
          <div>
            <Label className="text-xs text-muted-foreground">
              Order Number
            </Label>
            <p className="font-mono font-medium text-foreground">
              {claim.orderNumber}
            </p>
          </div>
        </div>
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label className="text-xs text-muted-foreground">Partner</Label>
              <p className="font-semibold text-foreground">
                {claim.partnerCompanyName}
              </p>
            </div>
            <div>
              <Label className="text-xs text-muted-foreground">
                Claimer(s)
              </Label>
              {claim.claimers.length === 0 ? (
                <p className="text-muted-foreground/50">&mdash;</p>
              ) : (
                <div className="space-y-1">
                  {claim.claimers.map((c, i) => (
                    <p key={i} className="font-medium text-foreground">
                      {c.name}
                      <span className="text-xs text-muted-foreground ml-1">
                        on {formatDate(c.claimedAt)}
                      </span>
                    </p>
                  ))}
                </div>
              )}
            </div>
          </div>
          {detail && (
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label className="text-xs text-muted-foreground">
                  Customer
                </Label>
                <p className="font-medium text-foreground">
                  {detail.customerName}
                </p>
              </div>
              <div>
                <Label className="text-xs text-muted-foreground">
                  Max Claimers
                </Label>
                <p className="font-medium text-foreground">
                  {detail.maxClaimersPerDeal}
                </p>
              </div>
            </div>
          )}
          <div>
            <Label className="text-xs text-muted-foreground">Status</Label>
            <div className="mt-1">
              <Badge
                variant={statusConfig[claim.status].variant}
                className={`gap-1 ${statusConfig[claim.status].className || ""}`}
              >
                {statusConfig[claim.status].label}
              </Badge>
            </div>
          </div>
          {claim.claimers.length > 0 && (
            <div>
              <Label className="text-xs text-muted-foreground">
                Claimed By
              </Label>
              <div className="mt-1 space-y-1">
                {claim.claimers.map((claimer) => (
                  <div
                    key={claimer.userId}
                    className="text-sm text-foreground flex items-center gap-2"
                  >
                    <span className="font-medium">{claimer.name}</span>
                    <span className="text-xs text-muted-foreground">
                      on {formatDate(claimer.claimedAt)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
          <div className="space-y-2">
            <Label>Total Monetary Reward</Label>
            <div className="relative">
              <DollarSign className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                type="text"
                value={formatCurrency(claim.totalMonetaryReward)}
                className="pl-9 bg-muted/50"
                readOnly
              />
            </div>
          </div>
        </div>

        {/* Currency Breakdown — editable monetary rewards */}
        <div className="border-t border-border pt-4 space-y-4">
          <h3 className="font-semibold text-foreground flex items-center gap-2">
            <Coins className="h-4 w-4 text-muted-foreground" />
            Reward Breakdown
          </h3>

          {/* Monetary */}
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <DollarSign className="h-3.5 w-3.5 text-emerald-500" />
              <span className="text-sm font-medium text-foreground">
                Monetary Rewards
              </span>
            </div>
            <div className="grid grid-cols-2 gap-3 pl-5">
              {Object.entries(claim.rewardBreakdown.monetary).map(([key]) => (
                <div key={key} className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground flex items-center gap-1.5">
                    <Coins className="h-3 w-3 text-green-600" />{" "}
                    <span className="capitalize">{key}</span>
                  </Label>
                  <Input
                    type="text"
                    value={rewardAdjustments[key] ?? ""}
                    onChange={(e) =>
                      setRewardAdjustments((prev) => ({
                        ...prev,
                        [key]: e.target.value,
                      }))
                    }
                    className="h-9 text-sm"
                  />
                </div>
              ))}
            </div>
          </div>

          {/* Non-Monetary (read-only) */}
          {Object.keys(claim.rewardBreakdown.nonMonetary).length > 0 && (
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <Award className="h-3.5 w-3.5 text-purple-500" />
                <span className="text-sm font-medium text-foreground">
                  Non-Monetary Rewards
                </span>
                <span className="text-xs text-muted-foreground">
                  (Read only)
                </span>
              </div>
              <div className="grid grid-cols-2 gap-3 pl-5">
                {Object.entries(claim.rewardBreakdown.nonMonetary).map(
                  ([key, value]) => (
                    <div key={key} className="space-y-1.5">
                      <Label className="text-xs text-muted-foreground flex items-center gap-1.5">
                        <Gift className="h-3 w-3 text-violet-500" />{" "}
                        <span className="capitalize">{key}</span>
                      </Label>
                      <Input
                        type="text"
                        value={value}
                        className="h-9 text-sm bg-muted/50"
                        readOnly
                      />
                    </div>
                  ),
                )}
              </div>
            </div>
          )}
        </div>

        {/* Incentive Breakdown - Collapsible */}
        <div className="border-t border-border pt-4">
          <button
            onClick={() => setDrawerBreakdownOpen(!drawerBreakdownOpen)}
            className="flex items-center justify-between w-full group"
          >
            <h3 className="font-semibold text-foreground flex items-center gap-2">
              <ClipboardList className="h-4 w-4 text-muted-foreground" />
              Incentive Breakdown
              {detailLoading ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin text-muted-foreground" />
              ) : (
                <Badge variant="secondary" className="text-xs">
                  {eligibleIncentives.length + ineligibleIncentives.length}
                </Badge>
              )}
            </h3>
            {drawerBreakdownOpen ? (
              <ChevronDown className="h-4 w-4 text-muted-foreground" />
            ) : (
              <ChevronRight className="h-4 w-4 text-muted-foreground" />
            )}
          </button>

          {drawerBreakdownOpen && (
            <div className="space-y-3 mt-3">
              {detailLoading ? (
                <div className="flex items-center justify-center py-4 gap-2 text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span className="text-sm">Loading...</span>
                </div>
              ) : (
                <>
                  {/* Eligible */}
                  {eligibleIncentives.length > 0 && (
                    <div className="space-y-1.5">
                      <span className="text-xs font-medium text-muted-foreground flex items-center gap-1.5">
                        <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" />
                        Eligible ({eligibleIncentives.length})
                      </span>
                      {eligibleIncentives.map((inc) => {
                        const monetaryEntries = Object.entries(
                          inc.rewardBreakdown.monetary,
                        );
                        const nonMonetaryEntries = Object.entries(
                          inc.rewardBreakdown.nonMonetary,
                        );
                        return (
                          <div
                            key={inc.incentiveId}
                            className="flex items-center justify-between rounded-lg border border-emerald-200 dark:border-emerald-800 bg-emerald-50/50 dark:bg-emerald-950/20 px-3 py-2"
                          >
                            <div className="flex items-center gap-2">
                              <Badge
                                variant="outline"
                                className="border-emerald-300 bg-emerald-100 text-emerald-700 dark:border-emerald-700 dark:bg-emerald-900/50 dark:text-emerald-400 text-xs"
                              >
                                Eligible
                              </Badge>
                              <span className="text-sm font-medium text-foreground">
                                {inc.incentiveName}
                              </span>
                            </div>
                            <HoverCard openDelay={100} closeDelay={50}>
                              <HoverCardTrigger asChild>
                                <span className="text-sm font-semibold text-emerald-700 dark:text-emerald-400 cursor-default border-b border-dashed border-emerald-400/50">
                                  {formatCurrency(inc.totalReward)}
                                </span>
                              </HoverCardTrigger>
                              <HoverCardContent
                                side="left"
                                align="center"
                                className="w-auto p-3"
                                sideOffset={8}
                              >
                                <div className="space-y-2">
                                  <p className="text-xs font-semibold text-foreground">
                                    Reward Breakdown
                                  </p>
                                  <div className="space-y-1.5 text-xs">
                                    {[
                                      ...monetaryEntries,
                                      ...nonMonetaryEntries,
                                    ].map(([key, value]) => {
                                      const config = getCurrency(key);
                                      const Icon = config.icon;
                                      return (
                                        <div
                                          key={key}
                                          className="flex items-center justify-between gap-4"
                                        >
                                          <span className="flex items-center gap-1.5 text-muted-foreground">
                                            <Icon
                                              className={cn(
                                                "h-3 w-3",
                                                config.iconClass,
                                              )}
                                            />
                                            <span>{config.label}</span>
                                          </span>
                                          <span className="font-medium text-foreground">
                                            {config.format(value)}
                                          </span>
                                        </div>
                                      );
                                    })}
                                  </div>
                                </div>
                              </HoverCardContent>
                            </HoverCard>
                          </div>
                        );
                      })}
                    </div>
                  )}

                  {/* Ineligible */}
                  {ineligibleIncentives.length > 0 && (
                    <div className="space-y-1.5">
                      <span className="text-xs font-medium text-muted-foreground flex items-center gap-1.5">
                        <ShieldAlert className="h-3.5 w-3.5" />
                        Ineligible ({ineligibleIncentives.length})
                      </span>
                      {ineligibleIncentives.map((inc) => (
                        <div
                          key={inc.incentiveId}
                          className="flex items-start gap-2.5 rounded-lg border border-border bg-muted/30 px-3 py-2"
                        >
                          <Badge
                            variant="outline"
                            className="border-muted-foreground/30 text-muted-foreground text-xs mt-0.5 shrink-0"
                          >
                            Ineligible
                          </Badge>
                          <div className="flex flex-col gap-0.5">
                            <span className="text-sm font-medium text-muted-foreground">
                              {inc.incentiveName}
                            </span>
                            <span className="text-xs text-muted-foreground/80 flex items-center gap-1.5">
                              <AlertCircle className="h-3 w-3 shrink-0" />
                              {inc.reason}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </>
              )}
            </div>
          )}
        </div>

        {/* Admin Actions */}
        <div className="border-t border-border pt-4 space-y-4">
          <h3 className="font-semibold text-foreground">Admin Actions</h3>
          <div className="space-y-2">
            <Label htmlFor="comment">
              Comment <span className="text-destructive">*</span>
            </Label>
            <Textarea
              id="comment"
              value={adminComment}
              onChange={(e) => setAdminComment(e.target.value)}
              placeholder="Add a comment for this action..."
              className="min-h-[100px]"
            />
          </div>
        </div>
        <div className="flex gap-2 pt-4 border-t border-border">
          <Button variant="outline" className="flex-1" onClick={onClose}>
            Cancel
          </Button>
          {claim.status === "CLAIMED" && (
            <PermissionGate permission="action.claim.unclaim">
              <Button
                variant="destructive"
                className="flex-1 gap-1.5"
                onClick={handleUnclaim}
                disabled={unclaimMutation.isPending}
              >
                {unclaimMutation.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <UserMinus className="h-4 w-4" />
                )}
                Unclaim Deal
              </Button>
            </PermissionGate>
          )}
          <Button
            className="flex-1"
            onClick={handleSaveChanges}
            disabled={updateMutation.isPending}
          >
            {updateMutation.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : null}
            Save Changes
          </Button>
        </div>
      </div>
    </SheetContent>
  );
};

// Per-partner group card — memoized so that re-rendering the page shell
// (opening/closing filter popovers, toggling the edit drawer, typing in the
// search box when the result hasn't changed, etc.) doesn't re-render each
// partner group and its rows. The group only re-renders when the claims in
// that group, its own sort state, or the expanded set changes.
interface PartnerGroupProps {
  partner: string
  partnerClaims: ClaimResponse[]
  partnerSortState: { field: SortField; direction: SortDirection } | undefined
  expandedClaims: Set<string>
  onToggleExpand: (id: string) => void
  handleOpenEdit: (claim: ClaimResponse) => void
  onSort: (partner: string, field: SortField) => void
}

const PartnerGroup = memo(function PartnerGroup({
  partner,
  partnerClaims,
  partnerSortState,
  expandedClaims,
  onToggleExpand,
  handleOpenEdit,
  onSort,
}: PartnerGroupProps) {
  const region = partnerClaims[0]?.region
  const defaultBadgeColor =
    "bg-slate-100 text-slate-700 border-slate-300 dark:bg-slate-900/50 dark:text-slate-300 dark:border-slate-700"

  const { totalEarned, partnerEntries } = useMemo(() => {
    let total = 0
    const aggregated: Record<string, number> = {}
    for (const claim of partnerClaims) {
      total += claim.totalMonetaryReward
      if (claim.rewardBreakdown) {
        for (const [currency, amount] of Object.entries(
          claim.rewardBreakdown.monetary ?? {},
        )) {
          aggregated[currency] =
            (aggregated[currency] ?? 0) + (parseFloat(String(amount)) || 0)
        }
        for (const [currency, amount] of Object.entries(
          claim.rewardBreakdown.nonMonetary ?? {},
        )) {
          aggregated[currency] =
            (aggregated[currency] ?? 0) + (parseFloat(String(amount)) || 0)
        }
      }
    }
    const entries: Record<string, string> = {}
    for (const [k, v] of Object.entries(aggregated)) entries[k] = String(v)
    return { totalEarned: total, partnerEntries: entries }
  }, [partnerClaims])

  const getSortIcon = (field: SortField) => {
    if (!partnerSortState || partnerSortState.field !== field)
      return <ArrowUpDown className="ml-1 h-3 w-3 opacity-50" />
    return partnerSortState.direction === "asc" ? (
      <ArrowUp className="ml-1 h-3 w-3" />
    ) : (
      <ArrowDown className="ml-1 h-3 w-3" />
    )
  }

  return (
    <div className="rounded-lg border border-border shadow-sm overflow-hidden">
      <div className="bg-muted/50 px-4 py-4 border-b border-border">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Building2 className="h-5 w-5 text-muted-foreground" />
            <h3 className="text-lg font-semibold text-foreground">{partner}</h3>
            {region && (
              <Badge
                className={`text-xs font-medium px-2 py-0.5 border ${defaultBadgeColor}`}
              >
                {region}
              </Badge>
            )}
          </div>
          <span className="text-sm font-semibold text-emerald-600 dark:text-emerald-400">
            <SharedRewardBreakdownHover
              label="Earnings"
              entries={partnerEntries}
              monetaryTotal={totalEarned}
              suffix="earned"
              animate={false}
            />
          </span>
        </div>
      </div>
      <div className="relative">
        <Table className="table-fixed">
          <TableHeader>
            <TableRow>
              <TableHead className="w-[40px]"></TableHead>
              <TableHead className="w-[12%] text-xs font-medium">
                <button
                  onClick={() => onSort(partner, "date")}
                  className="flex items-center hover:text-foreground transition-colors"
                >
                  Date {getSortIcon("date")}
                </button>
              </TableHead>
              <TableHead className="w-[15%] text-xs font-medium">
                <button
                  onClick={() => onSort(partner, "claimer")}
                  className="flex items-center hover:text-foreground transition-colors"
                >
                  Claimer(s) {getSortIcon("claimer")}
                </button>
              </TableHead>
              <TableHead className="w-[20%] text-xs font-medium">
                Incentive
              </TableHead>
              <TableHead className="w-[15%] text-xs font-medium">
                <button
                  onClick={() => onSort(partner, "orderNumber")}
                  className="flex items-center hover:text-foreground transition-colors"
                >
                  Order# {getSortIcon("orderNumber")}
                </button>
              </TableHead>
              <TableHead className="w-[14%] text-xs font-medium">
                <button
                  onClick={() => onSort(partner, "amount")}
                  className="flex items-center hover:text-foreground transition-colors"
                >
                  Amount Earned {getSortIcon("amount")}
                </button>
              </TableHead>
              <TableHead className="w-[12%] text-xs font-medium">
                <button
                  onClick={() => onSort(partner, "status")}
                  className="flex items-center hover:text-foreground transition-colors"
                >
                  Status {getSortIcon("status")}
                </button>
              </TableHead>
              <TableHead className="w-[12%]"></TableHead>
            </TableRow>
          </TableHeader>
        </Table>

        {partnerClaims.length > 5 ? (
          <ScrollableClaimsTable
            claims={partnerClaims}
            expandedClaims={expandedClaims}
            onToggleExpand={onToggleExpand}
            handleOpenEdit={handleOpenEdit}
          />
        ) : (
          <Table className="table-fixed">
            <TableBody>
              {partnerClaims.map((claim) => (
                <ClaimRow
                  key={claim.id}
                  claim={claim}
                  isExpanded={expandedClaims.has(claim.id)}
                  toggleExpanded={onToggleExpand}
                  handleOpenEdit={handleOpenEdit}
                />
              ))}
            </TableBody>
          </Table>
        )}
      </div>
    </div>
  )
})

function ManageClaimsPage() {
  // Filter state
  const [dateFilter, setDateFilter] = useState<DateFilter>("recent");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [regionFilter, setRegionFilter] = useState<RegionFilter>("GLOBAL");
  const [customStartDate, setCustomStartDate] = useState<Date | undefined>();
  const [customEndDate, setCustomEndDate] = useState<Date | undefined>();
  const [partnerSearch, setPartnerSearch] = useState<string>("");
  const [selectedPartner, setSelectedPartner] = useState<{
    id: string;
    name: string;
  } | null>(null);
  const [showPartnerSuggestions, setShowPartnerSuggestions] = useState(false);
  const [poSearch, setPoSearch] = useState<string>("");
  const partnerSearchRef = useRef<HTMLDivElement>(null);

  // UI state
  const [selectedClaim, setSelectedClaim] = useState<ClaimResponse | null>(
    null,
  );
  const [expandedClaims, setExpandedClaims] = useState<Set<string>>(new Set());
  const [partnerSortState, setPartnerSortState] = useState<
    Record<string, { field: SortField; direction: SortDirection }>
  >({});

  // Build API params from filter state
  const listParams = useMemo<ClaimListParams>(() => {
    const params: ClaimListParams = {};

    if (statusFilter !== "all") {
      params.status = statusFilter;
    }
    if (poSearch.trim()) {
      params.search = poSearch.trim();
    }
    if (regionFilter !== "GLOBAL") {
      params.region = regionFilter;
    }
    if (selectedPartner) {
      params.partnerCompanyId = selectedPartner.id;
    }

    // Date range
    const now = new Date();
    switch (dateFilter) {
      case "recent":
        params.startDate = format(subDays(now, 30), "yyyy-MM-dd");
        params.endDate = format(now, "yyyy-MM-dd");
        break;
      case "quarter":
        params.startDate = format(startOfQuarter(now), "yyyy-MM-dd");
        params.endDate = format(now, "yyyy-MM-dd");
        break;
      case "year":
        params.startDate = format(startOfYear(now), "yyyy-MM-dd");
        params.endDate = format(now, "yyyy-MM-dd");
        break;
      case "custom":
        if (customStartDate)
          params.startDate = format(customStartDate, "yyyy-MM-dd");
        if (customEndDate) params.endDate = format(customEndDate, "yyyy-MM-dd");
        break;
    }

    return params;
  }, [
    statusFilter,
    poSearch,
    regionFilter,
    selectedPartner,
    dateFilter,
    customStartDate,
    customEndDate,
  ]);

  // Summary params (same as list but without pagination)
  const summaryParams = useMemo(() => {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { page, size, ...rest } = listParams as ClaimListParams & {
      page?: number;
      size?: number;
    };
    return rest;
  }, [listParams]);

  // API hooks
  const { data: claimsPage, isLoading, isError } = useClaims(listParams);
  const claims: ClaimResponse[] = useMemo(
    () => claimsPage?.data ?? [],
    [claimsPage?.data],
  );
  const { data: summary } = useClaimSummary(summaryParams);

  // Defer the expensive list data so filter trigger/selection paints
  // immediately — React commits the Select/trigger UI update first and
  // renders the partner groups with the stale list at transition priority.
  // Combined with `keepPreviousData` on the query, this means the filter
  // feels instant: the trigger updates, then the table repopulates.
  const deferredClaims = useDeferredValue(claims);
  const deferredSortState = useDeferredValue(partnerSortState);

  // Close partner suggestions on outside click
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        partnerSearchRef.current &&
        !partnerSearchRef.current.contains(event.target as Node)
      ) {
        setShowPartnerSuggestions(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const toggleExpanded = useCallback((id: string) => {
    setExpandedClaims((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  // Get unique partners from current claims for autocomplete
  const partnersWithCounts = useMemo(() => {
    const partnerMap: Record<
      string,
      { id: string; name: string; count: number }
    > = {};
    claims.forEach((claim) => {
      if (!partnerMap[claim.partnerCompanyId]) {
        partnerMap[claim.partnerCompanyId] = {
          id: claim.partnerCompanyId,
          name: claim.partnerCompanyName,
          count: 0,
        };
      }
      partnerMap[claim.partnerCompanyId]!.count += 1;
    });
    return Object.values(partnerMap);
  }, [claims]);

  const filteredPartnerSuggestions = useMemo(
    () =>
      partnersWithCounts.filter((partner) =>
        partner.name.toLowerCase().includes(partnerSearch.toLowerCase()),
      ),
    [partnersWithCounts, partnerSearch],
  );

  // Sort helper — stable callback so the memoized PartnerGroup component
  // doesn't re-render on every parent render just because its prop identity
  // changed.
  const handleSort = useCallback((partner: string, field: SortField) => {
    setPartnerSortState((prev) => {
      const current = prev[partner] || { field: "date", direction: "desc" };
      if (current.field === field) {
        return {
          ...prev,
          [partner]: {
            field,
            direction: current.direction === "asc" ? "desc" : "asc",
          },
        };
      }
      return { ...prev, [partner]: { field, direction: "asc" } };
    });
  }, []);

  const handleOpenEdit = useCallback((claim: ClaimResponse) => {
    setSelectedClaim(claim);
  }, []);

  const handleCloseEdit = useCallback(() => {
    setSelectedClaim(null);
  }, []);

  // Group + sort claims by partner. Pure function derived from deferred
  // inputs, so when the user flips a filter the heavy grouping + sorting
  // work runs at transition priority (not in the interaction's critical
  // path).
  const groupedClaims = useMemo(() => {
    const statusOrder: Record<ClaimStatus, number> = {
      CLAIMED: 0,
      UNCLAIMED: 1,
    };
    const sortClaims = (claimsToSort: ClaimResponse[], partner: string) => {
      const sortState = deferredSortState[partner];
      if (!sortState) {
        return [...claimsToSort].sort(
          (a, b) =>
            new Date(b.orderDate).getTime() - new Date(a.orderDate).getTime(),
        );
      }
      const { field: sortField, direction: sortDirection } = sortState;
      return [...claimsToSort].sort((a, b) => {
        let comparison = 0;
        switch (sortField) {
          case "date":
            comparison =
              new Date(a.orderDate).getTime() -
              new Date(b.orderDate).getTime();
            break;
          case "claimer":
            comparison = (a.claimers[0]?.name ?? "").localeCompare(
              b.claimers[0]?.name ?? "",
            );
            break;
          case "orderNumber":
            comparison = a.orderNumber.localeCompare(b.orderNumber);
            break;
          case "amount":
            comparison = a.totalMonetaryReward - b.totalMonetaryReward;
            break;
          case "status":
            comparison = statusOrder[a.status] - statusOrder[b.status];
            break;
        }
        return sortDirection === "asc" ? comparison : -comparison;
      });
    };

    const grouped = deferredClaims.reduce(
      (acc, claim) => {
        const key = claim.partnerCompanyName;
        if (!acc[key]) acc[key] = [];
        acc[key].push(claim);
        return acc;
      },
      {} as Record<string, ClaimResponse[]>,
    );
    Object.keys(grouped).forEach((partner) => {
      grouped[partner] = sortClaims(grouped[partner] ?? [], partner);
    });
    return grouped;
  }, [deferredClaims, deferredSortState]);

  // Regions now come from the location hierarchy via LocationFilter component

  return (
    <div className="space-y-6">
      {/* Header */}
      <PageBanner
        theme="claims"
        title="Manage Claims"
        subtitle="View and manage all partner claim submissions"
      />
      <Card className="border" data-tour="claims-table">
        <CardHeader>
          <div className="flex items-start justify-between gap-4">
            {/* Left: Title + Total Earnings */}
            <div className="flex items-center gap-3 shrink-0">
              <ClipboardList className="h-6 w-6 text-muted-foreground" />
              <div>
                <CardTitle className="text-foreground">All Claims</CardTitle>
                <CardDescription className="mt-1">
                  Review and Manage Partner Claims
                </CardDescription>
              </div>
              <div className="flex items-center gap-2 bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-800 rounded-lg px-4 py-2 ml-1">
                <span className="text-sm font-medium text-emerald-700 dark:text-emerald-300">
                  Total Earnings:
                </span>
                <span className="text-lg font-semibold text-emerald-600 dark:text-emerald-400">
                  <SharedRewardBreakdownHover
                    label="Total Earnings"
                    entries={summary?.currencyBreakdown ?? {}}
                    monetaryTotal={
                      summary ? parseFloat(summary.totalEarnings) || 0 : 0
                    }
                    spins={6}
                  />
                </span>
              </div>
            </div>

            {/* Right: Filters — wrap into rows, staying on the right */}
            <div className="flex flex-wrap items-center justify-end gap-3">
              {/* Search */}
              <div className="relative" ref={partnerSearchRef}>
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                  <input
                    type="text"
                    placeholder="Search partner or PO#..."
                    value={selectedPartner?.name || partnerSearch || poSearch}
                    onChange={(e) => {
                      const val = e.target.value;
                      if (/^(PO|po|#|\d)/.test(val) && !selectedPartner) {
                        setPoSearch(val);
                        setPartnerSearch("");
                      } else {
                        setPartnerSearch(val);
                        setPoSearch("");
                        setSelectedPartner(null);
                        setShowPartnerSuggestions(true);
                      }
                    }}
                    onFocus={() => {
                      if (!selectedPartner && !poSearch)
                        setShowPartnerSuggestions(true);
                    }}
                    className="h-9 w-[240px] rounded-md border border-input bg-background pl-9 pr-8 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
                  />
                  {(selectedPartner || partnerSearch || poSearch) && (
                    <button
                      onClick={() => {
                        setPartnerSearch("");
                        setPoSearch("");
                        setSelectedPartner(null);
                        setShowPartnerSuggestions(false);
                      }}
                      className="absolute right-2 top-1/2 -translate-y-1/2 p-0.5 rounded-sm hover:bg-muted"
                    >
                      <X className="h-3.5 w-3.5 text-muted-foreground" />
                    </button>
                  )}
                </div>
                {showPartnerSuggestions &&
                  partnerSearch &&
                  !selectedPartner && (
                    <div className="absolute top-full left-0 right-0 mt-1 z-50 rounded-md border bg-popover shadow-md">
                      <Command className="rounded-md">
                        <CommandList>
                          {filteredPartnerSuggestions.length === 0 ? (
                            <CommandEmpty className="py-3 text-center text-sm text-muted-foreground">
                              No partners found
                            </CommandEmpty>
                          ) : (
                            <CommandGroup>
                              {filteredPartnerSuggestions
                                .slice(0, 5)
                                .map((partner) => (
                                  <CommandItem
                                    key={partner.id}
                                    onSelect={() => {
                                      setSelectedPartner({
                                        id: partner.id,
                                        name: partner.name,
                                      });
                                      setPartnerSearch("");
                                      setShowPartnerSuggestions(false);
                                    }}
                                    className="cursor-pointer group"
                                  >
                                    <Building2 className="mr-2 h-4 w-4 text-muted-foreground group-data-[selected=true]:text-accent-foreground" />
                                    <span>{partner.name}</span>
                                    <span className="ml-auto text-xs text-muted-foreground group-data-[selected=true]:text-accent-foreground">
                                      {partner.count} claims
                                    </span>
                                  </CommandItem>
                                ))}
                            </CommandGroup>
                          )}
                        </CommandList>
                      </Command>
                    </div>
                  )}
              </div>

              <div className="w-px h-5 bg-border" />

              <Select
                value={statusFilter}
                onValueChange={(value) =>
                  setStatusFilter(value as StatusFilter)
                }
              >
                <SelectTrigger className="w-[130px] h-9">
                  <SelectValue placeholder="All Statuses" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All Statuses</SelectItem>
                  <SelectItem value="CLAIMED">Claimed</SelectItem>
                  <SelectItem value="UNCLAIMED">Unclaimed</SelectItem>
                </SelectContent>
              </Select>

              <div className="w-px h-5 bg-border" />

              {/* Date Filter */}
              <Select
                value={dateFilter}
                onValueChange={(v) => setDateFilter(v as DateFilter)}
              >
                <SelectTrigger className="h-9 w-[160px] text-sm border-border">
                  <CalendarIcon className="h-3.5 w-3.5 mr-1.5 text-muted-foreground shrink-0" />
                  <SelectValue>
                    {dateFilter === "recent" && "Last 30 Days"}
                    {dateFilter === "quarter" && "This Quarter"}
                    {dateFilter === "year" && "This Year"}
                    {dateFilter === "custom" &&
                      (customStartDate && customEndDate
                        ? `${format(customStartDate, "MMM d")} – ${format(customEndDate, "MMM d")}`
                        : "Custom")}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="recent">Last 30 Days</SelectItem>
                  <SelectItem value="quarter">This Quarter</SelectItem>
                  <SelectItem value="year">This Year</SelectItem>
                  <SelectItem value="custom">Custom</SelectItem>
                </SelectContent>
              </Select>
              {dateFilter === "custom" && (
                <div className="flex items-center gap-1.5">
                  <Popover>
                    <PopoverTrigger asChild>
                      <Button
                        variant="outline"
                        size="sm"
                        className={cn(
                          "h-9 text-sm font-normal gap-1.5",
                          !customStartDate && "text-muted-foreground",
                        )}
                      >
                        <CalendarIcon className="h-3.5 w-3.5" />
                        {customStartDate
                          ? format(customStartDate, "MMM d")
                          : "Start"}
                      </Button>
                    </PopoverTrigger>
                    <PopoverContent className="w-auto p-0 z-[60]" align="end">
                      <Calendar
                        mode="single"
                        selected={customStartDate}
                        onSelect={setCustomStartDate}
                        initialFocus
                        className="p-3 pointer-events-auto"
                      />
                    </PopoverContent>
                  </Popover>
                  <span className="text-xs text-muted-foreground">&ndash;</span>
                  <Popover>
                    <PopoverTrigger asChild>
                      <Button
                        variant="outline"
                        size="sm"
                        className={cn(
                          "h-9 text-sm font-normal gap-1.5",
                          !customEndDate && "text-muted-foreground",
                        )}
                      >
                        <CalendarIcon className="h-3.5 w-3.5" />
                        {customEndDate ? format(customEndDate, "MMM d") : "End"}
                      </Button>
                    </PopoverTrigger>
                    <PopoverContent className="w-auto p-0 z-[60]" align="end">
                      <Calendar
                        mode="single"
                        selected={customEndDate}
                        onSelect={setCustomEndDate}
                        initialFocus
                        className="p-3 pointer-events-auto"
                      />
                    </PopoverContent>
                  </Popover>
                </div>
              )}

              <div className="w-px h-5 bg-border" />

              {/* Region Filter */}
              <LocationFilter
                value={regionFilter}
                onChange={setRegionFilter}
                className="h-9 w-[140px] text-sm border-border"
              />
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-8">
          {isLoading || !summary ? (
            <PartnerGroupedClaimsSkeleton />
          ) : isError ? (
            <div className="text-center py-12 text-destructive">
              <p>Failed to load claims. Please try again.</p>
            </div>
          ) : Object.keys(groupedClaims).length === 0 ? (
            <div className="text-center py-12 text-muted-foreground">
              <p>No claims found for the selected filters.</p>
            </div>
          ) : (
            (Object.entries(groupedClaims) as [string, ClaimResponse[]][]).map(
              ([partner, partnerClaims]) => (
                <PartnerGroup
                  key={partner}
                  partner={partner}
                  partnerClaims={partnerClaims}
                  partnerSortState={deferredSortState[partner]}
                  expandedClaims={expandedClaims}
                  onToggleExpand={toggleExpanded}
                  handleOpenEdit={handleOpenEdit}
                  onSort={handleSort}
                />
              ),
            )
          )}
        </CardContent>
      </Card>

      {/* Edit Claim Side Panel */}
      <Sheet
        open={!!selectedClaim}
        onOpenChange={(open) => !open && handleCloseEdit()}
      >
        {selectedClaim && (
          <ClaimEditDrawer claim={selectedClaim} onClose={handleCloseEdit} />
        )}
      </Sheet>
    </div>
  );
}

export default ManageClaimsPage;
