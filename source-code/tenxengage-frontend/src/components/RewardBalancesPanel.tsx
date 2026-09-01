import { useState, useMemo, useRef, useCallback } from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  DollarSign,
  Sparkles,
  History,
  ArrowDownLeft,
  ArrowUpRight,
  Ticket,
  Banknote,
  ShoppingBag,
  CreditCard,
  Music,
  Hotel,
  Car,
  Wallet,
  GraduationCap,
} from "lucide-react";
import { cn } from "@/lib/utils";
import {
  getCurrency,
  monetaryCurrencyIds,
  nonMonetaryCurrencyIds,
  currencyIds,
} from "@/config/currencies";
import type {
  RewardBalanceResponse,
  RewardTransactionResponse,
} from "@/types/claim.types";

// ─── Spend Categories ────────────────────────────────────────────────────────
//
// Spend / redemption is not modelled on the backend yet — no row will come back
// with `type: "spent"` today. The UI scaffolding (Type filter, ArrowUpRight
// direction, spend-category icons/labels, spend-detail subtitle) is kept here
// so the future redemption feature can populate it without rebuilding the table.

type SpendCategory =
  | "bank_transfer"
  | "merchandise"
  | "gift_card"
  | "prepaid_visa"
  | "concert"
  | "hotel"
  | "car_rental"
  | "raffle_entry"
  | "training_course"
  | "other";

const spendCategoryIcons: Record<SpendCategory, typeof Banknote> = {
  bank_transfer: Banknote,
  merchandise: ShoppingBag,
  gift_card: CreditCard,
  prepaid_visa: CreditCard,
  concert: Music,
  hotel: Hotel,
  car_rental: Car,
  raffle_entry: Ticket,
  training_course: GraduationCap,
  other: Wallet,
};

const spendCategoryLabels: Record<SpendCategory, string> = {
  bank_transfer: "Bank Transfer",
  merchandise: "Merchandise",
  gift_card: "Gift Card",
  prepaid_visa: "Prepaid Visa",
  concert: "Concert/Event",
  hotel: "Hotel",
  car_rental: "Car Rental",
  raffle_entry: "Raffle Entry",
  training_course: "Training Course",
  other: "Other",
};

// Future redemption rows will carry these optional fields. Left as a separate
// shape from RewardTransactionResponse since they are not in the contract yet.
interface SpendTransactionExtras {
  spendCategory?: SpendCategory;
  spendDetail?: string;
}

type DisplayTransaction = RewardTransactionResponse & SpendTransactionExtras;

// ─── Balance Card ────────────────────────────────────────────────────────────

