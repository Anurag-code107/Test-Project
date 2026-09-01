# Merge Catalog Items + Tenant Config Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge the "Catalog Items" and "Tenant Config" sub-tabs inside Platform Settings → Redemption Catalog into a single unified table view.

**Architecture:** `GlobalCatalogAdminPage.tsx` becomes the merged view — it already owns the global catalog table, search/filter, and Create/Edit dialog. We add a secondary `useTenantCatalogConfig` fetch (page 0, pageSize 100), build a `Map<id, TenantCatalogItemResponse>`, and use it to render an Enabled switch (with colored text label) and expand row (regional config) inline. The Edit dialog is a single scrollable form — `GlobalCatalogItemForm` only, no tabs, no Tenant Settings section. There is no separate gear/Settings button. A "Globally inactive" badge (`⚠ Globally inactive`) appears next to the item name when `isActive` is false. `TenantRedemptionSettingsForm` (Batch Settings) is appended below the table. `RedemptionCatalogTab.tsx` drops its `<Tabs>` wrapper and renders `GlobalCatalogAdminPage` directly.

**Tech Stack:** React 18, TypeScript, React Query, shadcn/ui (Switch, Fragment, Badge), Vitest + React Testing Library

---

## File Map

| File | Change |
|---|---|
| `src/pages/tenx-admin/GlobalCatalogAdminPage.tsx` | **Rewrite** — add tenant config data, Enabled column (switch + label), globally inactive badge, expand row, Batch Settings section |
| `src/components/settings/RedemptionCatalogTab.tsx` | **Simplify** — remove Tabs, render GlobalCatalogAdminPage directly |
| `src/components/redemption-catalog/__tests__/GlobalCatalogAdminPage.test.tsx` | **Update** — add mocks for new hooks, add tests for new columns |
| `src/pages/client-admin/CatalogConfigPage.tsx` | **No change** — keep as-is, no longer rendered here but do NOT delete |
| `src/components/redemption-catalog/TenantCatalogConfigTable.tsx` | **No change** — keep as-is |
| `src/components/redemption-catalog/TenantRedemptionSettingsForm.tsx` | **No change** — imported and reused |
| `src/components/redemption-catalog/RegionalConfigMatrix.tsx` | **No change** — imported and reused |

---

## Task 1: Update test file to cover merged columns

**Files:**
- Modify: `src/components/redemption-catalog/__tests__/GlobalCatalogAdminPage.test.tsx`

The existing mock only stubs `useGlobalCatalogItems`, `useActivateCatalogItem`, `useDeactivateCatalogItem`, `useIntegrationHealth`, `useTriggerCatalogSync`. The merged page adds `useTenantCatalogConfig` and `useUpsertItemConfig` — the mock must include them or the component will throw.

- [ ] **Step 1: Replace the entire test file with the updated version below**

