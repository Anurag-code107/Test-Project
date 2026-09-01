// Adapted from: src/pages/client-admin/ManageIncentivesPage.tsx (production analog from Mirror)
import { useState, useCallback, useEffect } from "react";
import { toast } from "sonner";
import { ClipboardList } from "lucide-react";
import { PageBanner } from "@/components/PageBanner";
import { ApprovalQueueTable } from "@/components/redemption/ApprovalQueueTable";
import { ApprovalQueueFilters } from "@/components/redemption/ApprovalQueueFilters";
import { ApproveConfirmDialog } from "@/components/redemption/ApproveConfirmDialog";
import { RejectDialog } from "@/components/redemption/RejectDialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ReturnsApprovalTab } from "@/components/redemption-returns/ReturnsApprovalTab";
import { useApprovalQueue } from "@/hooks/redemption/useApprovalQueue";
import { useAdminReturns } from "@/hooks/useAdminReturns";
import { useAuth } from "@/hooks/useAuth";
import type { ApprovalQueueFilters as FiltersType, PaginationMeta } from "@/types/redemption/redemption.types";

const DEFAULT_FILTERS: FiltersType = {
  page: 0,
  size: 20,
};

const EMPTY_PAGINATION: PaginationMeta = {
  page: 0,
  pageSize: 20,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
  hasPrevious: false,
};

export default function ApprovalQueuePage() {
  const { user } = useAuth();
  const clientId = user?.clientId ?? "";

  const [activeTab, setActiveTab] = useState<"redemptions" | "returns">("redemptions");
  const [filters, setFilters] = useState<FiltersType>(DEFAULT_FILTERS);
  const [approveDialogId, setApproveDialogId] = useState<string | null>(null);
  const [rejectDialogId, setRejectDialogId] = useState<string | null>(null);

  const { data, isLoading, isError } = useApprovalQueue(filters);
  const { data: returnsData } = useAdminReturns({ status: "PENDING_APPROVAL", page: 0, size: 1 });

  useEffect(() => {
    if (isError) toast.error("Could not load approval queue");
  }, [isError]);

  const items = data?.data ?? [];
  const pagination: PaginationMeta = data
    ? {
        page: data.page,
        pageSize: data.pageSize,
        totalElements: data.totalElements,
        totalPages: data.totalPages,
        hasNext: data.hasNext,
        hasPrevious: data.hasPrevious,
      }
    : EMPTY_PAGINATION;

  const handlePageChange = useCallback((page: number) => {
    setFilters((prev) => ({ ...prev, page }));
  }, []);

  const handleApprove = useCallback((id: string) => setApproveDialogId(id), []);
  const handleReject = useCallback((id: string) => setRejectDialogId(id), []);

  return (
    <main className="flex flex-col h-full animate-route-in" aria-label="Approval Queue">
      <div className="shrink-0 mb-6">
        <PageBanner
          theme="default"
          title="Approval Queue"
          subtitle="Review and action pending redemption requests"
          actions={
            <div className="flex items-center gap-2">
              <ClipboardList className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm text-muted-foreground tabular-nums">
                {activeTab === "returns"
                  ? (returnsData?.totalElements ?? 0)
                  : pagination.totalElements} pending
              </span>
            </div>
          }
        />
      </div>

      <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as "redemptions" | "returns")} className="flex-1 min-h-0 flex flex-col">
        <TabsList className="shrink-0 self-start mb-5" aria-label="Approval queue sections">
          <TabsTrigger value="redemptions">Redemptions</TabsTrigger>
          <TabsTrigger value="returns">Returns</TabsTrigger>
        </TabsList>

        <TabsContent value="redemptions" className="flex-1 min-h-0 overflow-y-auto mt-0">
          <div className="mb-4">
            <ApprovalQueueFilters
              filters={filters}
              onChange={(updated) => setFilters({ ...updated, page: 0 })}
            />
          </div>
          <ApprovalQueueTable
            items={items}
            pagination={pagination}
            onApprove={handleApprove}
            onReject={handleReject}
            isLoading={isLoading}
            onPageChange={handlePageChange}
          />
        </TabsContent>

        <TabsContent value="returns" className="flex-1 min-h-0 overflow-y-auto mt-0">
          {clientId ? (
            <ReturnsApprovalTab clientId={clientId} />
          ) : null}
        </TabsContent>
      </Tabs>

      <ApproveConfirmDialog
        redemptionId={approveDialogId ?? ""}
        open={approveDialogId !== null}
        onOpenChange={(open) => { if (!open) setApproveDialogId(null); }}
      />

      <RejectDialog
        redemptionId={rejectDialogId ?? ""}
        open={rejectDialogId !== null}
        onOpenChange={(open) => { if (!open) setRejectDialogId(null); }}
      />
    </main>
  );
}
