import { useState, useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import { format, subDays, startOfQuarter, startOfYear } from "date-fns";
import { PageBanner } from "@/components/PageBanner";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Calendar } from "@/components/ui/calendar";
import { CalendarIcon, ClipboardList, Gift, Users, User } from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/hooks/useAuth";
import {
  useClaims,
  useClaimSummary,
  useRewardBalances,
  useRewardTransactions,
} from "@/hooks/useClaimApi";
import { useUsers } from "@/hooks/useApi";
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
  const { user: authUser } = useAuth();

  const [dateFilter, setDateFilter] = useState<DateFilter>("recent");
  const [customStartDate, setCustomStartDate] = useState<Date | undefined>();
  const [customEndDate, setCustomEndDate] = useState<Date | undefined>();
  const [userScope, setUserScope] = useState<string>("all");
  // Compute date range params
  const dateParams = useMemo(() => {
    const now = new Date();
    switch (dateFilter) {
      case "recent":
        return { startDate: format(subDays(now, 30), "yyyy-MM-dd") };
      case "quarter":
        return { startDate: format(startOfQuarter(now), "yyyy-MM-dd") };
      case "year":
        return { startDate: format(startOfYear(now), "yyyy-MM-dd") };
      case "custom":
        return {
          ...(customStartDate
            ? { startDate: format(customStartDate, "yyyy-MM-dd") }
            : {}),
          ...(customEndDate
            ? { endDate: format(customEndDate, "yyyy-MM-dd") }
            : {}),
        };
      default:
        return {};
    }
  }, [dateFilter, customStartDate, customEndDate]);

  // Compute userId param based on scope
  const userIdParam = useMemo(() => {
    if (userScope === "all") return undefined;
    if (userScope === "me") return authUser?.id;
    return userScope;
  }, [userScope, authUser]);

  // API queries
  const claimParams = useMemo<ClaimListParams>(
    () => ({
      ...dateParams,
      ...(userIdParam ? { userId: userIdParam } : {}),
    }),
    [dateParams, userIdParam],
  );

  const { data: claimsPage, isLoading: claimsLoading } = useClaims(claimParams);
  const { data: summary } = useClaimSummary(claimParams);
  const { data: rewardBalances } = useRewardBalances();

  // Reward transactions are scoped per-user. The Partner Admin's "All Company"
  // / "Just Me" / "Specific Member" selector filters Claims by user, but the
  // current /api/v1/reward-transactions endpoint is per-user only — so when
  // userScope is "all" we fall back to the Partner Admin's own transactions.
  // A future "company-wide transactions" endpoint can plug in here.
  const transactionParams = useMemo<RewardTransactionListParams>(
    () => ({ ...dateParams }),
    [dateParams],
  );
  const { data: transactionsPage, isLoading: transactionsLoading } =
    useRewardTransactions(transactionParams);

  // Fetch team members for scope dropdown
  const { data: usersPage } = useUsers({ pageSize: 100 });
  const teamMembers = useMemo(() => {
    if (!usersPage?.data) return [];
    return usersPage.data.filter((u) => u.id !== authUser?.id);
  }, [usersPage, authUser]);

  const cardDescription = useMemo(() => {
    if (userScope === "all") return "Company-Wide Claims And PO History";
    if (userScope === "me")
      return "Your Claimable PO Numbers And Claim History";
    return "Claims For Selected Team Member";
  }, [userScope]);

  return (
    <Tabs defaultValue={initialTab} className="space-y-6">
      {/* Header */}
      <div>
        <PageBanner
          theme="rewards"
          title="Manage Rewards"
          subtitle="View your claims, earnings, and reward activity"
        />

        {/* Filters row — tabs left, company + date right */}
        <div className="flex items-center justify-between mt-5">
          {/* Segmented control (left side) */}
          <TabsList>
            <TabsTrigger value="claims" className="gap-2">
              <ClipboardList className="h-4 w-4" />
              Claims
            </TabsTrigger>
            <TabsTrigger value="rewards" className="gap-2">
              <Gift className="h-4 w-4" />
              Rewards
            </TabsTrigger>
          </TabsList>

          {/* Company + Date filters (right side) */}
          <div className="flex items-center gap-3">
            {/* User Scope Filter */}
            <Select value={userScope} onValueChange={setUserScope}>
              <SelectTrigger className="w-[180px] h-9 rounded-lg border-border bg-background text-sm">
                <div className="flex items-center gap-2">
                  {userScope === "all" ? (
                    <Users className="h-4 w-4 text-muted-foreground" />
                  ) : (
                    <User className="h-4 w-4 text-muted-foreground" />
                  )}
                  <SelectValue placeholder="All Company" />
                </div>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Company</SelectItem>
                <SelectItem value="me">Just Me</SelectItem>
                {teamMembers.map((m) => (
                  <SelectItem key={m.id} value={m.id}>
                    {m.firstName} {m.lastName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <div className="w-px h-5 bg-border" />

            {/* Date Filter */}
            <Select
              value={dateFilter}
              onValueChange={(v) => setDateFilter(v as DateFilter)}
            >
              <SelectTrigger className="h-9 w-[160px] text-sm border-border bg-background rounded-lg">
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
                        "h-9 text-sm font-normal gap-1.5 rounded-lg border-border",
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
                        "h-9 text-sm font-normal gap-1.5 rounded-lg border-border",
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
      </div>

      <TabsContent value="claims">
        <ClaimsTable
          claimsPage={claimsPage}
          claimsLoading={claimsLoading}
          summary={summary}
          currentUserId={authUser?.id}
          cardDescription={cardDescription}
        />
      </TabsContent>

      <TabsContent value="rewards">
        <RewardBalancesPanel
          rewardBalances={rewardBalances}
          transactions={transactionsPage?.data}
          totalTransactionCount={transactionsPage?.totalElements}
          transactionsLoading={transactionsLoading}
        />
      </TabsContent>
    </Tabs>
  );
}