```tsx
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import GlobalCatalogAdminPage from "@/pages/tenx-admin/GlobalCatalogAdminPage";

const mockUseGlobalCatalogItems = vi.fn();
const mockUseUpsertItemConfig = vi.fn();
const mockUseTenantCatalogConfig = vi.fn();

vi.mock("@/hooks/useRedemptionCatalog", () => ({
  useGlobalCatalogItems: (...args: unknown[]) => mockUseGlobalCatalogItems(...args),
  useActivateCatalogItem: () => ({ mutate: vi.fn(), isPending: false }),
  useDeactivateCatalogItem: () => ({ mutate: vi.fn(), isPending: false }),
  useIntegrationHealth: () => ({ isLoading: false, data: null }),
  useTriggerCatalogSync: () => ({ mutate: vi.fn(), isPending: false }),
  useTenantCatalogConfig: (...args: unknown[]) => mockUseTenantCatalogConfig(...args),
  useUpsertItemConfig: () => mockUseUpsertItemConfig(),
}));

vi.mock("@/components/redemption-catalog/GlobalCatalogItemForm", () => ({
  GlobalCatalogItemForm: () => <div data-testid="catalog-item-form" />,
}));
vi.mock("@/components/redemption-catalog/SyncStatusBanner", () => ({
  SyncStatusBanner: () => <div data-testid="sync-banner" />,
}));
vi.mock("@/components/redemption-catalog/TenantRedemptionSettingsForm", () => ({
  TenantRedemptionSettingsForm: () => <div data-testid="tenant-settings-form" />,
}));
vi.mock("@/components/redemption-catalog/RegionalConfigMatrix", () => ({
  RegionalConfigMatrix: () => <div data-testid="regional-config-matrix" />,
}));

const ITEM = {
  id: "item-1",
  name: "Amazon Gift Card",
  category: "NON_CASH",
  currencyId: "points",
  defaultMinRedemptionAmount: "50.00",
  defaultProcessingMode: "INSTANT",
  geographicScope: ["US"],
  isReturnable: false,
  defaultReturnWindowDays: 0,
  isActive: true,
  createdAt: "2026-05-01T00:00:00Z",
  updatedAt: "2026-05-01T00:00:00Z",
};

const PAGINATED_ONE = {
  data: [ITEM],
  totalElements: 1,
  totalPages: 1,
  page: 0,
  pageSize: 20,
  hasNext: false,
  hasPrevious: false,
};

const TENANT_CONFIG_ONE = {
  data: [
    {
      id: "item-1",
      name: "Amazon Gift Card",
      category: "NON_CASH",
      currencyId: "points",
      defaultMinRedemptionAmount: "50.00",
      defaultProcessingMode: "INSTANT",
      geographicScope: ["US"],
      isReturnable: false,
      defaultReturnWindowDays: 0,
      isGloballyActive: true,
      configId: "cfg-1",
      enabled: true,
      createdAt: "2026-05-01T00:00:00Z",
      updatedAt: "2026-05-01T00:00:00Z",
    },
  ],
  totalElements: 1,
  totalPages: 1,
  page: 0,
  pageSize: 100,
  hasNext: false,
  hasPrevious: false,
};

function wrapper({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("GlobalCatalogAdminPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseTenantCatalogConfig.mockReturnValue({ data: undefined });
    mockUseUpsertItemConfig.mockReturnValue({ mutate: vi.fn(), isPending: false });
  });

  it("renders skeleton while loading", () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: true, isError: false, data: undefined });

    render(<GlobalCatalogAdminPage />, { wrapper });

    expect(screen.getByTestId("catalog-skeleton")).toBeDefined();
  });

  it("renders empty state when no items", () => {
    mockUseGlobalCatalogItems.mockReturnValue({
      isLoading: false,
      isError: false,
      data: { data: [], totalElements: 0, totalPages: 0, page: 0, pageSize: 20, hasNext: false, hasPrevious: false },
    });

    render(<GlobalCatalogAdminPage />, { wrapper });

    expect(screen.getByTestId("catalog-empty")).toBeDefined();
    expect(screen.getByText(/no catalog items yet/i)).toBeDefined();
  });

  it("renders item row with Status badge and Enabled switch", () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });
    mockUseTenantCatalogConfig.mockReturnValue({ data: TENANT_CONFIG_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    expect(screen.getByTestId("catalog-item-row")).toBeDefined();
    expect(screen.getByText("Amazon Gift Card")).toBeDefined();
    expect(screen.getByTestId("status-active")).toBeDefined();
    expect(screen.getByRole("switch", { name: /enable amazon gift card/i })).toBeDefined();
  });

  it("Enabled switch reflects tenant config enabled state and shows text label", () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });
    mockUseTenantCatalogConfig.mockReturnValue({ data: TENANT_CONFIG_ONE }); // enabled: true

    render(<GlobalCatalogAdminPage />, { wrapper });

    expect(screen.getByText("Enabled")).toBeDefined();
  });

  it("Enabled switch defaults to false and shows Disabled label when no tenant config", () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });
    mockUseTenantCatalogConfig.mockReturnValue({
      data: { ...TENANT_CONFIG_ONE, data: [] },
    });

    render(<GlobalCatalogAdminPage />, { wrapper });

    const sw = screen.getByRole("switch", { name: /enable amazon gift card/i });
    expect(sw.getAttribute("data-state")).toBe("unchecked");
    expect(screen.getByText("Disabled")).toBeDefined();
  });

  it("toggling Enabled switch calls upsertItemConfig", async () => {
    const mutate = vi.fn();
    mockUseUpsertItemConfig.mockReturnValue({ mutate, isPending: false });
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });
    mockUseTenantCatalogConfig.mockReturnValue({
      data: { ...TENANT_CONFIG_ONE, data: [{ ...TENANT_CONFIG_ONE.data[0], enabled: false }] },
    });

    render(<GlobalCatalogAdminPage />, { wrapper });

    await userEvent.click(screen.getByRole("switch", { name: /enable amazon gift card/i }));

    expect(mutate).toHaveBeenCalledWith(
      { catalogItemId: "item-1", request: { enabled: true } },
      expect.any(Object),
    );
  });

  it("shows Globally inactive badge when item is not active", () => {
    mockUseGlobalCatalogItems.mockReturnValue({
      isLoading: false,
      isError: false,
      data: { ...PAGINATED_ONE, data: [{ ...ITEM, isActive: false }] },
    });
    mockUseTenantCatalogConfig.mockReturnValue({ data: TENANT_CONFIG_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    expect(screen.getByTestId("globally-inactive-badge")).toBeDefined();
  });

  it("Edit button opens dialog with single-form (no tabs)", async () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });
    mockUseTenantCatalogConfig.mockReturnValue({ data: TENANT_CONFIG_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    await userEvent.click(screen.getByTestId("edit-item-item-1"));

    expect(screen.getByTestId("catalog-item-form")).toBeDefined();
    expect(screen.queryByRole("tab", { name: /tenant settings/i })).toBeNull();
  });

  it("renders Batch Settings section", () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    expect(screen.getByText("Batch Settings")).toBeDefined();
    expect(screen.getByTestId("tenant-settings-form")).toBeDefined();
  });

  it("clicking expand button shows regional config when tenant config exists", async () => {
    mockUseGlobalCatalogItems.mockReturnValue({ isLoading: false, isError: false, data: PAGINATED_ONE });
    mockUseTenantCatalogConfig.mockReturnValue({ data: TENANT_CONFIG_ONE });

    render(<GlobalCatalogAdminPage />, { wrapper });

    await userEvent.click(screen.getByTestId("expand-row-item-1"));

    expect(screen.getByTestId("regional-config-matrix")).toBeDefined();
  });
});
```

