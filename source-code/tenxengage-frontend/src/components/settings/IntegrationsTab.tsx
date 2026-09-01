import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Plus, Loader2, Plug } from "lucide-react";
import { useConnectors } from "@/hooks/useConnectorApi";
import { ConnectorCard } from "./ConnectorCard";
import { ConnectorDialog } from "./ConnectorDialog";
import type { ConnectorResponse } from "@/types/connector.types";

export function IntegrationsTab() {
  const { data: connectors, isLoading } = useConnectors();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editConnector, setEditConnector] = useState<ConnectorResponse | null>(
    null,
  );

  function handleEdit(connector: ConnectorResponse) {
    setEditConnector(connector);
    setDialogOpen(true);
  }

  function handleDialogChange(open: boolean) {
    setDialogOpen(open);
    if (!open) setEditConnector(null);
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-5 w-5 animate-spin text-[hsl(200_10%_60%)]" />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <span className="text-xs font-semibold text-[hsl(200_10%_60%)] tracking-[0.06em] uppercase">
            Connected Data Sources
          </span>
          <p className="text-sm text-[hsl(200_10%_46%)] mt-0.5">
            Connect your CRM, data warehouse, or other platforms to sync data.
          </p>
        </div>
        <Button
          onClick={() => setDialogOpen(true)}
          size="sm"
          className="h-8 text-sm gap-1.5 bg-[hsl(217_91%_60%)] hover:bg-[hsl(217_91%_52%)]"
        >
          <Plus className="h-3.5 w-3.5" />
          Add Connector
        </Button>
      </div>

      {connectors && connectors.length > 0 ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {connectors.map((connector) => (
            <ConnectorCard
              key={connector.id}
              connector={connector}
              onEdit={handleEdit}
            />
          ))}
        </div>
      ) : (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-[hsl(195_15%_88%)] py-12">
          <Plug className="h-8 w-8 text-[hsl(200_10%_80%)] mb-3" />
          <p className="text-sm font-medium text-[hsl(200_20%_10%)]">
            No connectors configured
          </p>
          <p className="text-sm text-[hsl(200_10%_46%)] mt-1">
            Add a connector to start syncing data from external sources.
          </p>
          <Button
            variant="outline"
            size="sm"
            className="mt-4 h-8 text-sm gap-1.5 border-[hsl(195_15%_90%)]"
            onClick={() => setDialogOpen(true)}
          >
            <Plus className="h-3.5 w-3.5" />
            Add Connector
          </Button>
        </div>
      )}

      <ConnectorDialog
        open={dialogOpen}
        onOpenChange={handleDialogChange}
        connector={editConnector}
      />
    </div>
  );
}
