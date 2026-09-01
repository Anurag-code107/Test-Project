import { useState, useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import { format, subDays, startOfQuarter, startOfYear } from "date-fns";
import { PageBanner } from "@/components/PageBanner";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
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
import { CalendarIcon, ClipboardList, Gift } from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/hooks/useAuth";
import {
  useClaims,
  useClaimSummary,
  useRewardBalances,
  useRewardTransactions,
} from "@/hooks/useClaimApi";
import type {
  ClaimListParams,
  RewardTransactionListParams,
} from "@/types/claim.types";
import { ClaimsTable } from "@/components/ClaimsTable";
import { RewardBalancesPanel } from "@/components/RewardBalancesPanel";

type DateFilter = "recent" | "quarter" | "year" | "custom";

export default function ManageRewardsPage() {
  const [searchParams] = useSearchParams();
  const initialTab =
    searchParams.get("tab") === "rewards" ? "rewards" : "claims";
  const { user } = useAuth();

  const [dateFilter, setDateFilter] = useState<DateFilter>("recent");
  const [customStartDate, setCustomStartDate] = useState<Date | undefined>();
  const [customEndDate, setCustomEndDate] = useState<Date | undefined>();
  // Build API params from date filter
  const apiParams = useMemo<ClaimListParams>(() => {
    const params: ClaimListParams = {};
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
  }, [dateFilter, customStartDate, customEndDate]);

  const { data: claimsPage, isLoading: claimsLoading } = useClaims(apiParams);
  const { data: summary } = useClaimSummary(apiParams);
  const { data: rewardBalances } = useRewardBalances();

  const transactionParams = useMemo<RewardTransactionListParams>(
    () => ({
      ...(apiParams.startDate ? { startDate: apiParams.startDate } : {}),
      ...(apiParams.endDate ? { endDate: apiParams.endDate } : {}),
    }),
    [apiParams.startDate, apiParams.endDate],
  );
  const { data: transactionsPage, isLoading: transactionsLoading } =
    useRewardTransactions(transactionParams);

  return (
    <div className="space-y-6">
      {/* Header */}
      <PageBanner
        theme="rewards"
        title="Manage Rewards"
        subtitle="View your claims, earnings, and reward activity"
      />

      <Tabs defaultValue={initialTab} className="space-y-4">
        {/* Tabs left, date filter right — same row */}
        <div className="flex items-center justify-between">
          <TabsList>
            <TabsTrigger value="claims" className="gap-2">
              <ClipboardList className="h-4 w-4" />
              Claims
            </TabsTrigger>
            <TabsTrigger
              value="rewards"
              className="gap-2"
              data-tour="tab-rewards"
            >
              <Gift className="h-4 w-4" />
              Rewards
            </TabsTrigger>
          </TabsList>

          <div className="flex items-center gap-3">
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
          </div>
        </div>

        <TabsContent value="claims" data-tour="claims-table">
          <ClaimsTable
            claimsPage={claimsPage}
            claimsLoading={claimsLoading}
            summary={summary}
            currentUserId={user?.id}
            cardDescription="Your Claimable PO Numbers And Claim History"
          />
        </TabsContent>

        <TabsContent value="rewards" data-tour="rewards-tab-content">
          <RewardBalancesPanel
            rewardBalances={rewardBalances}
            transactions={transactionsPage?.data}
            totalTransactionCount={transactionsPage?.totalElements}
            transactionsLoading={transactionsLoading}
          />
        </TabsContent>
      </Tabs>
    </div>
  );
}