function BalanceCard({
  currencyId,
  balance,
}: {
  currencyId: string;
  balance: string;
}) {
  const config = getCurrency(currencyId);
  const Icon = config.icon;
  const ActionIcon = config.action.icon;
  const amount = Number(balance);

  return (
    <Card className={cn("border", config.borderClass, config.bgClass)}>
      <CardContent className="px-4 py-3 flex items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className={cn("p-2 rounded-lg", config.iconBgClass)}>
            <Icon className={cn("h-4 w-4", config.iconClass)} />
          </div>
          <span className="text-sm font-medium text-muted-foreground">
            {config.label} Balance
          </span>
          <span
            className={cn(
              "text-lg font-semibold",
              amount > 0 ? config.amountClass : "text-muted-foreground",
            )}
          >
            {config.rewardFormat(balance)}
          </span>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <Button variant="outline" size="sm" className="gap-1.5 text-xs">
            <ActionIcon className="h-3.5 w-3.5" />
            {config.action.label}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

// ─── Main Panel ──────────────────────────────────────────────────────────────

interface RewardBalancesPanelProps {
  rewardBalances: RewardBalanceResponse[] | undefined;
  transactions: RewardTransactionResponse[] | undefined;
  totalTransactionCount?: number;
  transactionsLoading?: boolean;
}

type TransactionFilter = "all" | "earned" | "spent";
type CategoryFilter = "all" | string;

export function RewardBalancesPanel({
  rewardBalances,
  transactions,
  totalTransactionCount,
  transactionsLoading = false,
}: RewardBalancesPanelProps) {
  const [typeFilter, setTypeFilter] = useState<TransactionFilter>("all");
  const [categoryFilter, setCategoryFilter] = useState<CategoryFilter>("all");
  const [scrollProgress, setScrollProgress] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null);

  const handleScroll = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    const { scrollTop, scrollHeight, clientHeight } = el;
    const maxScroll = scrollHeight - clientHeight;
    setScrollProgress(maxScroll > 0 ? scrollTop / maxScroll : 0);
  }, []);

  const balanceMap = useMemo(() => {
    if (!rewardBalances) return new Map<string, string>();
    return new Map(rewardBalances.map((b) => [b.currencyId, b.balance]));
  }, [rewardBalances]);

  // If the user has earned anything in any currency, render the full four-currency
  // set so Cash / Points / Raffle Tickets / Training Credits all show up side by side
  // — with $0 / 0 for whichever ones haven't accrued yet. Keeps the panel a coherent
  // board instead of hiding currencies just because the backend hasn't created a
  // reward_balances row for them yet.
  const hasAnyPositiveBalance = useMemo(() => {
    if (!rewardBalances) return false;
    return rewardBalances.some((b) => Number(b.balance) > 0);
  }, [rewardBalances]);

  const monetaryBalances = hasAnyPositiveBalance ? monetaryCurrencyIds : [];
  const nonMonetaryBalances = hasAnyPositiveBalance ? nonMonetaryCurrencyIds : [];
  const hasBalances = hasAnyPositiveBalance;

  const filteredTransactions = useMemo<DisplayTransaction[]>(() => {
    const rows = (transactions ?? []) as DisplayTransaction[];
    return rows.filter((txn) => {
      if (typeFilter !== "all" && txn.type !== typeFilter) return false;
      if (categoryFilter !== "all" && txn.currencyId !== categoryFilter)
        return false;
      return true;
    });
  }, [transactions, typeFilter, categoryFilter]);

  const totalCount = totalTransactionCount ?? transactions?.length ?? 0;

  const formatDate = (iso: string) =>
    new Date(iso).toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    });

  return (
    <div className="space-y-6">
      {/* Balance Summary — two columns */}
      {hasBalances ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Monetary Rewards */}
          {monetaryBalances.length > 0 && (
            <Card className="border">
              <CardHeader className="pb-3">
                <div className="flex items-center gap-2">
                  <DollarSign className="h-5 w-5 text-muted-foreground" />
                  <CardTitle className="text-foreground text-base">
                    Monetary Rewards
                  </CardTitle>
                </div>
              </CardHeader>
              <CardContent>
                <div className="space-y-2">
                  {monetaryBalances.map((id) => (
                    <BalanceCard
                      key={id}
                      currencyId={id}
                      balance={balanceMap.get(id) ?? "0"}
                    />
                  ))}
                </div>
              </CardContent>
            </Card>
          )}

          {/* Non-Monetary Rewards */}
          {nonMonetaryBalances.length > 0 && (
            <Card className="border">
              <CardHeader className="pb-3">
                <div className="flex items-center gap-2">
                  <Sparkles className="h-5 w-5 text-muted-foreground" />
                  <CardTitle className="text-foreground text-base">
                    Non-Monetary Rewards
                  </CardTitle>
                </div>
              </CardHeader>
              <CardContent>
                <div className="space-y-2">
                  {nonMonetaryBalances.map((id) => (
                    <BalanceCard
                      key={id}
                      currencyId={id}
                      balance={balanceMap.get(id) ?? "0"}
                    />
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      ) : (
        <Card className="border">
          <CardContent className="py-8">
            <div className="text-center text-muted-foreground">
              <p>No reward balances found.</p>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Transaction History */}
      <Card className="border" data-tour="transaction-history">
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <History className="h-5 w-5 text-muted-foreground" />
              <div>
                <CardTitle className="text-foreground">
                  Transaction History
                </CardTitle>
                <CardDescription className="mt-1">
                  Your Reward Earnings And Claim Activity
                </CardDescription>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <Select
                value={typeFilter}
                onValueChange={(v) => setTypeFilter(v as TransactionFilter)}
              >
                <SelectTrigger className="w-[140px]">
                  <SelectValue placeholder="All Types" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All Types</SelectItem>
                  <SelectItem value="earned">Earned</SelectItem>
                  <SelectItem value="spent">Spent</SelectItem>
                </SelectContent>
              </Select>

              <Select value={categoryFilter} onValueChange={setCategoryFilter}>
                <SelectTrigger className="w-[150px]">
                  <SelectValue placeholder="All Categories" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All Categories</SelectItem>
                  {currencyIds.map((id) => {
                    const c = getCurrency(id);
                    return (
                      <SelectItem key={id} value={id}>
                        {c.label}
                      </SelectItem>
                    );
                  })}
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {transactionsLoading && filteredTransactions.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground">
              <p>Loading transactions…</p>
            </div>
          ) : filteredTransactions.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground">
              <p>No transactions found for the selected filter.</p>
            </div>
          ) : (
            <div className="rounded-lg border border-border overflow-hidden">
              <div
                ref={scrollRef}
                onScroll={handleScroll}
                className="max-h-[calc(100vh-420px)] min-h-[200px] overflow-y-auto"
              >
                <Table>
                  <TableHeader className="sticky top-0 bg-background z-10">
                    <TableRow>
                      <TableHead className="w-[50px]"></TableHead>
                      <TableHead>Date</TableHead>
                      <TableHead>Description</TableHead>
                      <TableHead>Category</TableHead>
                      <TableHead>Type</TableHead>
                      <TableHead className="text-right">Amount</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredTransactions.map((txn) => {
                      const isEarned = txn.type === "earned";
                      const config = getCurrency(txn.currencyId);
                      const CatIcon = config.icon;
                      const SpendIcon = txn.spendCategory
                        ? spendCategoryIcons[txn.spendCategory]
                        : Wallet;
                      const amountNumber = Number(txn.amount);
                      const description = isEarned
                        ? `${config.label} reward from ${txn.incentiveName} claim`
                        : (txn.spendDetail ?? "Reward redemption");

                      return (
                        <TableRow key={txn.id}>
                          {/* Direction icon */}
                          <TableCell className="px-3">
                            <div
                              className={cn(
                                "p-1.5 rounded-lg w-fit",
                                isEarned
                                  ? "bg-emerald-100 dark:bg-emerald-900/50"
                                  : "bg-orange-100 dark:bg-orange-900/50",
                              )}
                            >
                              {isEarned ? (
                                <ArrowDownLeft className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                              ) : (
                                <ArrowUpRight className="h-4 w-4 text-orange-600 dark:text-orange-400" />
                              )}
                            </div>
                          </TableCell>
                          {/* Date */}
                          <TableCell className="text-muted-foreground whitespace-nowrap">
                            {formatDate(txn.date)}
                          </TableCell>
                          {/* Description */}
                          <TableCell>
                            <div className="space-y-0.5">
                              <p className="text-sm font-medium text-foreground">
                                {description}
                              </p>
                              {isEarned && txn.purchaseOrderNumber && (
                                <p className="text-xs text-muted-foreground">
                                  {txn.purchaseOrderNumber} · {txn.incentiveName}
                                </p>
                              )}
                              {txn.spendDetail && (
                                <p className="text-xs text-muted-foreground flex items-center gap-1">
                                  <SpendIcon className="h-3 w-3" />
                                  {txn.spendDetail}
                                </p>
                              )}
                            </div>
                          </TableCell>
                          {/* Category badge */}
                          <TableCell>
                            <Badge
                              variant="outline"
                              className={cn(
                                "gap-1",
                                config.borderClass,
                                config.bgClass,
                                config.amountClass,
                              )}
                            >
                              <CatIcon className="h-3 w-3" />
                              {config.label}
                            </Badge>
                          </TableCell>
                          {/* Type badge */}
                          <TableCell>
                            {txn.spendCategory ? (
                              <Badge
                                variant="secondary"
                                className="text-xs gap-1"
                              >
                                <SpendIcon className="h-3 w-3" />
                                {spendCategoryLabels[txn.spendCategory]}
                              </Badge>
                            ) : isEarned ? (
                              <Badge
                                variant="secondary"
                                className="text-xs gap-1"
                              >
                                <Ticket className="h-3 w-3" />
                                Claim Reward
                              </Badge>
                            ) : null}
                          </TableCell>
                          {/* Amount */}
                          <TableCell className="text-right">
                            <span
                              className={cn(
                                "font-semibold",
                                isEarned
                                  ? "text-emerald-600 dark:text-emerald-400"
                                  : "text-orange-600 dark:text-orange-400",
                              )}
                            >
                              {isEarned ? "+" : "-"}
                              {config.rewardFormat(Math.abs(amountNumber))}
                            </span>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
              {/* Footer */}
              <div className="flex items-center justify-between px-4 py-2 border-t border-border bg-muted/30 text-xs text-muted-foreground">
                <span>
                  Showing {filteredTransactions.length} of {totalCount}{" "}
                  transactions
                </span>
                <span>{Math.round(scrollProgress * 100)}% scrolled</span>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
