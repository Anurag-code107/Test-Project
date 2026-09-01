// Adapted from: src/pages/redemption/analytics/RedemptionAnalyticsPage.tsx (page shell pattern)
// Screen type: Settings / config page (Screen Pattern Mirror)
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { AlertCircle, Timer } from "lucide-react";
import { useBalanceExpirationPolicies } from "@/hooks/useBalanceExpirationPolicies";
import { BalanceExpirationPolicyForm } from "@/components/balanceExpiration/BalanceExpirationPolicyForm";
import { ExpiringSoonPreviewCard } from "@/components/balanceExpiration/ExpiringSoonPreviewCard";
import type { BalanceExpirationPolicyResponse } from "@/types/balanceExpiration.types";

/**
 * BalanceExpirationSettingsPage
 *
 * Route: /settings/redemption/balance-expiration
 * Permission: action.redemption.expiration.configure
 *
 * Covers AC-1, AC-2, AC-3, AC-5, AC-6:
 *  - Fetches saved policies; presents all 4 currencies (AC-6).
 *  - Currencies with no saved policy rendered as disabled defaults — never hidden.
 *  - Full loading / error / default states as specified in UI States.
 */

/** The four platform currencies, in display order (AC-6) */
const PLATFORM_CURRENCIES = ["cash", "points", "credits", "tickets"] as const;

/** Skeleton card for loading state */
function PolicyFormSkeleton() {
  return (
    <div
      role="status"
      aria-busy="true"
      aria-label="Loading expiration policy"
      className="rounded-xl border border-border p-4 space-y-4"
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Skeleton className="h-8 w-8 rounded-lg" />
          <div className="space-y-1">
            <Skeleton className="h-4 w-20" />
            <Skeleton className="h-3 w-28" />
          </div>
        </div>
        <Skeleton className="h-5 w-9 rounded-full" />
      </div>
      <Skeleton className="h-4 w-32" />
      <Skeleton className="h-9 w-full" />
      <Skeleton className="h-9 w-full" />
    </div>
  );
}

function BalanceExpirationSettingsPage() {
  const { data: policies, isLoading, isError, refetch } = useBalanceExpirationPolicies();

  // Build lookup: currencyId → policy (undefined if not configured)
  const policyMap = (policies ?? []).reduce<
    Record<string, BalanceExpirationPolicyResponse>
  >((acc, p) => {
    acc[p.currencyId.toLowerCase()] = p;
    return acc;
  }, {});

  return (
    <div className="animate-route-in space-y-6 p-6">
      {/* Page header */}
      <div>
        <div className="flex items-center gap-2 mb-1">
          <Timer className="h-5 w-5 text-primary" aria-hidden="true" />
          <h1 className="text-xl font-semibold text-foreground">
            Balance Expiration
          </h1>
        </div>
        <p className="text-sm text-muted-foreground">
          Configure per-currency reward balance expiration policies. Expiration is
          off by default and must be explicitly enabled per currency type.
        </p>
      </div>

      {isLoading ? (
        <div
          role="status"
          aria-busy="true"
          aria-label="Loading expiration policies"
          className="grid grid-cols-1 lg:grid-cols-2 gap-4"
        >
          {Array.from({ length: 4 }, (_, i) => (
            <PolicyFormSkeleton key={i} />
          ))}
        </div>
      ) : isError ? (
        <div
          role="alert"
          aria-live="assertive"
          className="flex flex-col items-center gap-3 py-12 text-center"
        >
          <AlertCircle className="h-8 w-8 text-destructive" aria-hidden="true" />
          <p className="text-sm text-destructive">Could not load expiration policies</p>
          <Button variant="outline" onClick={() => refetch()}>
            Try again
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
          {/* Policy forms — 2-col on XL */}
          <div className="xl:col-span-2 grid grid-cols-1 lg:grid-cols-2 gap-4">
            {PLATFORM_CURRENCIES.map((currencyId) => (
              <BalanceExpirationPolicyForm
                key={currencyId}
                currencyId={currencyId}
                policy={policyMap[currencyId]}
              />
            ))}
          </div>

          {/* Expiring-soon preview card */}
          <div>
            <ExpiringSoonPreviewCard />
          </div>
        </div>
      )}
    </div>
  );
}

export default BalanceExpirationSettingsPage;
