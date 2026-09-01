import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Loader2, Link2, ExternalLink } from "lucide-react";
import type { XtrmAccountSummary } from "@/types/partner-company.types";

/**
 * A partner company's payout-provider connection.
 *
 * The only place an admin can see why a company cannot send rewards, and the only way to retry without a
 * support ticket. Shows the failure reason verbatim rather than a generic "not connected", because the two
 * common causes — missing admin details and a provider outage — need different responses from the admin.
 */

export interface XtrmAccountStatusProps {
  account?: XtrmAccountSummary;
  onConnect: () => void;
  isConnecting: boolean;
  /** False when the company has no admin details stored, so connecting cannot succeed yet. */
  canConnect?: boolean;
  /**
   * Where the admin completes identity verification at XTRM. Comes from the server, which knows which
   * XTRM this deployment talks to — a sandbox admin sent to the production portal would verify an account
   * that does not exist here. Omitted when unconfigured, and the link is then simply not offered.
   */
  portalUrl?: string;
}

const STATUS_LABEL: Record<string, string> = {
  PENDING: "Not connected",
  CONNECTED: "Connected",
  DISABLED: "Disabled",
};

export function XtrmAccountStatus({
  account,
  onConnect,
  isConnecting,
  canConnect = true,
  portalUrl,
}: XtrmAccountStatusProps) {
  const status = account?.status;
  const isConnected = status === "CONNECTED";
  // No row at all means never attempted — the same actionable state as PENDING, so it reads the same.
  const label = status ? STATUS_LABEL[status] ?? status : "Not connected";

  return (
    <div className="space-y-3 rounded-md border p-4">
      <div className="flex items-center justify-between gap-3">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium">Reward Payouts</span>
            <Badge variant={isConnected ? "default" : "secondary"}>
              {label}
            </Badge>
          </div>
          {account?.accountNumber ? (
            <p className="text-xs text-muted-foreground">
              Account {account.accountNumber}
              {account.identityLevel ? ` · ${account.identityLevel}` : ""}
            </p>
          ) : (
            <p className="text-xs text-muted-foreground">
              Connect this company to send gift cards and bank transfers to its
              sellers.
            </p>
          )}
        </div>

        {!isConnected && (
          <Button
            variant="outline"
            size="sm"
            className="gap-2 shrink-0"
            onClick={onConnect}
            disabled={isConnecting || !canConnect}
          >
            {isConnecting ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Link2 className="h-4 w-4" />
            )}
            {status === "PENDING" && account?.lastError ? "Retry" : "Connect"}
          </Button>
        )}
      </div>

      {account?.lastError && !isConnected && (
        <p className="text-xs text-destructive">{account.lastError}</p>
      )}

      {/* A new tab, never an iframe: every xtrm.com host sends X-Frame-Options: SAMEORIGIN (checked
          2026-09-01), so a browser refuses to frame it — an embed renders an empty box. rel="noopener"
          also keeps the opened page from reaching back into this one via window.opener. */}
      {isConnected && portalUrl && (
        <div className="space-y-1 border-t pt-3">
          <a
            href={portalUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 text-xs font-medium text-primary hover:underline"
          >
            Verify your identity at XTRM
            <ExternalLink className="h-3 w-3" />
          </a>
          <p className="text-xs text-muted-foreground">
            Sign in with the email above, then follow XTRM&apos;s verification
            steps. Higher payout limits may require it.
            {account?.identityLevel
              ? ` Your level was ${account.identityLevel} when this account was created; we can't see changes you make at XTRM.`
              : ""}
          </p>
        </div>
      )}

      {!canConnect && !isConnected && (
        <p className="text-xs text-muted-foreground">
          Add the company admin details above first — they create the payout
          account.
        </p>
      )}
    </div>
  );
}