- [ ] **Step 2: Run the tests to confirm the new tests fail (hooks not yet in page)**

```bash
cd tenxengage-frontend
npm run test -- GlobalCatalogAdminPage
```

Expected: Several new tests fail — `useTenantCatalogConfig is not a function` or similar.

- [ ] **Step 3: Commit the updated test file**

```bash
git add src/components/redemption-catalog/__tests__/GlobalCatalogAdminPage.test.tsx
git commit -m "test: update GlobalCatalogAdminPage tests for merged tenant config columns"
```

---

## Task 2: Rewrite `GlobalCatalogAdminPage.tsx`

**Files:**
- Modify: `src/pages/tenx-admin/GlobalCatalogAdminPage.tsx`

- [ ] **Step 1: Replace the file with the merged implementation below**

```tsx
import { Fragment, useState } from "react";
import { AlertTriangle, ChevronDown, ChevronRight, Plus } from "lucide-react";
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
import { GlobalCatalogItemForm } from "@/components/redemption-catalog/GlobalCatalogItemForm";
import { RegionalConfigMatrix } from "@/components/redemption-catalog/RegionalConfigMatrix";
import { SyncStatusBanner } from "@/components/redemption-catalog/SyncStatusBanner";
import { TenantRedemptionSettingsForm } from "@/components/redemption-catalog/TenantRedemptionSettingsForm";
import {
  useActivateCatalogItem,
  useDeactivateCatalogItem,
  useGlobalCatalogItems,
  useTenantCatalogConfig,
  useUpsertItemConfig,
} from "@/hooks/useRedemptionCatalog";
import type {
  CatalogCategory,
  GlobalCatalogItemFilters,
  RedemptionCatalogItemDetailResponse,
} from "@/types/redemption-catalog.types";

export default function GlobalCatalogAdminPage() {
  const [filters, setFilters] = useState<GlobalCatalogItemFilters>({
    page: 0,
    pageSize: 20,
  });
  const [formOpen, setFormOpen] = useState(false);
  const [editItem, setEditItem] = useState<RedemptionCatalogItemDetailResponse | undefined>();
  const [expandedItemId, setExpandedItemId] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useGlobalCatalogItems(filters);
  // pageSize 100: tenant catalog stays well under 100 items; avoids pagination join complexity
  const { data: tenantConfigData } = useTenantCatalogConfig({ page: 0, pageSize: 100 });
  const activate = useActivateCatalogItem();
  const deactivate = useDeactivateCatalogItem();
  const upsert = useUpsertItemConfig();

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

  function handleToggle(itemId: string, enabled: boolean) {
    upsert.mutate(
      { catalogItemId: itemId, request: { enabled } },
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

  return (
    <div className="animate-route-in p-6 space-y-4" data-testid="catalog-admin-page">
      <SyncStatusBanner />

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
                <th className="px-4 py-3 text-left font-medium">Status</th>
                <th className="px-4 py-3 text-left font-medium">Enabled</th>
                <th className="px-4 py-3 text-right font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {data.data.map((item) => {
                const tenantConfig = tenantConfigMap.get(item.id);
                return (
                  <Fragment key={item.id}>
                    <tr className="border-b hover:bg-muted/20" data-testid="catalog-item-row">
                      <td className="px-2 py-3">
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
                      <td className="px-4 py-3 text-muted-foreground">{item.currencyId}</td>
                      <td className="px-4 py-3">{item.defaultMinRedemptionAmount}</td>
                      <td className="px-4 py-3">
                        <Badge
                          variant={item.isActive ? "default" : "secondary"}
                          data-testid={item.isActive ? "status-active" : "status-inactive"}
                        >
                          {item.isActive ? "Active" : "Inactive"}
                        </Badge>
                      </td>
                      <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                        <div className="flex items-center gap-2">
                          <Switch
                            checked={tenantConfig?.enabled ?? false}
                            onCheckedChange={(checked) => handleToggle(item.id, checked)}
                            disabled={upsert.isPending}
                            aria-label={`Enable ${item.name}`}
                          />
                          <span
                            className={cn(
                              "text-xs font-medium",
                              tenantConfig?.enabled ? "text-success" : "text-muted-foreground",
                            )}
                          >
                            {tenantConfig?.enabled ? "Enabled" : "Disabled"}
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-right space-x-1">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => openEdit(item as RedemptionCatalogItemDetailResponse)}
                          data-testid={`edit-item-${item.id}`}
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

                    {expandedItemId === item.id && tenantConfig && (
                      <tr className="border-b bg-muted/5">
                        <td colSpan={8} className="px-6 py-4">
                          <div className="space-y-2">
                            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                              Regional availability
                            </p>
                            <RegionalConfigMatrix
                              catalogItemId={item.id}
                              geographicScope={tenantConfig.geographicScope}
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
          <GlobalCatalogItemForm item={editItem} onSave={closeForm} />
        </DialogContent>
      </Dialog>
    </div>
  );
}
```

