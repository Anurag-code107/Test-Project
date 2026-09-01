import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import type { RewardWalletResponse } from "@/types/wallet.types";

interface Props {
  wallet: RewardWalletResponse | null;
  isLoading: boolean;
  /** What the current selection would spend. */
  pendingTotal: number;
  remaining: number;
}

/**
 * The company wallet's standing, with a live "remaining after this" figure.
 *
 * <p>Remaining is shown because the admin is spending a shared budget — several admins draw on the same
 * wallet, so the number they need is what is left, not what was there when the page loaded.</p>
 */
export function CompanyBalanceHeader({ wallet, isLoading, pendingTotal, remaining }: Props) {
  if (isLoading) {
    return <Skeleton className="h-24 w-full" data-testid="balance-loading" />;
  }
  if (!wallet) return null;

  const overspend = remaining < 0;

  return (
    <Card data-testid="company-balance">
      <CardContent className="pt-6 grid gap-4 sm:grid-cols-3">
        <Figure label="Available" value={wallet.availableBalance} currency={wallet.currencyId} />
        <Figure label="Reserved" value={wallet.reservedBalance} currency={wallet.currencyId} muted />
        <div>
          <p className="text-xs uppercase tracking-wide text-muted-foreground">
            {pendingTotal > 0 ? "Remaining after this" : "Remaining"}
          </p>
          <p
            className={`text-xl font-semibold tabular-nums ${overspend ? "text-destructive" : ""}`}
            data-testid="remaining-balance"
          >
            {remaining.toFixed(2)} {wallet.currencyId}
          </p>
          {overspend && (
            <p className="text-xs text-destructive mt-1">Exceeds the available balance.</p>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

function Figure({
  label,
  value,
  currency,
  muted,
}: {
  label: string;
  value: string;
  currency: string;
  muted?: boolean;
}) {
  return (
    <div>
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className={`text-xl font-semibold tabular-nums ${muted ? "text-muted-foreground" : ""}`}>
        {value} {currency}
      </p>
    </div>
  );
}
