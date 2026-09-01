import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
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
import {
  MoreVertical,
  Plug,
  PlugZap,
  Trash2,
  Pencil,
  Loader2,
} from "lucide-react";
import { toast } from "sonner";
import type { ConnectorResponse } from "@/types/connector.types";
import { CONNECTOR_TYPE_LABELS } from "@/types/connector.types";
import { useDeleteConnector, useTestConnection } from "@/hooks/useConnectorApi";
import { cn } from "@/lib/utils";

interface ConnectorCardProps {
  connector: ConnectorResponse;
  onEdit: (connector: ConnectorResponse) => void;
}

const STATUS_CONFIG: Record<
  string,
  { label: string; dot: string; text: string }
> = {
  CONNECTED: {
    label: "Connected",
    dot: "bg-[hsl(95_55%_42%)]",
    text: "text-[hsl(95_55%_36%)]",
  },
  DISCONNECTED: {
    label: "Disconnected",
    dot: "bg-[hsl(200_10%_60%)]",
    text: "text-[hsl(200_10%_46%)]",
  },
  ERROR: {
    label: "Error",
    dot: "bg-[hsl(0_65%_50%)]",
    text: "text-[hsl(0_65%_42%)]",
  },
  SYNCING: {
    label: "Syncing",
    dot: "bg-[hsl(217_91%_55%)]",
    text: "text-[hsl(217_91%_45%)]",
  },
};

const CONNECTOR_ICONS: Record<string, string> = {
  SALESFORCE: "☁️",
  MICROSOFT_DYNAMICS_365: "🔷",
  SNOWFLAKE: "❄️",
  HUBSPOT: "🟠",
};

export function ConnectorCard({ connector, onEdit }: ConnectorCardProps) {
  const [deleteOpen, setDeleteOpen] = useState(false);
  const deleteMutation = useDeleteConnector();
  const testMutation = useTestConnection();

  const statusInfo = (STATUS_CONFIG[connector.status] ??
    STATUS_CONFIG.DISCONNECTED)!;

  function handleTest() {
    testMutation.mutate(connector.id, {
      onSuccess: (result) => {
        if (result.success) {
          toast.success(result.message);
        } else {
          toast.error(result.message);
        }
      },
      onError: () => toast.error("Failed to test connection"),
    });
  }

  function handleDelete() {
    deleteMutation.mutate(connector.id, {
      onSuccess: () => {
        toast.success(`Deleted connector "${connector.name}"`);
        setDeleteOpen(false);
      },
      onError: () => toast.error("Failed to delete connector"),
    });
  }

  return (
    <>
      <div className="rounded-xl border border-[hsl(195_15%_92%)] p-4 hover:border-[hsl(217_91%_60%/0.25)] hover:shadow-[0_2px_8px_hsl(200_15%_15%/0.04)] transition-[border-color,box-shadow]">
        <div className="flex items-start justify-between">
          <div className="flex items-start gap-3">
            <div className="text-xl mt-0.5">
              {CONNECTOR_ICONS[connector.connectorType] ?? "🔌"}
            </div>
            <div className="space-y-0.5">
              <h3 className="text-sm font-medium text-[hsl(200_20%_10%)]">
                {connector.name}
              </h3>
              <p className="text-xs text-[hsl(200_10%_60%)]">
                {CONNECTOR_TYPE_LABELS[connector.connectorType]}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="inline-flex items-center gap-1.5 text-xs">
              <span className={`w-1.5 h-1.5 rounded-full ${statusInfo.dot}`} />
              <span className={`font-medium ${statusInfo.text}`}>
                {statusInfo.label}
              </span>
            </span>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button className="flex items-center justify-center w-7 h-7 rounded-md text-[hsl(200_10%_60%)] hover:bg-[hsl(217_91%_60%/0.04)] transition-colors">
                  <MoreVertical className="h-3.5 w-3.5" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => onEdit(connector)}>
                  <Pencil className="h-3.5 w-3.5 mr-2" />
                  Edit
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={handleTest}
                  disabled={testMutation.isPending}
                >
                  <PlugZap className="h-3.5 w-3.5 mr-2" />
                  Test Connection
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={() => setDeleteOpen(true)}
                  className="text-[hsl(0_65%_50%)] focus:text-[hsl(0_65%_50%)]"
                >
                  <Trash2 className="h-3.5 w-3.5 mr-2" />
                  Delete
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>

        {connector.lastSyncStatus && (
          <p
            className={cn(
              "text-xs mt-3",
              connector.status === "ERROR"
                ? "text-[hsl(0_65%_50%)]"
                : "text-[hsl(200_10%_46%)]",
            )}
          >
            {connector.lastSyncStatus}
          </p>
        )}

        <div className="mt-3">
          <Button
            variant="outline"
            size="sm"
            className="h-7 text-xs gap-1.5 border-[hsl(195_15%_90%)] text-[hsl(200_10%_40%)]"
            onClick={handleTest}
            disabled={testMutation.isPending}
          >
            {testMutation.isPending ? (
              <Loader2 className="h-3 w-3 animate-spin" />
            ) : (
              <Plug className="h-3 w-3" />
            )}
            Test Connection
          </Button>
        </div>
      </div>

      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Connector</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to delete "{connector.name}"? This action
              cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              className="bg-[hsl(0_65%_50%)] text-white hover:bg-[hsl(0_65%_44%)]"
            >
              {deleteMutation.isPending ? (
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
              ) : null}
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
