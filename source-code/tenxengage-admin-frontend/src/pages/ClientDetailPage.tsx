import { useState, useEffect, useMemo } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { ArrowLeft, Save } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

import {
  useClient,
  useClientOverrides,
  useSetClientOverrides,
} from "@/hooks/useClientApi";
import { useFeatureFlags } from "@/hooks/useFeatureFlagApi";
import { formatDate } from "@/utils/formatters";

import type {
  ClientStatus,
  SubscriptionTier,
  FeatureFlagResponse,
  SetFeatureOverrideRequest,
} from "@/types/client.types";

// -- Badge helpers --

const STATUS_BADGE_CLASSES: Record<ClientStatus, string> = {
  ACTIVE: "bg-green-100 text-green-800 hover:bg-green-100",
  INACTIVE: "bg-gray-100 text-gray-800 hover:bg-gray-100",
  SUSPENDED: "bg-red-100 text-red-800 hover:bg-red-100",
  TRIAL: "bg-amber-100 text-amber-800 hover:bg-amber-100",
};

const TIER_BADGE_CLASSES: Record<SubscriptionTier, string> = {
  STARTER: "bg-slate-100 text-slate-800 hover:bg-slate-100",
  PROFESSIONAL: "bg-blue-100 text-blue-800 hover:bg-blue-100",
  ENTERPRISE: "bg-purple-100 text-purple-800 hover:bg-purple-100",
};

// -- Utility --

type OverrideValue = "TIER_DEFAULT" | "FORCE_ENABLE" | "FORCE_DISABLE";

