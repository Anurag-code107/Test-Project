import { useState } from "react";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { GlobalCatalogItemForm } from "@/components/redemption-catalog/GlobalCatalogItemForm";
import { SyncStatusBanner } from "@/components/redemption-catalog/SyncStatusBanner";
import {
  useGlobalCatalogItems,
  useActivateCatalogItem,
  useDeactivateCatalogItem,
} from "@/hooks/useRedemptionCatalog";
import type {
  GlobalCatalogItemFilters,
  RedemptionCatalogItemDetailResponse,
  CatalogCategory,
} from "@/types/redemption-catalog.types";

export default function GlobalCatalogAdminPage() {
  const [filters, setFilters] = useState<GlobalCatalogItemFilters>({
    page: 0,
    pageSize: 20,
  });
  const [formOpen, setFormOpen] = useState(false);
  const [editItem, setEditItem] = useState<RedemptionCatalogItemDetailResponse | undefined>();

  const { data, isLoading, isError, refetch } = useGlobalCatalogItems(filters);
  const activate = useActivateCatalogItem();
  const deactivate = useDeactivateCatalogItem();

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

  return (
    <div className="animate-route-in p-6 space-y-4" data-testid="catalog-admin-page">
      <SyncStatusBanner />

      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Global Catalog Items</h1>
        <Button onClick={openCreate}>
          <Plus className="w-4 h-4 mr-2" />
          New Item
        </Button>
      </div>

      {/* Filters */}
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
      </div>

      {/* Table */}
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
        <div className="flex flex-col items-center justify-center py-16 text-muted-foreground" data-testid="catalog-empty">
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
                <th className="px-4 py-3 text-left font-medium">Name</th>
                <th className="px-4 py-3 text-left font-medium">Category</th>
                <th className="px-4 py-3 text-left font-medium">Currency</th>
                <th className="px-4 py-3 text-left font-medium">Min Amount</th>
                <th className="px-4 py-3 text-left font-medium">Status</th>
                <th className="px-4 py-3 text-right font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {data.data.map((item) => (
                <tr
                  key={item.id}
                  className="border-b last:border-0 hover:bg-muted/20"
                  data-testid="catalog-item-row"
                >
                  <td className="px-4 py-3 font-medium">{item.name}</td>
                  <td className="px-4 py-3">
                    <Badge variant={item.category === "CASH" ? "default" : "secondary"}>
                      {item.category}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">{item.currencyId}</td>
                  <td className="px-4 py-3">{item.defaultMinRedemptionAmount}</td>
                  <td className="px-4 py-3">
                    <Badge variant={item.isActive ? "default" : "secondary"} data-testid={item.isActive ? "status-active" : "status-inactive"}>
                      {item.isActive ? "Active" : "Inactive"}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-right space-x-2">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => openEdit(item as RedemptionCatalogItemDetailResponse)}
                    >
                      Edit
                    </Button>
                    {item.isActive ? (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => deactivate.mutate(item.id)}
                        disabled={deactivate.isPending}
                      >
                        Deactivate
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        onClick={() => activate.mutate(item.id)}
                        disabled={activate.isPending}
                      >
                        Activate
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination */}
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

      {/* Create / Edit dialog */}
      <Dialog open={formOpen} onOpenChange={(open) => { if (!open) closeForm(); }}>
        <DialogContent className="w-full sm:max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editItem ? "Edit Catalog Item" : "New Catalog Item"}</DialogTitle>
          </DialogHeader>
          <GlobalCatalogItemForm item={editItem} onSave={closeForm} />
        </DialogContent>
      </Dialog>
    </div>
  );
}
