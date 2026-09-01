import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Loader2, Landmark, CreditCard } from "lucide-react";
import { useWithdrawals } from "@/hooks/redemption-payout/useRedemptionProfile";
import { formatFiat } from "@/lib/formatFiat";

interface RecentWithdrawalsProps {
  /** Whether the user is XTRM-enrolled — the history query is gated on it. */
  enrolled: boolean;
}

/**
 * The user's withdrawal history — 5 per page, newest first, with Prev/Next pagination over the full history.
 * Sits at the bottom of the Payout tab; refetched after a confirmed withdrawal.
 */
export function RecentWithdrawals({ enrolled }: RecentWithdrawalsProps) {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useWithdrawals(enrolled, page);

  const items = data?.data ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Wallet withdrawals</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {!enrolled ? (
          <p className="text-sm text-muted-foreground">
            Complete your payout profile to see your withdrawals.
          </p>
        ) : !data && isLoading ? (
          <div className="flex items-center text-sm text-muted-foreground">
            <Loader2 className="mr-2 h-4 w-4 animate-spin" /> Loading…
          </div>
        ) : data && data.totalElements === 0 ? (
          <p className="text-sm text-muted-foreground">No withdrawals yet.</p>
        ) : (
          <>
            <ul className="space-y-2">
              {items.map((w) => (
                <li key={w.id} className="flex items-center justify-between rounded-md border p-3">
                  <div className="flex items-center gap-2">
                    {w.destinationType === "BANK" ? (
                      <Landmark className="h-4 w-4 text-muted-foreground" />
                    ) : (
                      <CreditCard className="h-4 w-4 text-muted-foreground" />
                    )}
                    <span className="text-sm font-medium">{w.destinationLabel ?? w.destinationType}</span>
                    <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                      {w.status}
                    </span>
                  </div>
                  <span className="text-sm tabular-nums">{formatFiat(w.amountNet, w.currency)}</span>
                </li>
              ))}
            </ul>

            {totalPages > 1 && (
              <div className="flex items-center justify-between pt-1">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={!data?.hasPrevious}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </Button>
                <span className="text-xs tabular-nums text-muted-foreground">
                  Page {page + 1} of {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={!data?.hasNext}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
