// Adapted from: src/pages/client-admin/ManageIncentivesPage.tsx (production analog from Mirror)
import { useState, useCallback, useEffect, useMemo } from "react";
import { toast } from "sonner";
import { Download } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { PageBanner } from "@/components/PageBanner";
import { TransactionHistoryTable } from "@/components/redemption-history/TransactionHistoryTable";
import { HistoryFilterBar } from "@/components/redemption-history/HistoryFilterBar";
import { TransactionDetailSheet } from "@/components/redemption-history/TransactionDetailSheet";
import { ExportDialog } from "@/components/redemption-history/ExportDialog";
import { PermissionGate } from "@/components/PermissionGate";
import { MyReturnsTab } from "@/components/redemption-returns/MyReturnsTab";
import { RequestReturnDialog } from "@/components/redemption-returns/RequestReturnDialog";
import { usePersonalRedemptions } from "@/hooks/redemption-history/usePersonalRedemptions";
import { useCompanyRedemptions } from "@/hooks/redemption-history/useCompanyRedemptions";
import { useAuth } from "@/hooks/useAuth";
import { usePermissions } from "@/hooks/usePermissions";
import { useQueryClient } from "@tanstack/react-query";
import { COMPANY_REDEMPTION_ENABLED } from "@/config/redemptionFeatures";
import type { ExportJobScope, RedemptionHistoryFilters, RedemptionRequestResponse } from "@/types/redemption-history/redemption-history.types";
import type { PaginatedResponse } from "@/types/api.types";

const EMPTY_ROWS: RedemptionRequestResponse[] = [];

type UseHistoryQuery = (
  filters: RedemptionHistoryFilters,
  page: number,
) => { data: PaginatedResponse<RedemptionRequestResponse> | undefined; isLoading: boolean; isError: boolean };

interface HistoryPaneProps {
  useHistoryQuery: UseHistoryQuery;
  errorToast: string;
  emptyNoFiltersText?: string;
  emptyWithFiltersText?: string;
  showExport?: boolean;
  exportScope?: ExportJobScope;
  showReturnActions?: boolean;
}

function HistoryPane({ useHistoryQuery, errorToast, emptyNoFiltersText, emptyWithFiltersText, showExport, exportScope, showReturnActions }: HistoryPaneProps) {
  const queryClient = useQueryClient();
  const [filters, setFilters] = useState<RedemptionHistoryFilters>({});
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [exportDialogOpen, setExportDialogOpen] = useState(false);
  const [returnDialogTx, setReturnDialogTx] = useState<RedemptionRequestResponse | null>(null);

  const { data, isLoading, isError } = useHistoryQuery(filters, page);

  useEffect(() => {
    if (isError) toast.error(errorToast);
  }, [isError, errorToast]);

  const hasActiveFilters =
    !!filters.dateFrom || !!filters.dateTo || !!filters.status || !!filters.category;

  const handleFiltersChange = useCallback((updated: RedemptionHistoryFilters) => {
    setFilters(updated);
    setPage(0);
  }, []);

  const handleRowClick = useCallback((id: string) => setSelectedId(id), []);
  const handlePageChange = useCallback((p: number) => setPage(p), []);

  const handleReturnSuccess = useCallback(() => {
    setReturnDialogTx(null);
    queryClient.invalidateQueries({ queryKey: ["redemption-history"] });
  }, [queryClient]);

  const rows = data?.data ?? EMPTY_ROWS;
  const pagination = useMemo(
    () => data
      ? { page: data.page, pageSize: data.pageSize, totalElements: data.totalElements,
          totalPages: data.totalPages, hasNext: data.hasNext, hasPrevious: data.hasPrevious }
      : undefined,
    [data],
  );

  return (
    <>
      <div className="mt-5 flex flex-wrap items-center gap-3">
        <div className="flex-1">
          <HistoryFilterBar filters={filters} onFiltersChange={handleFiltersChange} />
        </div>
        {pagination && (
          <span className="text-sm text-muted-foreground tabular-nums shrink-0">
            {pagination.totalElements} {pagination.totalElements === 1 ? "transaction" : "transactions"}
          </span>
        )}
        {showExport && (
          <PermissionGate permission="action.redemption.export">
            <Button variant="outline" size="sm" onClick={() => setExportDialogOpen(true)}>
              <Download className="h-4 w-4 mr-2" />
              Export
            </Button>
          </PermissionGate>
        )}
      </div>
      <div className="flex-1 min-h-0 overflow-y-auto mt-4">
        <TransactionHistoryTable
          data={rows}
          isLoading={isLoading}
          onRowClick={handleRowClick}
          hasActiveFilters={hasActiveFilters}
          emptyNoFiltersText={emptyNoFiltersText}
          emptyWithFiltersText={emptyWithFiltersText}
          pagination={pagination}
          onPageChange={handlePageChange}
          onRequestReturn={showReturnActions ? setReturnDialogTx : undefined}
        />
      </div>
      <TransactionDetailSheet
        redemptionId={selectedId}
        open={selectedId !== null}
        onClose={() => setSelectedId(null)}
      />
      {showExport && (
        <ExportDialog
          open={exportDialogOpen}
          onClose={() => setExportDialogOpen(false)}
          filters={filters}
          scope={exportScope}
        />
      )}
      {returnDialogTx && (
        <RequestReturnDialog
          open={returnDialogTx !== null}
          onOpenChange={(open) => { if (!open) setReturnDialogTx(null); }}
          redemptionId={returnDialogTx.id}
          amount={returnDialogTx.amount}
          currencyId={returnDialogTx.currencyId}
          catalogItemName={returnDialogTx.catalogItemName}
          onSuccess={handleReturnSuccess}
        />
      )}
    </>
  );
}

