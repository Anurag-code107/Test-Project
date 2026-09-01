import { useEffect, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import type {
  ConnectorResponse,
  ConnectorType,
  CreateConnectorRequest,
} from "@/types/connector.types";
import {
  CONNECTOR_CONFIG_FIELDS,
  CONNECTOR_TYPE_LABELS,
  CONNECTOR_AUTH_TYPES,
} from "@/types/connector.types";
import {
  useCreateConnector,
  useUpdateConnector,
} from "@/hooks/useConnectorApi";

interface ConnectorDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  connector?: ConnectorResponse | null;
}

const CONNECTOR_TYPES: ConnectorType[] = [
  "SALESFORCE",
  "MICROSOFT_DYNAMICS_365",
  "SNOWFLAKE",
  "HUBSPOT",
];

export function ConnectorDialog({
  open,
  onOpenChange,
  connector,
}: ConnectorDialogProps) {
  const isEdit = !!connector;
  const createMutation = useCreateConnector();
  const updateMutation = useUpdateConnector();
  const isPending = createMutation.isPending || updateMutation.isPending;

  const [connectorType, setConnectorType] =
    useState<ConnectorType>("SALESFORCE");
  const [name, setName] = useState("");
  const [authType, setAuthType] = useState("");
  const [config, setConfig] = useState<Record<string, string>>({});

  useEffect(() => {
    if (open) {
      if (connector) {
        setConnectorType(connector.connectorType);
        setName(connector.name);
        setAuthType(connector.authType ?? "");
        // Pre-fill config from configSummary (values are masked, user replaces them)
        setConfig(
          Object.fromEntries(
            Object.entries(connector.configSummary).map(([k, v]) => [
              k,
              v.includes("***") ? "" : v,
            ]),
          ),
        );
      } else {
        setConnectorType("SALESFORCE");
        setName("");
        setAuthType("");
        setConfig({});
      }
    }
  }, [open, connector]);

  // Update authType default when connector type changes
  useEffect(() => {
    const authTypes = CONNECTOR_AUTH_TYPES[connectorType];
    if (authTypes.length === 1) {
      setAuthType(authTypes[0]!.value);
    } else if (!isEdit) {
      setAuthType(authTypes[0]?.value ?? "");
    }
  }, [connectorType, isEdit]);

  const fields = CONNECTOR_CONFIG_FIELDS[connectorType];
  const authTypes = CONNECTOR_AUTH_TYPES[connectorType];

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (!name.trim()) {
      toast.error("Name is required");
      return;
    }

    // Validate required fields
    const missingFields = fields
      .filter((f) => f.required && !config[f.key]?.trim())
      .map((f) => f.label);

    if (!isEdit && missingFields.length > 0) {
      toast.error(`Missing required fields: ${missingFields.join(", ")}`);
      return;
    }

    if (isEdit && connector) {
      // Only send config if user filled in new values
      const hasNewConfig = Object.values(config).some((v) => v.trim() !== "");
      updateMutation.mutate(
        {
          id: connector.id,
          data: {
            name: name.trim(),
            ...(hasNewConfig ? { config } : {}),
            authType: authType || undefined,
          },
        },
        {
          onSuccess: () => {
            toast.success(`Updated connector "${name}"`);
            onOpenChange(false);
          },
          onError: () => toast.error("Failed to update connector"),
        },
      );
    } else {
      const request: CreateConnectorRequest = {
        connectorType,
        name: name.trim(),
        config,
        authType: authType || undefined,
      };
      createMutation.mutate(request, {
        onSuccess: () => {
          toast.success(`Created connector "${name}"`);
          onOpenChange(false);
        },
        onError: () => toast.error("Failed to create connector"),
      });
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {isEdit ? "Edit Connector" : "Add Connector"}
          </DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          {!isEdit && (
            <div className="space-y-2">
              <Label>Connector Type</Label>
              <Select
                value={connectorType}
                onValueChange={(v) => {
                  setConnectorType(v as ConnectorType);
                  setConfig({});
                }}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CONNECTOR_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>
                      {CONNECTOR_TYPE_LABELS[type]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="connector-name">Name</Label>
            <Input
              id="connector-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="My Salesforce Connection"
            />
          </div>

          {authTypes.length > 1 && (
            <div className="space-y-2">
              <Label>Authentication Type</Label>
              <Select value={authType} onValueChange={setAuthType}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {authTypes.map((at) => (
                    <SelectItem key={at.value} value={at.value}>
                      {at.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}

          <div className="space-y-3">
            <Label className="text-sm font-medium">Configuration</Label>
            {fields.map((field) => (
              <div key={field.key} className="space-y-1">
                <Label
                  htmlFor={`config-${field.key}`}
                  className="text-xs text-muted-foreground"
                >
                  {field.label}{" "}
                  {field.required && !isEdit && (
                    <span className="text-destructive">*</span>
                  )}
                </Label>
                <Input
                  id={`config-${field.key}`}
                  type={field.type}
                  value={config[field.key] ?? ""}
                  onChange={(e) =>
                    setConfig({ ...config, [field.key]: e.target.value })
                  }
                  placeholder={
                    isEdit
                      ? `Leave blank to keep existing ${field.label.toLowerCase()}`
                      : (field.placeholder ?? "")
                  }
                />
              </div>
            ))}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
              {isEdit ? "Update" : "Create"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
