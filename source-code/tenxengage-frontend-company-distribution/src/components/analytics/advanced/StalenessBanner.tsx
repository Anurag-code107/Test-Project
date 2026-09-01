// No production analog — banner component is novel to this feature.
import { useState, useEffect } from "react";
import { X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatDate } from "@/utils/formatters";

export interface StalenessBannerProps {
  isStale: boolean;
  lastRefreshedAt: string | null;
}

/**
 * Yellow staleness banner shown when isStale=true or lastRefreshedAt=null (AC-6).
 * - Dismiss button sets local dismissed=true (session-only, not persisted — AC-6).
 * - When isStale transitions false → true on the next poll, dismissed resets to
 *   false (via useEffect) so the banner is NOT re-shown for a newly-stale state
 *   that the user already dismissed. Per story logic: if !isStale, reset dismissed
 *   so the banner re-appears if staleness recurs after a false period (AC-7).
 */
export function StalenessBanner({ isStale, lastRefreshedAt }: StalenessBannerProps) {
  const [dismissed, setDismissed] = useState(false);

  // AC-7: when isStale transitions to false, clear dismissed so the banner can
  // re-appear if staleness recurs in a later poll cycle.
  useEffect(() => {
    if (!isStale) {
      setDismissed(false);
    }
  }, [isStale]);

  const shouldShow = isStale && !dismissed;

  if (!shouldShow) return null;

  const bannerText =
    lastRefreshedAt === null
      ? "Analytics data has not been refreshed yet."
      : `Analytics data may be outdated. Last refreshed: ${formatDate(lastRefreshedAt)} at ${new Date(lastRefreshedAt).toISOString().slice(11, 16)} UTC.`;

  return (
    <div
      role="alert"
      aria-live="polite"
      className="flex items-start justify-between gap-3 rounded-lg border border-warning/40 bg-warning/10 px-4 py-3 text-sm text-warning dark:border-warning/40 dark:bg-warning/15"
    >
      <span>{bannerText}</span>
      <Button
        variant="ghost"
        size="sm"
        aria-label="Dismiss staleness warning"
        className="h-6 w-6 shrink-0 p-0 text-warning hover:bg-warning/20 hover:text-warning"
        onClick={() => setDismissed(true)}
      >
        <X className="h-4 w-4" aria-hidden />
      </Button>
    </div>
  );
}
