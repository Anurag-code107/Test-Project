import { useMemo, useState } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import { useRegionalConfig, useUpsertRegionConfig, useDeleteRegionConfig } from "@/hooks/useRedemptionCatalog";

interface Props {
  catalogItemId: string;
  geographicScope: string[];
}

export function RegionalConfigMatrix({ catalogItemId, geographicScope }: Props) {
  const { data: regionConfigs, isLoading } = useRegionalConfig(catalogItemId);
  const upsert = useUpsertRegionConfig();
  const deleteConfig = useDeleteRegionConfig();
  const [regionErrors, setRegionErrors] = useState<Record<string, string>>({});

  const configByRegion = useMemo(
    () => new Map(regionConfigs?.map((r) => [r.regionCode, r]) ?? []),
    [regionConfigs],
  );

  function getConfigForRegion(regionCode: string) {
    return configByRegion.get(regionCode) ?? null;
  }

  function handleToggle(regionCode: string, enabled: boolean) {
    setRegionErrors((prev) => ({ ...prev, [regionCode]: "" }));
    upsert.mutate(
      { catalogItemId, regionCode, request: { enabled } },
      {
        onError: (err: unknown) => {
          const error = err as { response?: { status?: number; data?: { errorMessage?: string } } };
          const status = error?.response?.status;
          if (status === 422) {
            const msg =
              error?.response?.data?.errorMessage ??
              "Region not supported by this catalog item";
            setRegionErrors((prev) => ({ ...prev, [regionCode]: msg }));
          } else {
            toast.error("Could not update regional configuration");
          }
        },
      },
    );
  }

  function handleDelete(regionCode: string) {
    deleteConfig.mutate(
      { catalogItemId, regionCode },
      {
        onError: () => {
          toast.error("Could not remove regional override");
        },
      },
    );
  }

  if (isLoading) {
    return (
      <div className="space-y-2" data-testid="regional-matrix-skeleton">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-10 w-full" />
        ))}
      </div>
    );
  }

  if (geographicScope.length === 0) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="regional-global-scope">
        This item is available globally — no regional overrides needed.
      </p>
    );
  }

  return (
    <div className="flex flex-wrap gap-2" data-testid="regional-config-matrix">
      {geographicScope.map((regionCode) => {
        const config = getConfigForRegion(regionCode);
        const isEnabled = config?.enabled ?? false;

        return (
          <div
            key={regionCode}
            className="flex items-center gap-1.5 rounded-full border px-3 py-1 bg-background"
            data-testid={`region-row-${regionCode}`}
          >
            <span
              className={cn(
                "h-2 w-2 rounded-full flex-shrink-0",
                isEnabled ? "bg-success" : "bg-muted-foreground/30",
              )}
            />
            <span className="text-sm font-medium">{regionCode}</span>
            <Switch
              checked={isEnabled}
              onCheckedChange={(checked) => {
                if (checked) {
                  handleToggle(regionCode, true);
                } else {
                  handleDelete(regionCode);
                }
              }}
              disabled={upsert.isPending || deleteConfig.isPending}
              aria-label={`Toggle ${regionCode}`}
              data-testid={`region-toggle-${regionCode}`}
            />
            {regionErrors[regionCode] && (
              <span className="text-xs text-destructive" data-testid={`region-error-${regionCode}`}>
                {regionErrors[regionCode]}
              </span>
            )}
          </div>
        );
      })}
    </div>
  );
}
