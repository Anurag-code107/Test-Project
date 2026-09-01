import { Fragment, useState } from "react";
import { AlertTriangle, ChevronDown, ChevronRight, Settings } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { Button } from "@/components/ui/button";
import { toast } from "sonner";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { useTenantCatalogConfig, useUpsertItemConfig } from "@/hooks/useRedemptionCatalog";
import { ItemConfigPanel } from "@/components/redemption-catalog/ItemConfigPanel";
import { RegionalConfigMatrix } from "@/components/redemption-catalog/RegionalConfigMatrix";
import type { TenantCatalogItemResponse } from "@/types/redemption-catalog.types";

export function TenantCatalogConfigTable() {
  const [expandedItemId, setExpandedItemId] = useState<string | null>(null);
  const [panelItem, setPanelItem] = useState<{ id: string; enabled: boolean } | null>(null);
  const { data, isLoading, isError, refetch } = useTenantCatalogConfig({ page: 0, pageSize: 20 });
  const upsert = useUpsertItemConfig();

  function handleToggle(item: TenantCatalogItemResponse, enabled: boolean) {
    upsert.mutate(
      { catalogItemId: item.id, request: { enabled } },
      {
        onError: (err: unknown) => {
          const status = (err as { response?: { status?: number } })?.response?.status;
          if (status === 409) {
            toast.error("Configuration was updated concurrently. Refresh and retry.");
          } else {
            toast.error("Could not save configuration");
          }
        },
      },
    );
  }

  function toggleExpand(itemId: string) {
    setExpandedItemId((prev) => (prev === itemId ? null : itemId));
  }

  if (isLoading) {
    return (
      <div className="space-y-2" data-testid="catalog-config-skeleton">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-12 w-full" />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="rounded-md border border-destructive/20 bg-destructive/5 p-4 text-sm text-destructive">
        Could not load catalog configuration.
        <Button
          variant="link"
          className="ml-2 text-destructive underline p-0 h-auto"
          onClick={() => void refetch()}
        >
          Retry
        </Button>
      </div>
    );
  }

  if (!data || data.data.length === 0) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="catalog-config-empty">
        No catalog items available.
      </p>
    );
  }

  return (
    <>
      <div className="overflow-x-auto rounded-md border">
        <table className="w-full text-sm">
          <thead className="border-b bg-muted/40">
            <tr>
              <th className="w-8 px-2 py-3" />
              <th className="px-4 py-3 text-left font-medium">Name</th>
              <th className="px-4 py-3 text-left font-medium">Category</th>
              <th className="px-4 py-3 text-left font-medium">Min Amount</th>
              <th className="px-4 py-3 text-left font-medium">Enabled</th>
              <th className="px-4 py-3 text-right font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {data.data.map((item) => (
              <Fragment key={item.id}>
                <tr
                  className="border-b hover:bg-muted/20"
                  data-testid="catalog-config-row"
                >
                  <td className="px-2 py-3">
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-6 w-6 p-0"
                      onClick={() => toggleExpand(item.id)}
                      data-testid={`expand-row-${item.id}`}
                      aria-label={expandedItemId === item.id ? "Collapse" : "Expand"}
                    >
                      {expandedItemId === item.id ? (
                        <ChevronDown className="w-4 h-4" />
                      ) : (
                        <ChevronRight className="w-4 h-4" />
                      )}
                    </Button>
                  </td>
                  <td className="px-4 py-3 font-medium">
                    <span>{item.name}</span>
                    {!item.isGloballyActive && (
                      <Badge
                        variant="outline"
                        className="ml-2 border-warning/60 text-warning"
                        data-testid="globally-inactive-badge"
                      >
                        <AlertTriangle className="w-3 h-3 mr-1" />
                        Globally inactive
                      </Badge>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <Badge variant={item.category === "CASH" ? "default" : "secondary"}>
                      {item.category}
                    </Badge>
                  </td>
                  <td className="px-4 py-3">{item.defaultMinRedemptionAmount}</td>
                  <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                    <Switch
                      checked={item.enabled}
                      onCheckedChange={(checked) => handleToggle(item, checked)}
                      disabled={upsert.isPending}
                      aria-label={`Enable ${item.name}`}
                      data-testid="enable-switch"
                    />
                  </td>
                  <td className="px-4 py-3 text-right">
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={(e) => {
                        e.stopPropagation();
                        setPanelItem({ id: item.id, enabled: item.enabled });
                      }}
                      aria-label={`Configure overrides for ${item.name}`}
                      data-testid={`config-overrides-${item.id}`}
                    >
                      <Settings className="w-4 h-4" />
                    </Button>
                  </td>
                </tr>

                {expandedItemId === item.id ? (
                  <tr key={`${item.id}-expanded`} className="border-b bg-muted/5">
                    <td colSpan={6} className="px-6 py-4">
                      <div className="space-y-2">
                        <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                          Regional availability
                        </p>
                        <RegionalConfigMatrix
                          catalogItemId={item.id}
                          geographicScope={item.geographicScope}
                        />
                      </div>
                    </td>
                  </tr>
                ) : null}
              </Fragment>
            ))}
          </tbody>
        </table>
      </div>

      <Sheet open={!!panelItem} onOpenChange={(open) => { if (!open) setPanelItem(null); }}>
        <SheetContent className="w-full sm:max-w-[540px] overflow-y-auto">
          <SheetHeader>
            <SheetTitle>Item Configuration</SheetTitle>
          </SheetHeader>
          {panelItem && (
            <ItemConfigPanel
              catalogItemId={panelItem.id}
              enabled={panelItem.enabled}
              onClose={() => setPanelItem(null)}
            />
          )}
        </SheetContent>
      </Sheet>
    </>
  );
}
