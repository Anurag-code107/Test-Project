import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Loader2, Wallet, AlertTriangle, ArrowDownToLine } from "lucide-react";
import { useDigitalWallets } from "@/hooks/redemption-payout/useRedemptionProfile";
import { xtrmErrorCode, friendlyXtrmError } from "@/hooks/redemption-payout/useRedemptionProfileMutations";
import { formatFiat } from "@/lib/formatFiat";

interface DigitalWalletsPanelProps {
  /** Whether the user is XTRM-enrolled — the wallet list needs a PAT, so gate the query on it. */
  enrolled: boolean;
  /** Whether the wallet is the current payout destination (profile.payoutMethod === "ANYPAY"). */
  isDefault: boolean;
  /** Open the withdraw modal. */
  onWithdraw: () => void;
  /** Make the digital wallet the payout destination. */
  onSetDefault: () => void;
  settingDefault: boolean;
}

/**
 * The user's XTRM digital wallet(s). Shows the balance, a Withdraw action (opens the withdraw modal), and a
 * "Set as default" action that makes the wallet the payout destination. The "Default" badge shows only when
 * the wallet is the current destination. List-ready for multi-wallet.
 */
export function DigitalWalletsPanel({
  enrolled,
  isDefault,
  onWithdraw,
  onSetDefault,
  settingDefault,
}: DigitalWalletsPanelProps) {
  const { data: wallets, isLoading, isError, error } = useDigitalWallets(enrolled);
  // Payouts are USD-only, so show just the USD wallet — hides other-currency wallets XTRM auto-provisions
  // (e.g. an INR wallet) that have no delete API. Revisit when multi-currency wallets are supported.
  const visibleWallets = (wallets ?? []).filter((w) => w.currency === "USD");
  const hasWallets = visibleWallets.length > 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Digital Wallet</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {!enrolled ? (
          <p className="text-sm text-muted-foreground">
            Complete your payout profile to view your digital wallet.
          </p>
        ) : isLoading ? (
          <div className="flex items-center text-sm text-muted-foreground">
            <Loader2 className="mr-2 h-4 w-4 animate-spin" /> Loading wallet…
          </div>
        ) : isError ? (
          <Alert variant="destructive">
            <AlertTriangle className="h-4 w-4" />
            <AlertDescription>{friendlyXtrmError(xtrmErrorCode(error))}</AlertDescription>
          </Alert>
        ) : !hasWallets ? (
          <p className="text-sm text-muted-foreground">No wallet yet.</p>
        ) : (
          <>
            <ul className="space-y-2">
              {visibleWallets.map((w) => (
                <li key={w.id} className="flex items-center justify-between rounded-md border p-3">
                  <div className="flex items-center gap-2">
                    <Wallet className="h-4 w-4 text-muted-foreground" />
                    <span className="text-sm font-medium">{w.name}</span>
                    {w.currency === "USD" && isDefault && (
                      <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                        Default
                      </span>
                    )}
                  </div>
                  <span className="text-sm tabular-nums font-semibold">
                    {formatFiat(w.balance, w.currency)}
                  </span>
                </li>
              ))}
            </ul>

            <div className="flex items-center gap-2">
              <Button size="sm" onClick={onWithdraw}>
                <ArrowDownToLine className="mr-2 h-4 w-4" />
                Withdraw
              </Button>
              {isDefault ? (
                <span className="text-xs text-muted-foreground">Receives your payouts.</span>
              ) : (
                <Button variant="outline" size="sm" onClick={onSetDefault} disabled={settingDefault}>
                  {settingDefault && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Set as default
                </Button>
              )}
            </div>
            <p className="text-xs text-muted-foreground">
              Set as default to receive redemption payouts here. Withdraw moves funds out to a linked bank or card.
            </p>
          </>
        )}
      </CardContent>
    </Card>
  );
}
