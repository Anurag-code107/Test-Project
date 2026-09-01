import { RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { useIntegrationHealth, useTriggerCatalogSync } from "@/hooks/useRedemptionCatalog";
import type { IntegrationSyncStatus } from "@/types/redemption-catalog.types";

const DATE_FORMATTER = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
});

function statusVariant(
  status: IntegrationSyncStatus,
): "default" | "secondary" | "destructive" | "outline" {
  if (status === "SUCCESS") return "default";
  if (status === "FAILED") return "destructive";
  if (status === "IN_PROGRESS") return "secondary";
  return "outline";
}

function formatDate(iso: string | null): string {
  if (!iso) return "Never";
  return DATE_FORMATTER.format(new Date(iso));
}

export function SyncStatusBanner() {
  const { data: health, isLoading } = useIntegrationHealth();
  const sync = useTriggerCatalogSync();

  function handleSync() {
    sync.mutate(undefined, {
      onSuccess: (data) => {
        toast.success(`Sync job queued (Job ID: ${data.jobId})`);
      },
      onError: (err: unknown) => {
        const status = (err as { response?: { status?: number } })?.response?.status;
        if (status === 429) {
          toast.error("Sync rate limit reached. Wait 1 minute before triggering again.");
        } else {
          toast.error("Failed to trigger sync.");
        }
      },
    });
  }

  return (
    <div
      className="rounded-lg border bg-muted/30 p-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4"
      data-testid="sync-status-banner"
    >
      {isLoading ? (
        <div className="flex gap-4 items-center">
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-5 w-20" />
        </div>
      ) : health ? (
        <div className="flex items-center gap-4 text-sm flex-wrap">
          <span className="text-muted-foreground">
            Last sync:{" "}
            <span className="text-foreground font-medium" data-testid="last-sync-at">
              {formatDate(health.lastSyncAt)}
            </span>
          </span>
          <Badge
            variant={statusVariant(health.syncStatus)}
            data-testid="sync-status-badge"
          >
            {health.syncStatus}
          </Badge>
          {health.failedSyncCount > 0 && (
            <span className="text-destructive text-xs" data-testid="failed-sync-count">
              {health.failedSyncCount} failed
            </span>
          )}
        </div>
      ) : (
        <span className="text-sm text-muted-foreground">Integration health unavailable</span>
      )}

      <Button
        size="sm"
        variant="outline"
        onClick={handleSync}
        disabled={sync.isPending}
        data-testid="trigger-sync-btn"
      >
        <RefreshCw className={cn("w-3.5 h-3.5 mr-1.5", sync.isPending && "animate-spin")} />
        {sync.isPending ? "Syncing…" : "Trigger Sync"}
      </Button>
    </div>
  );
}