- [ ] **Step 2: Run the tests**

```bash
npm run test -- GlobalCatalogAdminPage
```

Expected: All tests pass including the new ones added in Task 1.

- [ ] **Step 3: Commit**

```bash
git add src/pages/tenx-admin/GlobalCatalogAdminPage.tsx
git commit -m "feat: merge tenant config into GlobalCatalogAdminPage — Enabled switch, globally inactive badge, regional expand, Batch Settings"
```

---

## Task 3: Simplify `RedemptionCatalogTab.tsx`

**Files:**
- Modify: `src/components/settings/RedemptionCatalogTab.tsx`

- [ ] **Step 1: Replace the file content — remove Tabs, render page directly**

```tsx
import { Package } from "lucide-react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import GlobalCatalogAdminPage from "@/pages/tenx-admin/GlobalCatalogAdminPage";

export function RedemptionCatalogTab() {
  return (
    <Card className="border-dashed">
      <CardHeader className="pb-3">
        <div className="flex items-center gap-2">
          <Package className="h-5 w-5 text-muted-foreground" />
          <CardTitle className="text-foreground">Redemption Catalog</CardTitle>
        </div>
        <CardDescription>
          Manage global catalog items and configure tenant-level availability.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <GlobalCatalogAdminPage />
      </CardContent>
    </Card>
  );
}
```

