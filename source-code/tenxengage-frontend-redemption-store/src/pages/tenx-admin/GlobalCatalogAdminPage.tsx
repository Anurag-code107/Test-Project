import { Fragment, useState } from "react";
import { AlertTriangle, ChevronDown, ChevronRight, Pencil, Plus, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { GlobalCatalogItemForm } from "@/components/redemption-catalog/GlobalCatalogItemForm";
import { RegionalConfigMatrix } from "@/components/redemption-catalog/RegionalConfigMatrix";
import { CATALOG_GEOGRAPHIC_SCOPE_ENABLED } from "@/config/redemptionFeatures";
import { TenantRedemptionSettingsForm } from "@/components/redemption-catalog/TenantRedemptionSettingsForm";
import {
  useActivateCatalogItem,
  useDeactivateCatalogItem,
  useDeleteCatalogItem,
  useGlobalCatalogItems,
  useTenantCatalogConfig,
} from "@/hooks/useRedemptionCatalog";
import type {
  CatalogCategory,
  GlobalCatalogItemFilters,
  RedemptionCatalogItemDetailResponse,
} from "@/types/redemption-catalog.types";

/**
 * Why an activation was refused. Only one live card may hold a gift-card SKU, and a 409 here is always
 * that — kept to the bare cause, since the admin already knows which row they toggled. Other failures
 * fall back to the server's own message, which is written for a human.
 */
function activationErrorMessage(err: unknown, itemName: string): string {
  const error = err as { response?: { status?: number; data?: { errorMessage?: string } } };
  if (error?.response?.status === 409) {
    return "SKU already in use";
  }
  return error?.response?.data?.errorMessage ?? `Could not activate “${itemName}”`;
}

export default function GlobalCatalogAdminPage() {
  const [filters, setFilters] = useState<GlobalCatalogItemFilters>({
    page: 0,
    pageSize: 10,
  });
  const [formOpen, setFormOpen] = useState(false);
  const [editItem, setEditItem] = useState<RedemptionCatalogItemDetailResponse | undefined>();
  const [expandedItemId, setExpandedItemId] = useState<string | null>(null);
  const [enabledFilter, setEnabledFilter] = useState<boolean | undefined>(undefined);
  const [deleteTarget, setDeleteTarget] = useState<{ id: string; name: string } | null>(null);

  const { data, isLoading, isError, refetch } = useGlobalCatalogItems(filters);
  const { data: tenantConfigData } = useTenantCatalogConfig({ page: 0, pageSize: 50 });
  const activate = useActivateCatalogItem();
  const deactivate = useDeactivateCatalogItem();
  const deleteItem = useDeleteCatalogItem();

  const tenantConfigMap = new Map(
    (tenantConfigData?.data ?? []).map((c) => [c.id, c]),
  );

  function openCreate() {
    setEditItem(undefined);
    setFormOpen(true);
  }

  function openEdit(item: RedemptionCatalogItemDetailResponse) {
    setEditItem(item);
    setFormOpen(true);
  }

  function closeForm() {
    setFormOpen(false);
    setEditItem(undefined);
  }

  function toggleExpand(itemId: string) {
    setExpandedItemId((prev) => (prev === itemId ? null : itemId));
  }

  function confirmDelete() {
    if (!deleteTarget) return;
    const { name } = deleteTarget;
    deleteItem.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast.success(`Deleted “${name}”`);
        setDeleteTarget(null);
      },
      onError: () => {
        toast.error(`Could not delete “${name}”`);
      },
    });
  }

  return (
    <div className="animate-route-in p-6 space-y-4" data-testid="catalog-admin-page">

      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Catalog Items</h1>
        <Button onClick={openCreate}>
          <Plus className="w-4 h-4 mr-2" />
          New Item
        </Button>
      </div>

      <div className="flex gap-3">
        <label htmlFor="catalog-search" className="sr-only">Search catalog items</label>
        <Input
          id="catalog-search"
          placeholder="Search items…"
          className="max-w-xs"
          value={filters.search ?? ""}
          onChange={(e) =>
            setFilters((f) => ({ ...f, search: e.target.value || undefined, page: 0 }))
          }
        />
        <Select
          value={filters.category ?? "all"}
          onValueChange={(v) =>
            setFilters((f) => ({
              ...f,
              category: v === "all" ? undefined : (v as CatalogCategory),
              page: 0,
            }))
          }
        >
          <SelectTrigger className="w-36">
            <SelectValue placeholder="Category" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All categories</SelectItem>
            <SelectItem value="CASH">Cash</SelectItem>
            <SelectItem value="NON_CASH">Non-Cash</SelectItem>
          </SelectContent>
        </Select>
        <Select
          value={filters.isActive === undefined ? "all" : String(filters.isActive)}
          onValueChange={(v) =>
            setFilters((f) => ({
              ...f,
              isActive: v === "all" ? undefined : v === "true",
              page: 0,
            }))
          }
        >
          <SelectTrigger className="w-32">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            <SelectItem value="true">Active</SelectItem>
            <SelectItem value="false">Inactive</SelectItem>
          </SelectContent>
        </Select>
        <Select
          value={enabledFilter === undefined ? "all" : String(enabledFilter)}
          onValueChange={(v) =>
            setEnabledFilter(v === "all" ? undefined : v === "true")
          }
        >
          <SelectTrigger className="w-36">
            <SelectValue placeholder="Enabled" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All</SelectItem>
            <SelectItem value="true">Enabled</SelectItem>
            <SelectItem value="false">Disabled</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {isLoading && (
        <div className="space-y-2" data-testid="catalog-skeleton">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      )}

      {isError && (
        <div className="rounded-md border border-destructive/20 bg-destructive/5 p-4 text-sm text-destructive">
          Could not load catalog items.
          <Button
            variant="link"
            className="ml-2 text-destructive underline p-0 h-auto"
            onClick={() => void refetch()}
          >
            Retry
          </Button>
        </div>
      )}

      {!isLoading && !isError && data?.data.length === 0 && (
        <div
          className="flex flex-col items-center justify-center py-16 text-muted-foreground"
          data-testid="catalog-empty"
        >
          <p className="mb-4">No catalog items yet — create your first item</p>
          <Button onClick={openCreate}>
            <Plus className="w-4 h-4 mr-2" />
            New Item
          </Button>
        </div>
      )}

      {!isLoading && !isError && data && data.data.length > 0 && (
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="border-b bg-muted/40">
              <tr>
                <th className="w-8 px-2 py-3" />
                <th className="px-4 py-3 text-left font-medium">Name</th>
                <th className="px-4 py-3 text-left font-medium">Category</th>
                <th className="px-4 py-3 text-left font-medium">Currency</th>
                <th className="px-4 py-3 text-left font-medium">Min Amount</th>
                {/* No separate Status column — the Active toggle below carries the same isActive state. */}
                <th className="px-4 py-3 text-left font-medium">Active</th>
                <th className="px-4 py-3 text-right font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {data.data
                .filter((item) =>
                  enabledFilter === undefined
                    ? true
                    : (tenantConfigMap.get(item.id)?.enabled ?? false) === enabledFilter,
                )
                .map((item) => {
                const tenantConfig = tenantConfigMap.get(item.id);
                return (
                  <Fragment key={item.id}>
                    <tr className="border-b hover:bg-muted/20" data-testid="catalog-item-row">
                      <td className="px-2 py-3">
                        {/* Expand reveals only the regional-availability matrix — hidden while the
                            dormant geographic-scope feature is off. */}
                        {CATALOG_GEOGRAPHIC_SCOPE_ENABLED && (
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-6 w-6 p-0"
                            onClick={() => toggleExpand(item.id)}
                            aria-label={expandedItemId === item.id ? "Collapse" : "Expand"}
                            data-testid={`expand-row-${item.id}`}
                          >
                            {expandedItemId === item.id ? (
                              <ChevronDown className="w-4 h-4" />
                            ) : (
                              <ChevronRight className="w-4 h-4" />
                            )}
                          </Button>
                        )}
                      </td>
                      <td className="px-4 py-3 font-medium">
                        <span>{item.name}</span>
                        {!item.isActive && (
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
                      <td className="px-4 py-3 text-muted-foreground">{item.currencyId.toUpperCase()}</td>
                      <td className="px-4 py-3">{item.defaultMinRedemptionAmount}</td>
                      <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                        {/* Single visibility control (Model 2): the Active toggle = the item's isActive.
                            On = visible to this client's sellers; off = hidden. No separate enable step. */}
                        <div className="flex items-center gap-2">
                          <Switch
                            checked={item.isActive}
                            onCheckedChange={(checked) =>
                              checked
                                ? activate.mutate(item.id, {
                                    // Activation enforces SKU uniqueness across LIVE items, so a 409
                                    // here means a rival item already holds this SKU. Silence would
                                    // just look like the toggle bouncing back.
                                    onError: (err) =>
                                      toast.error(activationErrorMessage(err, item.name)),
                                  })
                                : deactivate.mutate(item.id, {
                                    onError: () =>
                                      toast.error(`Could not deactivate “${item.name}”`),
                                  })
                            }
                            disabled={activate.isPending || deactivate.isPending}
                            aria-label={`${item.isActive ? "Deactivate" : "Activate"} ${item.name}`}
                          />
                          <span
                            className={cn(
                              "text-xs font-medium",
                              item.isActive ? "text-success" : "text-muted-foreground",
                            )}
                          >
                            {item.isActive ? "Active" : "Inactive"}
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-right space-x-1">
                        <Button
                          size="icon"
                          variant="outline"
                          className="h-8 w-8"
                          onClick={() => openEdit(item as RedemptionCatalogItemDetailResponse)}
                          data-testid={`edit-item-${item.id}`}
                          aria-label={`Edit ${item.name}`}
                          title="Edit"
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          size="icon"
                          variant="outline"
                          className="h-8 w-8 text-destructive hover:text-destructive"
                          onClick={() => setDeleteTarget({ id: item.id, name: item.name })}
                          data-testid={`delete-item-${item.id}`}
                          aria-label={`Delete ${item.name}`}
                          title="Delete"
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </td>
                    </tr>

                    {CATALOG_GEOGRAPHIC_SCOPE_ENABLED && expandedItemId === item.id && tenantConfig && (
                      <tr className="border-b bg-muted/5">
                        <td colSpan={7} className="px-6 py-4">
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
                    )}
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-muted-foreground">
          <span>
            Page {(filters.page ?? 0) + 1} of {data.totalPages} ({data.totalElements} items)
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={!data.hasPrevious}
              onClick={() => setFilters((f) => ({ ...f, page: (f.page ?? 0) - 1 }))}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={!data.hasNext}
              onClick={() => setFilters((f) => ({ ...f, page: (f.page ?? 0) + 1 }))}
            >
              Next
            </Button>
          </div>
        </div>
      )}

      <div className="space-y-4 pt-4 border-t">
        <h2 className="text-lg font-medium">Batch Settings</h2>
        <TenantRedemptionSettingsForm />
      </div>

      <Dialog open={formOpen} onOpenChange={(open) => { if (!open) closeForm(); }}>
        <DialogContent className="w-full sm:max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editItem ? "Edit Catalog Item" : "New Catalog Item"}</DialogTitle>
          </DialogHeader>
          <GlobalCatalogItemForm
            item={editItem}
            onSave={closeForm}
            tenantConfig={editItem ? (() => {
              const cfg = tenantConfigMap.get(editItem.id);
              return cfg?.configId ? {
                id: cfg.configId,
                redemptionCatalogItemId: editItem.id,
                enabled: cfg.enabled,
                processingModeOverride: cfg.processingModeOverride,
                minTransactionAmountOverride: cfg.minTransactionAmountOverride,
                maxTransactionAmountOverride: cfg.maxTransactionAmountOverride,
                minWalletBalanceOverride: cfg.minWalletBalanceOverride,
                returnWindowDaysOverride: cfg.returnWindowDaysOverride,
                createdAt: '',
                updatedAt: '',
              } : undefined;
            })() : undefined}
          />
        </DialogContent>
      </Dialog>

      <AlertDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => { if (!open) setDeleteTarget(null); }}
      >
        <AlertDialogContent data-testid="delete-confirm-dialog">
          <AlertDialogHeader>
            <AlertDialogTitle>Delete catalog item?</AlertDialogTitle>
            <AlertDialogDescription>
              “{deleteTarget?.name}” will be removed from the catalog and hidden from your
              sellers. Existing redemption history is preserved. This can only be undone by
              re-creating the item.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteItem.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={(e) => { e.preventDefault(); confirmDelete(); }}
              disabled={deleteItem.isPending}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              data-testid="delete-confirm-button"
            >
              {deleteItem.isPending ? "Deleting…" : "Delete"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
