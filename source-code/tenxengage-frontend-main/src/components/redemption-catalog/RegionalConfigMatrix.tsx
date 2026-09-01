import { useMemo, useState } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { Button } from "@/components/ui/button";
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
    <div className="space-y-1" data-testid="regional-config-matrix">
      {geographicScope.map((regionCode) => {
        const config = getConfigForRegion(regionCode);
        const hasOverride = config !== null;
        const isEnabled = config?.enabled ?? false;

        return (
          <div
            key={regionCode}
            className="flex items-center justify-between rounded-md border px-3 py-2"
            data-testid={`region-row-${regionCode}`}
          >
            <div className="flex flex-col gap-0.5">
              <span className="text-sm font-medium">{regionCode}</span>
              {!hasOverride && (
                <span className="text-xs text-muted-foreground" data-testid={`fallback-${regionCode}`}>
                  Using tenant default
                </span>
              )}
              {regionErrors[regionCode] && (
                <span className="text-xs text-destructive" data-testid={`region-error-${regionCode}`}>
                  {regionErrors[regionCode]}
                </span>
              )}
            </div>

            <div className="flex items-center gap-3">
              <Switch
                checked={isEnabled}
                onCheckedChange={(checked) => handleToggle(regionCode, checked)}
                disabled={upsert.isPending}
                data-testid={`region-toggle-${regionCode}`}
              />
              {hasOverride && (
                <Button
                  size="sm"
                  variant="ghost"
                  className="text-muted-foreground hover:text-destructive h-7 px-2 text-xs"
                  onClick={() => handleDelete(regionCode)}
                  disabled={deleteConfig.isPending}
                  data-testid={`region-delete-${regionCode}`}
                >
                  Remove override
                </Button>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