export default function TransactionHistoryPage() {
  const { user } = useAuth();
  const { can } = usePermissions();
  const isPartnerAdmin = user?.clientRoleName === "Partner Admin";
  // Company redemption is not yet supported — hide the Company tab until it ships.
  const showCompanyTab =
    COMPANY_REDEMPTION_ENABLED && isPartnerAdmin && can("action.redemption.view_history");
  const hasReturnPermission = can("action.redemption.return.request");

  // Always show tabs: Personal (+ My Returns tab if user has return permission)
  // When user is a Partner Admin, also show Company tab
  return (
    <div className="flex flex-col h-full animate-route-in" aria-label="Main content">
      <div className="shrink-0 mb-6">
        <PageBanner
          theme="default"
          title="Transaction history"
          subtitle="View your redemption history"
        />
      </div>

      <Tabs defaultValue="personal" aria-label="Transaction history tabs" className="flex flex-col flex-1 min-h-0">
        <TabsList className="self-start shrink-0">
          <TabsTrigger value="personal">Personal</TabsTrigger>
          {showCompanyTab && <TabsTrigger value="company">Company</TabsTrigger>}
          {hasReturnPermission && <TabsTrigger value="my-returns">My Returns</TabsTrigger>}
        </TabsList>

        <TabsContent
          value="personal"
          className="mt-0 flex-1 min-h-0 data-[state=active]:flex flex-col"
        >
          <HistoryPane
            useHistoryQuery={usePersonalRedemptions}
            errorToast="Could not load transactions"
            showExport
            exportScope="PERSONAL"
            showReturnActions={hasReturnPermission}
          />
        </TabsContent>

        {showCompanyTab && (
          <TabsContent
            value="company"
            className="mt-0 flex-1 min-h-0 data-[state=active]:flex flex-col"
          >
            <HistoryPane
              useHistoryQuery={useCompanyRedemptions}
              errorToast="Could not load company transactions"
              emptyNoFiltersText="No company transactions yet"
              emptyWithFiltersText="No company transactions match your filters"
              showExport
              exportScope="COMPANY"
              showReturnActions={hasReturnPermission}
            />
          </TabsContent>
        )}

        {hasReturnPermission && (
          <TabsContent
            value="my-returns"
            className="mt-0 flex-1 min-h-0 data-[state=active]:flex flex-col pt-4"
          >
            <MyReturnsTab />
          </TabsContent>
        )}
      </Tabs>
    </div>
  );
}
