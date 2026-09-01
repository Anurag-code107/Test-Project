import { useState, useMemo, useRef, useCallback, memo } from "react";
import { PermissionGate } from "@/components/PermissionGate";
import { useFeatures } from "@/hooks/useFeatures";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import {
  Search,
  Megaphone,
  GraduationCap,
  FileCheck,
  Layers,
  Plus,
  Route,
  BookOpen,
  Pencil,
  ChevronDown,
  Trash2,
  Zap,
  Send,
  RotateCcw,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
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
import { ManagedIncentiveCard } from "@/components/manage-incentives/ManagedIncentiveCard";
import { IncentiveGridSkeleton } from "@/components/skeletons/IncentiveGridSkeleton";
import { JourneyIncentiveCard } from "@/components/journey/JourneyIncentiveCard";
import { IncentiveDetailDrawer } from "@/components/view-incentives/IncentiveDetailDrawer";
import {
  useIncentives,
  useUpdateIncentiveStatus,
  useDeleteIncentive,
  useSubmitForApproval,
  useResubmitForApproval,
} from "@/hooks/useIncentiveApi";
import type {
  IncentiveType,
  IncentiveStatus,
  IncentiveResponse,
} from "@/types/incentive.types";
import { INCENTIVE_STATUS_LABELS } from "@/types/incentive.types";
import { cn } from "@/lib/utils";
import { PageBanner } from "@/components/PageBanner";

// --- Type/icon maps ---

type DisplayType = IncentiveType | "ENABLEMENT";

const engagementIcons: Record<DisplayType, React.ReactNode> = {
  JOURNEY: <Layers className="h-4 w-4" />,
  SALES: <Megaphone className="h-4 w-4" />,
  TRAINING: <GraduationCap className="h-4 w-4" />,
  ACTIVITY: <FileCheck className="h-4 w-4" />,
  ENABLEMENT: <BookOpen className="h-4 w-4" />,
};

const engagementColors: Record<DisplayType, string> = {
  JOURNEY: "text-indigo-500",
  SALES: "text-[hsl(217_91%_55%)]",
  TRAINING: "text-amber-500",
  ACTIVITY: "text-blue-500",
  ENABLEMENT: "text-emerald-500",
};

const engagementGradients: Record<DisplayType, string> = {
  SALES:
    "from-[hsl(217_91%_60%/0.08)] via-[hsl(217_91%_60%/0.04)] to-transparent border-[hsl(217_91%_60%/0.15)]",
  TRAINING:
    "from-[hsl(38_90%_50%/0.08)] via-[hsl(38_90%_50%/0.04)] to-transparent border-[hsl(38_90%_50%/0.15)]",
  ACTIVITY:
    "from-[hsl(217_80%_55%/0.08)] via-[hsl(217_80%_55%/0.04)] to-transparent border-[hsl(217_80%_55%/0.15)]",
  JOURNEY:
    "from-[hsl(245_58%_55%/0.08)] via-[hsl(245_58%_55%/0.04)] to-transparent border-[hsl(245_58%_55%/0.15)]",
  ENABLEMENT:
    "from-[hsl(160_55%_42%/0.08)] via-[hsl(160_55%_42%/0.04)] to-transparent border-[hsl(160_55%_42%/0.15)]",
};

const engagementIconBg: Record<DisplayType, string> = {
  SALES: "bg-[hsl(217_91%_60%/0.1)]",
  TRAINING: "bg-[hsl(38_90%_50%/0.1)]",
  ACTIVITY: "bg-[hsl(217_80%_55%/0.1)]",
  JOURNEY: "bg-[hsl(245_58%_55%/0.1)]",
  ENABLEMENT: "bg-[hsl(160_55%_42%/0.1)]",
};

const engagementCountBg: Record<DisplayType, string> = {
  SALES: "bg-[hsl(217_91%_60%/0.1)] text-[hsl(217_91%_50%)]",
  TRAINING: "bg-[hsl(38_90%_50%/0.1)] text-[hsl(38_80%_40%)]",
  ACTIVITY: "bg-[hsl(217_80%_55%/0.1)] text-[hsl(217_80%_45%)]",
  JOURNEY: "bg-[hsl(245_58%_55%/0.1)] text-[hsl(245_58%_45%)]",
  ENABLEMENT: "bg-[hsl(160_55%_42%/0.1)] text-[hsl(160_55%_35%)]",
};

const createButtonLabels: Record<DisplayType, string> = {
  SALES: "Create Sales Incentive",
  TRAINING: "Create Training Incentive",
  ACTIVITY: "Create Activity Incentive",
  JOURNEY: "Create Journey Incentive",
  ENABLEMENT: "Create Enablement Incentive",
};

const sectionLabels: Record<DisplayType, string> = {
  SALES: "Sales Incentives",
  TRAINING: "Training Incentives",
  ACTIVITY: "Activity Incentives",
  JOURNEY: "Journeys",
  ENABLEMENT: "Enablement Incentives",
};

const allStatuses: IncentiveStatus[] = [
  "DRAFT",
  "PENDING_APPROVAL",
  "DENIED",
  "ACTIVE",
  "INACTIVE",
];

const defaultStatuses: IncentiveStatus[] = [
  "DRAFT",
  "PENDING_APPROVAL",
  "DENIED",
  "ACTIVE",
];

const enablementSubTypes: IncentiveType[] = ["TRAINING", "ACTIVITY"];

// --- Tab definition ---

interface TabDef {
  id: string;
  label: string;
  icon: React.ReactNode;
}

const tabs: TabDef[] = [
  { id: "sales", label: "Sales", icon: <Megaphone className="h-3.5 w-3.5" /> },
  {
    id: "enablement",
    label: "Enablement",
    icon: <BookOpen className="h-3.5 w-3.5" />,
  },
  {
    id: "journeys",
    label: "Journeys",
    icon: <Route className="h-3.5 w-3.5" />,
  },
];

// --- GridSection ---

interface GridSectionProps {
  type: DisplayType;
  incentives: IncentiveResponse[];
  onStatusChange: (id: string, newStatus: IncentiveStatus) => void;
  onDelete: (id: string) => void;
  onCreateNew: () => void;
  onEdit: (id: string) => void;
  onSubmitForApproval: (id: string) => void;
  onResubmitForApproval: (id: string) => void;
  onCardClick: (id: string) => void;
}

export const GridSection = memo(function GridSection({
  type,
  incentives,
  onStatusChange,
  onDelete,
  onCreateNew,
  onEdit,
  onSubmitForApproval,
  onResubmitForApproval,
  onCardClick,
}: GridSectionProps) {
  return (
    <section className="flex flex-col flex-1 min-h-0">
      {/* Gradient banner title strip */}
      <div
        className={cn(
          "flex items-center justify-between px-5 py-3.5 rounded-xl border bg-gradient-to-r mb-4 shrink-0",
          engagementGradients[type],
        )}
      >
        <div className="flex items-center gap-3">
          <div
            className={cn(
              "flex items-center justify-center h-8 w-8 rounded-lg",
              engagementIconBg[type],
            )}
          >
            <span className={engagementColors[type]}>
              {engagementIcons[type]}
            </span>
          </div>
          <h2 className="text-xl font-semibold text-foreground tracking-tight">
            {sectionLabels[type]}
          </h2>
          <span
            className={cn(
              "text-xs font-semibold tabular-nums px-2 py-0.5 rounded-full",
              engagementCountBg[type],
            )}
          >
            {incentives.length}
          </span>
        </div>
        <PermissionGate permission="action.incentive.create">
          <Button className="gap-2 h-9" onClick={onCreateNew}>
            <Plus className="h-4 w-4" />
            {createButtonLabels[type]}
          </Button>
        </PermissionGate>
      </div>

      {/* Grid */}
      <div
        className="overflow-y-auto flex-1"
        style={{ scrollbarWidth: "thin" }}
      >
        <div className="grid grid-cols-2 gap-4">
          {incentives.map((incentive) => (
            <ManagedIncentiveCard
              key={incentive.id}
              incentive={incentive}
              onStatusChange={onStatusChange}
              onDelete={onDelete}
              onEdit={() => onEdit(incentive.id)}
              onSubmitForApproval={onSubmitForApproval}
              onResubmitForApproval={onResubmitForApproval}
              onClick={() => onCardClick(incentive.id)}
            />
          ))}
        </div>
      </div>
    </section>
  );
});

// --- EmptySection ---

function EmptySection({
  type,
  onCreateNew,
}: {
  type: DisplayType;
  onCreateNew: () => void;
}) {
  return (
    <section className="flex flex-col flex-1 min-h-0">
      {/* Gradient banner title strip */}
      <div
        className={cn(
          "flex items-center justify-between px-5 py-3.5 rounded-xl border bg-gradient-to-r mb-4 shrink-0",
          engagementGradients[type],
        )}
      >
        <div className="flex items-center gap-3">
          <div
            className={cn(
              "flex items-center justify-center h-8 w-8 rounded-lg",
              engagementIconBg[type],
            )}
          >
            <span className={engagementColors[type]}>
              {engagementIcons[type]}
            </span>
          </div>
          <h2 className="text-xl font-semibold text-foreground tracking-tight">
            {sectionLabels[type]}
          </h2>
          <span
            className={cn(
              "text-xs font-semibold tabular-nums px-2 py-0.5 rounded-full",
              engagementCountBg[type],
            )}
          >
            0
          </span>
        </div>
        <PermissionGate permission="action.incentive.create">
          <Button className="gap-2 h-9" onClick={onCreateNew}>
            <Plus className="h-4 w-4" />
            {createButtonLabels[type]}
          </Button>
        </PermissionGate>
      </div>

      {/* Empty state */}
      <div className="flex flex-col items-center justify-center py-20 rounded-xl border border-dashed border-border">
        <div className={cn("mb-3", engagementColors[type])}>
          {engagementIcons[type]}
        </div>
        <p className="text-sm text-muted-foreground mb-4">
          No {sectionLabels[type].toLowerCase()} found
        </p>
        <PermissionGate permission="action.incentive.create">
          <Button
            variant="outline"
            size="sm"
            className="gap-1.5 h-8 text-xs"
            onClick={() => onCreateNew()}
          >
            <Plus className="h-3 w-3" />
            Create First Incentive
          </Button>
        </PermissionGate>
      </div>
    </section>
  );
}

// --- Journey Grid Section ---

const statusStylesLocal: Record<IncentiveStatus, string> = {
  DRAFT: "bg-muted text-muted-foreground",
  PENDING_APPROVAL:
    "bg-[hsl(38_90%_50%/0.12)] text-amber-600 ring-1 ring-amber-500/20",
  DENIED: "bg-[hsl(0_65%_50%/0.08)] text-[hsl(0_65%_50%)]",
  ACTIVE: "bg-[hsl(95_55%_42%/0.08)] text-[hsl(95_55%_42%)]",
  INACTIVE: "bg-[hsl(0_65%_50%/0.08)] text-[hsl(0_65%_50%)]",
};

function getAllowedStatusesLocal(
  currentStatus: IncentiveStatus,
): IncentiveStatus[] {
  switch (currentStatus) {
    case "ACTIVE":
      return ["ACTIVE", "INACTIVE"];
    case "INACTIVE":
      return ["INACTIVE", "ACTIVE"];
    case "DRAFT":
      return ["DRAFT", "INACTIVE"];
    case "PENDING_APPROVAL":
      return ["PENDING_APPROVAL", "INACTIVE"];
    default:
      return [currentStatus];
  }
}

interface JourneyGridSectionProps {
  incentives: IncentiveResponse[];
  allIncentives: IncentiveResponse[];
  onStatusChange: (id: string, newStatus: IncentiveStatus) => void;
  onDelete: (id: string) => void;
  onCreateNew: () => void;
  onEdit: (id: string) => void;
  onSubmitForApproval: (id: string) => void;
  onResubmitForApproval: (id: string) => void;
  onCardClick: (id: string) => void;
}

export const JourneyGridSection = memo(function JourneyGridSection({
  incentives,
  allIncentives,
  onStatusChange,
  onDelete,
  onCreateNew,
  onEdit,
  onSubmitForApproval,
  onResubmitForApproval,
  onCardClick,
}: JourneyGridSectionProps) {
  const [deleteTarget, setDeleteTarget] = useState<IncentiveResponse | null>(
    null,
  );

  return (
    <section className="flex flex-col flex-1 min-h-0">
      {/* Gradient banner title strip */}
      <div
        className={cn(
          "flex items-center justify-between px-5 py-3.5 rounded-xl border bg-gradient-to-r mb-4 shrink-0",
          engagementGradients.JOURNEY,
        )}
      >
        <div className="flex items-center gap-3">
          <div
            className={cn(
              "flex items-center justify-center h-8 w-8 rounded-lg",
              engagementIconBg.JOURNEY,
            )}
          >
            <span className={engagementColors.JOURNEY}>
              {engagementIcons.JOURNEY}
            </span>
          </div>
          <h2 className="text-xl font-semibold text-foreground tracking-tight">
            {sectionLabels.JOURNEY}
          </h2>
          <span
            className={cn(
              "text-xs font-semibold tabular-nums px-2 py-0.5 rounded-full",
              engagementCountBg.JOURNEY,
            )}
          >
            {incentives.length}
          </span>
        </div>
        <PermissionGate permission="action.incentive.create">
          <Button className="gap-2 h-9" onClick={onCreateNew}>
            <Plus className="h-4 w-4" />
            {createButtonLabels.JOURNEY}
          </Button>
        </PermissionGate>
      </div>

      {/* Journey cards — stacked vertically, full width */}
      <div
        className="overflow-y-auto flex-1 space-y-4"
        style={{ scrollbarWidth: "thin" }}
      >
        {incentives.map((incentive) => (
          <JourneyIncentiveCard
            key={incentive.id}
            incentive={incentive}
            variant="manage"
            allIncentives={allIncentives}
            onClick={() => onCardClick(incentive.id)}
            onStageClick={(childIncentive) => onCardClick(childIncentive.id)}
            onStageEdit={(childIncentive) => onEdit(childIncentive.id)}
            headerActions={
              <JourneyHeaderActions
                incentive={incentive}
                onStatusChange={onStatusChange}
                onDelete={() => setDeleteTarget(incentive)}
                onEdit={() => onEdit(incentive.id)}
                onSubmitForApproval={() => onSubmitForApproval(incentive.id)}
                onResubmitForApproval={() =>
                  onResubmitForApproval(incentive.id)
                }
              />
            }
          />
        ))}
      </div>

      {/* Delete confirmation */}
      <AlertDialog
        open={!!deleteTarget}
        onOpenChange={(o) => {
          if (!o) setDeleteTarget(null);
        }}
      >
        <AlertDialogContent onClick={(e) => e.stopPropagation()}>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Journey</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete &quot;{deleteTarget?.name}&quot;?
              This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                if (deleteTarget) {
                  onDelete(deleteTarget.id);
                  setDeleteTarget(null);
                }
              }}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
});

