// Adapted from: src/pages/client-admin/MyProfilePage.tsx (Card structure)
// Read-only aggregate at-risk totals shown beside the policy form (AC-7).
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { AlertCircle, Clock } from "lucide-react";
import { cn } from "@/lib/utils";
import { getCurrency } from "@/config/currencies";
import { formatDate } from "@/utils/formatters";
import { useExpiringSoon } from "@/hooks/useExpiringSoon";

interface ExpiringSoonPreviewCardProps {
  withinDays?: number;
  currencyId?: string;
}

/**
 * ExpiringSoonPreviewCard
 *
 * Covers AC-7 — read-only per-currency at-risk totals.
 * Refetches when useUpsertBalanceExpirationPolicy invalidates
 * ['balance-expiring-soon', clientId].
 */
export function ExpiringSoonPreviewCard({
  withinDays,
  currencyId,
}: ExpiringSoonPreviewCardProps) {
  const { data, isLoading, isError, refetch } = useExpiringSoon({
    withinDays,
    currencyId,
  });

  return (
    <Card className="border border-border">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-2">
          <Clock className="h-4 w-4 text-warning" aria-hidden="true" />
          <CardTitle className="text-sm font-medium">Expiring Soon</CardTitle>
        </div>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div
            role="status"
            aria-busy="true"
            aria-label="Loading expiring-soon preview"
            className="space-y-3"
          >
            {[1, 2].map((i) => (
              <div key={i} className="flex justify-between items-center">
                <Skeleton className="h-4 w-24 rounded" />
                <Skeleton className="h-4 w-16 rounded" />
              </div>
            ))}
          </div>
        ) : isError ? (
          <div
            role="alert"
            aria-live="assertive"
            className="flex flex-col items-center gap-2 py-4 text-center"
          >
            <AlertCircle className="h-5 w-5 text-destructive" aria-hidden="true" />
            <p className="text-sm text-destructive">Could not load expiring-soon data</p>
            <Button variant="outline" size="sm" onClick={() => refetch()}>
              Try again
            </Button>
          </div>
        ) : !data || data.length === 0 ? (
          <div
            role="status"
            aria-live="polite"
            className="py-4 text-center text-sm text-muted-foreground"
          >
            No balances approaching expiry in the selected window.
          </div>
        ) : (
          <ul className="space-y-3" aria-label="Balances expiring soon">
            {data.map((item) => {
              const currency = getCurrency(item.currencyId.toLowerCase());
              return (
                <li
                  key={`${item.currencyId}-${item.scheduledExpiryDate}`}
                  className="flex items-start justify-between gap-2"
                >
                  <div className="flex items-center gap-2 min-w-0">
                    <span
                      className={cn(
                        "h-6 w-6 flex items-center justify-center rounded",
                        currency.iconBgClass,
                      )}
                    >
                      <currency.icon
                        className={cn("h-3 w-3", currency.iconClass)}
                        aria-hidden="true"
                      />
                    </span>
                    <div className="min-w-0">
                      <p className="text-xs font-medium text-foreground truncate">
                        {currency.label}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        Expires {formatDate(item.scheduledExpiryDate)}
                      </p>
                    </div>
                  </div>
                  <div className="text-right shrink-0">
                    <p className="text-xs font-medium text-foreground">
                      {item.affectedWalletCount} wallets
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {currency.rewardFormat(item.totalAmountAtRisk)} at risk
                    </p>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