- [ ] **Step 2: Run full test suite**

```bash
npm run test
```

Expected: All tests pass — no regressions.

- [ ] **Step 3: Commit**

```bash
git add src/components/settings/RedemptionCatalogTab.tsx
git commit -m "feat: remove Catalog Items / Tenant Config sub-tabs — single merged view"
```

---

## Task 4: Manual verification

- [ ] **Step 1: Start the dev server**

```bash
npm run dev
```

- [ ] **Step 2: Navigate to Platform Settings → Redemption Catalog**

URL: `http://localhost:3000/settings/platform?tab=redemption-catalog`

Confirm:
- No "Catalog Items" / "Tenant Config" sub-tabs visible
- Sync banner at top
- Table has columns: (expand) | Name | Category | Currency | Min Amount | Status | Enabled | Actions
- Each row has: Edit button, Activate/Deactivate button, Enabled switch with text label

- [ ] **Step 3: Test Enabled switch**

- Find an item with `enabled: false` — switch shows OFF with grey "Disabled" label
- Toggle it ON → switch shows ON with green "Enabled" label
- Refresh page → switch reflects persisted state

- [ ] **Step 4: Test Edit dialog (single form, no tabs)**

- Click Edit on any row → dialog opens titled "Edit Catalog Item"
- Confirm there are NO tabs ("Item Details" / "Tenant Settings" tabs must NOT be present)
- All item fields are pre-filled (name, description, category, currency, min amount, processing mode, geo scope, returnable, image)
- Save → dialog closes, no error

- [ ] **Step 5: Test globally inactive badge**

- Deactivate an item → status badge changes to "Inactive" and a "⚠ Globally inactive" badge appears next to the item name
- Reactivate → badge disappears

- [ ] **Step 6: Test expand row**

- Click the chevron on any row that has `geographicScope` in tenant config → expands showing "Regional availability" and the regional config matrix
- Click chevron again → collapses

- [ ] **Step 7: Test Batch Settings section**

- Scroll below the table → "Batch Settings" heading appears
- Batch Cadence radio (Daily / Weekly) and Max In-Flight Redemptions input are present
- Change a value and save → "Settings saved" toast appears

- [ ] **Step 8: Test Activate/Deactivate still works**

- Click Deactivate on an active item → status badge changes to Inactive, "⚠ Globally inactive" badge appears, button changes to Activate
- Click Activate → badge disappears, button returns to Deactivate

---

## Done When

- [ ] `npm run test` passes with all new tests green
- [ ] No sub-tabs in Platform Settings → Redemption Catalog
- [ ] Single table shows Status (global) + Enabled (tenant toggle with text label) side-by-side
- [ ] Edit button opens a single-form dialog — `GlobalCatalogItemForm` only, no tabs
- [ ] "⚠ Globally inactive" badge appears next to item name when `isActive` is false
- [ ] Expand chevron shows Regional Config Matrix
- [ ] Batch Settings section below table works (Batch Cadence + Max In-Flight)
- [ ] Edit / Activate / Deactivate all still function