/* Header actions (status dropdown + edit) for journey cards in manage mode */

function JourneyHeaderActions({
  incentive,
  onStatusChange,
  onDelete,
  onEdit,
  onSubmitForApproval,
  onResubmitForApproval,
}: {
  incentive: IncentiveResponse;
  onStatusChange: (id: string, newStatus: IncentiveStatus) => void;
  onDelete: () => void;
  onEdit: () => void;
  onSubmitForApproval: () => void;
  onResubmitForApproval: () => void;
}) {
  const showActivateButton =
    incentive.status === "DRAFT" && !incentive.requiresApproval;
  const showSubmitButton =
    incentive.status === "DRAFT" && incentive.requiresApproval === true;
  const showResubmitButton = incentive.status === "DENIED";

  return (
    <div
      className="flex items-center gap-1.5"
      onClick={(e) => e.stopPropagation()}
    >
      {showActivateButton && (
        <PermissionGate permission="action.incentive.activate">
          <Button
            variant="default"
            size="sm"
            className="h-7 text-xs px-2.5"
            onClick={() => onStatusChange(incentive.id, "ACTIVE")}
          >
            <Zap className="h-3 w-3 mr-1" />
            Activate
          </Button>
        </PermissionGate>
      )}
      {showSubmitButton && (
        <PermissionGate permission="action.incentive.submit_approval">
          <Button
            variant="default"
            size="sm"
            className="h-7 text-xs px-2.5"
            onClick={onSubmitForApproval}
          >
            <Send className="h-3 w-3 mr-1" />
            Submit
          </Button>
        </PermissionGate>
      )}
      {showResubmitButton && (
        <Button
          variant="outline"
          size="sm"
          className="h-7 text-xs px-2.5 border-amber-500/30 text-amber-600 hover:bg-amber-500/5"
          onClick={onResubmitForApproval}
        >
          <RotateCcw className="h-3 w-3 mr-1" />
          Resubmit
        </Button>
      )}

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button
            className={cn(
              "shrink-0 text-xs font-medium px-2 py-0.5 rounded-md inline-flex items-center gap-1 transition-opacity hover:opacity-80",
              statusStylesLocal[incentive.status],
            )}
          >
            {INCENTIVE_STATUS_LABELS[incentive.status]}
            <ChevronDown className="h-2.5 w-2.5 opacity-50" />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-48 rounded-xl">
          <div className="px-2.5 py-1.5 text-xs font-medium text-muted-foreground">
            Change Status
          </div>
          {getAllowedStatusesLocal(incentive.status).map((status) => (
            <DropdownMenuItem
              key={status}
              onClick={() => onStatusChange(incentive.id, status)}
              disabled={status === incentive.status}
              className="cursor-pointer text-sm"
            >
              {INCENTIVE_STATUS_LABELS[status]}
              {status === incentive.status && (
                <span className="ml-auto text-xs text-muted-foreground">
                  current
                </span>
              )}
            </DropdownMenuItem>
          ))}
          <PermissionGate permission="action.incentive.delete">
            <DropdownMenuSeparator />
            <DropdownMenuItem
              className="text-destructive focus:text-destructive cursor-pointer text-sm"
              onClick={onDelete}
            >
              <Trash2 className="h-3.5 w-3.5 mr-2" />
              Delete Journey
            </DropdownMenuItem>
          </PermissionGate>
        </DropdownMenuContent>
      </DropdownMenu>

      <PermissionGate permission="action.incentive.edit">
        <Button
          variant="ghost"
          size="sm"
          className="shrink-0 h-7 w-7 p-0"
          onClick={onEdit}
        >
          <Pencil className="h-3.5 w-3.5" />
        </Button>
      </PermissionGate>
    </div>
  );
}