function toTitleCase(str: string): string {
  return str
    .replace(/_/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

function getTierDefault(
  flag: FeatureFlagResponse,
  tier: SubscriptionTier,
): boolean {
  switch (tier) {
    case "STARTER":
      return flag.starterEnabled;
    case "PROFESSIONAL":
      return flag.professionalEnabled;
    case "ENTERPRISE":
      return flag.enterpriseEnabled;
  }
}

// -- Component --

export default function ClientDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const { data: client, isLoading: clientLoading } = useClient(id);
  const { data: featureFlags, isLoading: flagsLoading } = useFeatureFlags();
  const { data: clientOverrides, isLoading: overridesLoading } =
    useClientOverrides(id);
  const setOverrides = useSetClientOverrides();

  // Map of featureFlagId -> OverrideValue for local state
  const [overrideMap, setOverrideMap] = useState<
    Record<string, OverrideValue>
  >({});

  // Sync server overrides into local state whenever they arrive
  const serverOverrideMap = useMemo(() => {
    const map: Record<string, OverrideValue> = {};
    if (clientOverrides) {
      for (const o of clientOverrides) {
        map[o.featureFlagId] = o.enabled ? "FORCE_ENABLE" : "FORCE_DISABLE";
      }
    }
    return map;
  }, [clientOverrides]);

  useEffect(() => {
    setOverrideMap(serverOverrideMap);
  }, [serverOverrideMap]);

  // Handlers

  function handleOverrideChange(flagId: string, value: OverrideValue) {
    setOverrideMap((prev) => ({ ...prev, [flagId]: value }));
  }

  async function handleSaveOverrides() {
    if (!id || !featureFlags) return;

    const overrides: SetFeatureOverrideRequest[] = [];

    for (const [flagId, value] of Object.entries(overrideMap)) {
      if (value === "FORCE_ENABLE") {
        overrides.push({ featureFlagId: flagId, enabled: true });
      } else if (value === "FORCE_DISABLE") {
        overrides.push({ featureFlagId: flagId, enabled: false });
      }
      // TIER_DEFAULT means no override -- we don't send it
    }

    try {
      await setOverrides.mutateAsync({ clientId: id, overrides });
      toast.success("Feature overrides saved successfully");
    } catch {
      toast.error("Failed to save feature overrides");
    }
  }

  // Loading state

  if (clientLoading || flagsLoading || overridesLoading) {
    return (
      <div className="flex items-center justify-center py-20 text-muted-foreground">
        Loading client details...
      </div>
    );
  }

  if (!client) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-20">
        <p className="text-muted-foreground">Client not found</p>
        <Button
          variant="outline"
          onClick={() => navigate("/clients")}
        >
          Back to Clients
        </Button>
      </div>
    );
  }

  // Derive data

  const flags = featureFlags ?? [];
  const hasChanges =
    JSON.stringify(overrideMap) !== JSON.stringify(serverOverrideMap);

  // Render

  return (
    <div className="flex flex-col gap-6 px-6 py-6">
      {/* Back Button */}
      <Button
        variant="ghost"
        className="w-fit gap-2"
        onClick={() => navigate("/clients")}
      >
        <ArrowLeft className="h-4 w-4" />
        Back to Clients
      </Button>

      {/* Client Info Card */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div className="flex flex-col gap-2">
              <h2 className="text-2xl font-semibold">{client.name}</h2>
              <p className="text-sm text-muted-foreground">
                {client.subdomain}
              </p>
              <div className="flex items-center gap-2">
                <Badge
                  variant="secondary"
                  className={STATUS_BADGE_CLASSES[client.status]}
                >
                  {client.status}
                </Badge>
                <Badge
                  variant="secondary"
                  className={TIER_BADGE_CLASSES[client.subscriptionTier]}
                >
                  {client.subscriptionTier}
                </Badge>
              </div>
            </div>
            <p className="text-sm text-muted-foreground">
              Created {formatDate(client.createdAt)}
            </p>
          </div>
        </CardContent>
      </Card>

      {/* Feature Overrides Card */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Feature Access</CardTitle>
              <CardDescription>
                Manage which features are available for this client beyond their
                tier defaults
              </CardDescription>
            </div>
            <Button
              onClick={handleSaveOverrides}
              disabled={setOverrides.isPending || !hasChanges}
            >
              <Save className="mr-2 h-4 w-4" />
              {setOverrides.isPending ? "Saving..." : "Save"}
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          <div className="rounded-lg border">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-muted/50">
                    <th className="px-4 py-3 text-left font-medium text-muted-foreground">
                      Feature Name
                    </th>
                    <th className="px-4 py-3 text-left font-medium text-muted-foreground">
                      Tier Default
                    </th>
                    <th className="px-4 py-3 text-left font-medium text-muted-foreground">
                      Override
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {flags.length === 0 ? (
                    <tr>
                      <td
                        colSpan={3}
                        className="px-4 py-8 text-center text-muted-foreground"
                      >
                        No feature flags configured
                      </td>
                    </tr>
                  ) : (
                    flags.map((flag) => {
                      const tierDefault = getTierDefault(
                        flag,
                        client.subscriptionTier,
                      );
                      const currentValue =
                        overrideMap[flag.id] ?? "TIER_DEFAULT";

                      return (
                        <tr
                          key={flag.id}
                          className="border-b last:border-b-0 hover:bg-muted/30 transition-colors"
                        >
                          <td className="px-4 py-3 font-medium">
                            {toTitleCase(flag.featureKey)}
                          </td>
                          <td className="px-4 py-3">
                            {tierDefault ? (
                              <Badge
                                variant="secondary"
                                className="bg-green-100 text-green-800 hover:bg-green-100"
                              >
                                Enabled
                              </Badge>
                            ) : (
                              <Badge
                                variant="secondary"
                                className="bg-gray-100 text-gray-800 hover:bg-gray-100"
                              >
                                Disabled
                              </Badge>
                            )}
                          </td>
                          <td className="px-4 py-3">
                            <Select
                              value={currentValue}
                              onValueChange={(v) =>
                                handleOverrideChange(
                                  flag.id,
                                  v as OverrideValue,
                                )
                              }
                            >
                              <SelectTrigger className="w-[180px]">
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value="TIER_DEFAULT">
                                  Tier Default
                                </SelectItem>
                                <SelectItem value="FORCE_ENABLE">
                                  Force Enable
                                </SelectItem>
                                <SelectItem value="FORCE_DISABLE">
                                  Force Disable
                                </SelectItem>
                              </SelectContent>
                            </Select>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