// --- Main Page ---

function ManageIncentivesPage() {
  const navigate = useNavigate();
  const { has } = useFeatures();
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedStatuses, setSelectedStatuses] =
    useState<IncentiveStatus[]>(defaultStatuses);
  const [activeTab, setActiveTab] = useState("sales");

  // Tabs hidden when the tenant's tier doesn't include the corresponding
  // feature. Sales / Enablement are always available; Journeys requires
  // journey_incentives.
  const visibleTabs = useMemo(
    () => tabs.filter((tab) => tab.id !== "journeys" || has("journey_incentives")),
    [has],
  );
  const [drawerIncentiveId, setDrawerIncentiveId] = useState<string | null>(
    null,
  );
  const contentRef = useRef<HTMLDivElement>(null);

  const {
    data: apiData,
    isLoading,
    refetch,
  } = useIncentives({ pageSize: 500 });
  // Destructure the stable `.mutate` function from each mutation hook —
  // TanStack Query returns a new wrapper object on every render, so depending
  // on the whole mutation in `useCallback` defeats `sharedProps`'s memoization
  // and the GridSection memo bailout (BUG-073).
  const { mutate: updateStatusMutate } = useUpdateIncentiveStatus();
  const { mutate: deleteMutate } = useDeleteIncentive();
  const { mutate: submitForApprovalMutate } = useSubmitForApproval();
  const { mutate: resubmitForApprovalMutate } = useResubmitForApproval();

  const allIncentives: IncentiveResponse[] = useMemo(() => {
    return apiData ? apiData.data : [];
  }, [apiData]);

  const filteredIncentives = useMemo(() => {
    return allIncentives.filter((inc) => {
      const matchesSearch =
        !searchQuery ||
        inc.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        (inc.description ?? "")
          .toLowerCase()
          .includes(searchQuery.toLowerCase());
      const matchesStatus =
        selectedStatuses.length === 0 || selectedStatuses.includes(inc.status);
      return matchesSearch && matchesStatus;
    });
  }, [allIncentives, searchQuery, selectedStatuses]);

  const handleCreateNew = useCallback(() => {
    if (activeTab === "sales") {
      navigate("/builder", { state: { createType: "SALES" } });
    } else if (activeTab === "enablement") {
      navigate("/builder", { state: { flow: "enablement" } });
    } else if (activeTab === "journeys") {
      navigate("/builder", { state: { createType: "JOURNEY" } });
    } else {
      navigate("/builder");
    }
  }, [activeTab, navigate]);

  const handleEdit = useCallback(
    (id: string) => navigate("/builder", { state: { editId: id } }),
    [navigate],
  );

  const handleStatusChange = useCallback(
    (id: string, newStatus: IncentiveStatus) => {
      const incentive = allIncentives.find((inc) => inc.id === id);
      const oldLabel = incentive
        ? INCENTIVE_STATUS_LABELS[incentive.status]
        : "";
      const newLabel = INCENTIVE_STATUS_LABELS[newStatus];
      updateStatusMutate(
        { id, data: { status: newStatus } },
        {
          onSuccess: () => {
            toast.success(`Status changed from ${oldLabel} to ${newLabel}`);
          },
          onError: (err) => {
            toast.error(
              err instanceof Error ? err.message : "Failed to update status",
            );
          },
        },
      );
    },
    [allIncentives, updateStatusMutate],
  );

  const handleDelete = useCallback(
    (id: string) => {
      const incentive = allIncentives.find((inc) => inc.id === id);
      deleteMutate(id, {
        onSuccess: () => {
          toast.success("Incentive Deleted", {
            description: incentive
              ? `"${incentive.name}" has been deleted.`
              : "The incentive has been deleted.",
          });
        },
        onError: (error) => {
          const message =
            error instanceof Error ? error.message : "Please try again.";
          toast.error("Failed to delete incentive", { description: message });
        },
      });
    },
    [allIncentives, deleteMutate],
  );

  const handleSubmitForApproval = useCallback(
    (id: string) => {
      const incentive = allIncentives.find((inc) => inc.id === id);
      submitForApprovalMutate(id, {
        onSuccess: () => {
          toast.success("Submitted for Approval", {
            description: incentive
              ? `"${incentive.name}" has been submitted — emails sent to approvers.`
              : "The incentive has been submitted for approval.",
          });
        },
        onError: (error) => {
          const message =
            error instanceof Error ? error.message : "Please try again.";
          toast.error("Failed to submit for approval", {
            description: message,
          });
        },
      });
    },
    [allIncentives, submitForApprovalMutate],
  );

  const handleResubmitForApproval = useCallback(
    (id: string) => {
      const incentive = allIncentives.find((inc) => inc.id === id);
      resubmitForApprovalMutate(id, {
        onSuccess: () => {
          toast.success("Resubmitted for Approval", {
            description: incentive
              ? `"${incentive.name}" has been resubmitted — emails sent to all approvers.`
              : "The incentive has been resubmitted for approval.",
          });
        },
        onError: (error) => {
          const message =
            error instanceof Error ? error.message : "Please try again.";
          toast.error("Failed to resubmit for approval", {
            description: message,
          });
        },
      });
    },
    [allIncentives, resubmitForApprovalMutate],
  );

  const handleCardClick = useCallback(
    (id: string) => setDrawerIncentiveId(id),
    [],
  );

  const handleTabChange = (tab: string) => {
    setActiveTab(tab);
    refetch();

    // Replay fade animation on the content area
    const el = contentRef.current;
    if (el && !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      el.classList.remove("animate-route-in");
      void el.offsetWidth;
      el.classList.add("animate-route-in");
    }
  };

  const toggleStatus = (status: IncentiveStatus) => {
    setSelectedStatuses((prev) =>
      prev.includes(status)
        ? prev.filter((s) => s !== status)
        : [...prev, status],
    );
  };

  const sharedProps = useMemo(
    () => ({
      onStatusChange: handleStatusChange,
      onDelete: handleDelete,
      onCreateNew: handleCreateNew,
      onEdit: handleEdit,
      onSubmitForApproval: handleSubmitForApproval,
      onResubmitForApproval: handleResubmitForApproval,
      onCardClick: handleCardClick,
    }),
    [
      handleStatusChange,
      handleDelete,
      handleCreateNew,
      handleEdit,
      handleSubmitForApproval,
      handleResubmitForApproval,
      handleCardClick,
    ],
  );

  const salesIncentives = useMemo(
    () => filteredIncentives.filter((inc) => inc.incentiveType === "SALES"),
    [filteredIncentives],
  );

  const enablementIncentives = useMemo(
    () =>
      filteredIncentives.filter((inc) =>
        enablementSubTypes.includes(inc.incentiveType),
      ),
    [filteredIncentives],
  );

  const journeyIncentives = useMemo(
    () => filteredIncentives.filter((inc) => inc.incentiveType === "JOURNEY"),
    [filteredIncentives],
  );

  const tabContent: Record<
    string,
    { type: DisplayType; incentives: IncentiveResponse[] }
  > = {
    sales: { type: "SALES", incentives: salesIncentives },
    enablement: { type: "ENABLEMENT", incentives: enablementIncentives },
    journeys: { type: "JOURNEY", incentives: journeyIncentives },
  };

  const current = tabContent[activeTab];

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="shrink-0 mb-6">
        <PageBanner
          theme="incentives"
          title="Manage Incentives"
          subtitle="View and manage all incentive programs"
        />

        {/* Filters row — tabs left, search + status right */}
        <div className="flex items-center justify-between mt-5">
          {/* Tab pills (left side) */}
          <div className="flex items-center gap-1">
            {visibleTabs.map((tab) => (
              <button
                key={tab.id}
                onClick={() => handleTabChange(tab.id)}
                className={cn(
                  "inline-flex items-center gap-1.5 h-9 px-3.5 rounded-lg text-sm font-medium transition-[background-color,color,box-shadow] duration-150",
                  activeTab === tab.id
                    ? "bg-primary text-primary-foreground shadow-sm"
                    : "text-muted-foreground hover:text-foreground hover:bg-muted",
                )}
              >
                {tab.icon}
                {tab.label}
              </button>
            ))}
          </div>

          {/* Search + Status (right side) */}
          <div className="flex items-center gap-3">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              <input
                placeholder="Search incentives..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="h-9 w-[220px] rounded-lg border border-border bg-background pl-9 pr-3 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/40 transition-[border-color,box-shadow]"
              />
            </div>

            <div className="w-px h-5 bg-border" />

            <Popover>
              <PopoverTrigger asChild>
                <button className="inline-flex items-center gap-2 h-9 px-3 rounded-lg border border-border bg-background text-sm text-muted-foreground hover:border-muted-foreground/40 transition-colors">
                  {selectedStatuses.length === allStatuses.length
                    ? "All Statuses"
                    : selectedStatuses.length === 0
                      ? "No Status"
                      : `Status (${selectedStatuses.length})`}
                </button>
              </PopoverTrigger>
              <PopoverContent className="w-[200px] p-3 rounded-xl" align="end">
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium text-foreground">
                      Status Filter
                    </span>
                    <button
                      className="text-xs text-muted-foreground hover:text-foreground transition-colors"
                      onClick={() => setSelectedStatuses(defaultStatuses)}
                    >
                      Reset
                    </button>
                  </div>
                  <div className="space-y-1.5">
                    {allStatuses.map((status) => (
                      <label
                        key={status}
                        className="flex items-center gap-2.5 cursor-pointer hover:bg-muted -mx-1 px-1 py-1.5 rounded-lg transition-colors"
                      >
                        <Checkbox
                          checked={selectedStatuses.includes(status)}
                          onCheckedChange={() => toggleStatus(status)}
                        />
                        <span className="text-sm text-foreground">
                          {INCENTIVE_STATUS_LABELS[status]}
                        </span>
                      </label>
                    ))}
                  </div>
                </div>
              </PopoverContent>
            </Popover>
          </div>
        </div>
      </div>

      {/* Content */}
      <div
        ref={contentRef}
        className="flex-1 min-h-0 flex flex-col"
        data-tour="manage-incentives-content"
      >
        {isLoading ? (
          <IncentiveGridSkeleton />
        ) : current ? (
          current.incentives.length === 0 ? (
            <EmptySection type={current.type} onCreateNew={handleCreateNew} />
          ) : activeTab === "journeys" ? (
            <JourneyGridSection
              incentives={current.incentives}
              allIncentives={allIncentives}
              {...sharedProps}
            />
          ) : (
            <GridSection
              type={current.type}
              incentives={current.incentives}
              {...sharedProps}
            />
          )
        ) : null}
      </div>

      <IncentiveDetailDrawer
        open={!!drawerIncentiveId}
        onOpenChange={(o) => {
          if (!o) setDrawerIncentiveId(null);
        }}
        incentiveId={drawerIncentiveId}
        listIncentive={allIncentives.find((i) => i.id === drawerIncentiveId)}
      />
    </div>
  );
}

export default ManageIncentivesPage;
