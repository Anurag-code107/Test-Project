import { useState } from "react";
import { Plus, Loader2, Shield, Sparkles } from "lucide-react";
import { toast } from "sonner";
import { PageBanner } from "@/components/PageBanner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  useFeatureFlags,
  useCreateFeatureFlag,
  useUpdateFeatureFlag,
} from "@/hooks/useFeatureFlagApi";
import type {
  FeatureFlagResponse,
  CreateFeatureFlagRequest,
} from "@/types/client.types";

/* ─── Helpers ──────────────────────────────────────────────────────────────── */

function formatFeatureKey(key: string): string {
  return key.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

const FEATURE_KEY_PATTERN = /^[a-z][a-z0-9_]*$/;

type TierKey = "starterEnabled" | "professionalEnabled" | "enterpriseEnabled";

interface TierConfig {
  key: TierKey;
  label: string;
  accent: string;
  badgeVariant: "secondary" | "default" | "outline";
  progressBg: string;
  progressFill: string;
  icon: typeof Shield;
}

const TIERS: TierConfig[] = [
  {
    key: "starterEnabled",
    label: "Starter",
    accent: "border-slate-200 bg-slate-50/50",
    badgeVariant: "secondary",
    progressBg: "bg-slate-100",
    progressFill: "bg-slate-500",
    icon: Shield,
  },
  {
    key: "professionalEnabled",
    label: "Professional",
    accent: "border-blue-200 bg-blue-50/50",
    badgeVariant: "default",
    progressBg: "bg-blue-100",
    progressFill: "bg-blue-500",
    icon: Sparkles,
  },
  {
    key: "enterpriseEnabled",
    label: "Enterprise",
    accent: "border-purple-200 bg-purple-50/50",
    badgeVariant: "outline",
    progressBg: "bg-purple-100",
    progressFill: "bg-purple-500",
    icon: Shield,
  },
];

/* ─── Component ────────────────────────────────────────────────────────────── */

export default function ManageSubscriptionsPage() {
  const { data: featureFlags, isLoading, isError } = useFeatureFlags();
  const createMutation = useCreateFeatureFlag();
  const updateMutation = useUpdateFeatureFlag();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [newFeatureKey, setNewFeatureKey] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [newStarterEnabled, setNewStarterEnabled] = useState(false);
  const [newProfessionalEnabled, setNewProfessionalEnabled] = useState(false);
  const [newEnterpriseEnabled, setNewEnterpriseEnabled] = useState(false);

  // Track which flag+tier is currently being updated for loading indicators
  const [updatingCell, setUpdatingCell] = useState<string | null>(null);

  /* ─── Toggle handler ───────────────────────────────────────────────────── */

  async function handleToggle(flag: FeatureFlagResponse, tierKey: TierKey) {
    const cellId = `${flag.id}-${tierKey}`;
    setUpdatingCell(cellId);
    try {
      await updateMutation.mutateAsync({
        id: flag.id,
        data: { [tierKey]: !flag[tierKey] },
      });
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to update feature flag";
      toast.error(message);
    } finally {
      setUpdatingCell(null);
    }
  }

  /* ─── Create handler ───────────────────────────────────────────────────── */

  function resetForm() {
    setNewFeatureKey("");
    setNewDescription("");
    setNewStarterEnabled(false);
    setNewProfessionalEnabled(false);
    setNewEnterpriseEnabled(false);
  }

  async function handleCreate() {
    const trimmedKey = newFeatureKey.trim();
    if (!trimmedKey) {
      toast.error("Feature key is required");
      return;
    }
    if (!FEATURE_KEY_PATTERN.test(trimmedKey)) {
      toast.error(
        "Feature key must start with a lowercase letter and contain only lowercase letters, numbers, and underscores",
      );
      return;
    }

    const payload: CreateFeatureFlagRequest = {
      featureKey: trimmedKey,
      description: newDescription.trim() || undefined,
      starterEnabled: newStarterEnabled,
      professionalEnabled: newProfessionalEnabled,
      enterpriseEnabled: newEnterpriseEnabled,
    };

    try {
      await createMutation.mutateAsync(payload);
      toast.success(`Created feature "${formatFeatureKey(trimmedKey)}"`);
      resetForm();
      setDialogOpen(false);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to create feature flag";
      toast.error(message);
    }
  }

  /* ─── Tier summary calculations ────────────────────────────────────────── */

  function getTierCount(tierKey: TierKey): number {
    if (!featureFlags) return 0;
    return featureFlags.filter((f) => f[tierKey]).length;
  }

  const totalFeatures = featureFlags?.length ?? 0;

  /* ─── Loading / Error states ───────────────────────────────────────────── */

  if (isLoading) {
    return (
      <div className="flex flex-col h-full">
        <div className="shrink-0 mb-6">
          <PageBanner
            theme="default"
            title="Subscription Management"
            subtitle="Configure features available at each subscription tier"
          />
        </div>
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
          <span className="ml-2 text-sm text-muted-foreground">
            Loading feature flags...
          </span>
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col h-full">
        <div className="shrink-0 mb-6">
          <PageBanner
            theme="default"
            title="Subscription Management"
            subtitle="Configure features available at each subscription tier"
          />
        </div>
        <p className="text-sm text-destructive py-4 px-6">
          Failed to load feature flags. Please try again later.
        </p>
      </div>
    );
  }

  const isCreateFormValid =
    newFeatureKey.trim().length > 0 &&
    FEATURE_KEY_PATTERN.test(newFeatureKey.trim());

  /* ─── Render ─────────────────────────────────────────────────────────── */

  return (
    <div className="flex flex-col h-full">
      {/* Banner */}
      <div className="shrink-0 mb-6">
        <PageBanner
          theme="default"
          title="Subscription Management"
          subtitle="Configure features available at each subscription tier"
          actions={
            <Button
              onClick={() => setDialogOpen(true)}
              variant="outline"
              className="h-8 text-xs gap-1.5 border-[hsl(195_15%_90%)] text-[hsl(200_10%_40%)] hover:border-[hsl(217_91%_60%/0.3)]"
            >
              <Plus className="h-3.5 w-3.5" />
              Add Feature
            </Button>
          }
        />
      </div>

      {/* Feature Matrix Table */}
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-[200px]">Feature</TableHead>
                <TableHead className="w-[300px]">Description</TableHead>
                {TIERS.map((tier) => (
                  <TableHead key={tier.key} className="w-[120px] text-center">
                    <div className="flex items-center justify-center gap-1.5">
                      <tier.icon className="h-3.5 w-3.5" />
                      {tier.label}
                    </div>
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {featureFlags && featureFlags.length > 0 ? (
                featureFlags.map((flag) => (
                  <TableRow key={flag.id}>
                    <TableCell className="font-medium">
                      <div className="flex items-center gap-2">
                        <span className="text-sm">
                          {formatFeatureKey(flag.featureKey)}
                        </span>
                        <Badge
                          variant="outline"
                          className="text-[10px] font-mono px-1.5 py-0"
                        >
                          {flag.featureKey}
                        </Badge>
                      </div>
                    </TableCell>
                    <TableCell>
                      <span className="text-sm text-muted-foreground max-w-[280px] block truncate">
                        {flag.description || "No description"}
                      </span>
                    </TableCell>
                    {TIERS.map((tier) => {
                      const cellId = `${flag.id}-${tier.key}`;
                      const isUpdating = updatingCell === cellId;
                      return (
                        <TableCell key={tier.key} className="text-center">
                          <div className="flex items-center justify-center gap-1.5">
                            {isUpdating ? (
                              <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
                            ) : (
                              <Switch
                                checked={flag[tier.key]}
                                onCheckedChange={() =>
                                  handleToggle(flag, tier.key)
                                }
                                aria-label={`${formatFeatureKey(flag.featureKey)} - ${tier.label}`}
                              />
                            )}
                          </div>
                        </TableCell>
                      );
                    })}
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell
                    colSpan={5}
                    className="h-24 text-center text-muted-foreground"
                  >
                    No feature flags configured. Click "Add Feature" to create
                    one.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Tier Summary Cards */}
      {totalFeatures > 0 && (
        <div className="grid grid-cols-3 gap-4 mt-6">
          {TIERS.map((tier) => {
            const enabledCount = getTierCount(tier.key);
            const percentage =
              totalFeatures > 0 ? (enabledCount / totalFeatures) * 100 : 0;

            return (
              <Card key={tier.key} className={`${tier.accent}`}>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm font-medium flex items-center gap-2">
                    <tier.icon className="h-4 w-4" />
                    {tier.label}
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-sm text-muted-foreground mb-3">
                    {enabledCount} of {totalFeatures} features enabled
                  </p>
                  <div className={`h-2 rounded-full ${tier.progressBg}`}>
                    <div
                      className={`h-2 rounded-full transition-all duration-300 ${tier.progressFill}`}
                      style={{ width: `${percentage}%` }}
                    />
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      {/* Add Feature Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add Feature Flag</DialogTitle>
          </DialogHeader>

          <div className="space-y-4 py-2">
            {/* Feature Key */}
            <div className="space-y-1.5">
              <Label htmlFor="feature-key" className="text-sm">
                Feature Key <span className="text-destructive">*</span>
              </Label>
              <Input
                id="feature-key"
                value={newFeatureKey}
                onChange={(e) => setNewFeatureKey(e.target.value)}
                placeholder="e.g. custom_reports"
                pattern="^[a-z][a-z0-9_]*$"
                className="font-mono text-sm"
              />
              <p className="text-[11px] text-muted-foreground">
                Lowercase letters, numbers, and underscores only. Must start
                with a letter.
              </p>
            </div>

            {/* Description */}
            <div className="space-y-1.5">
              <Label htmlFor="feature-description" className="text-sm">
                Description
              </Label>
              <Input
                id="feature-description"
                value={newDescription}
                onChange={(e) => setNewDescription(e.target.value)}
                placeholder="Brief description of the feature"
                className="text-sm"
              />
            </div>

            {/* Tier Availability */}
            <div className="space-y-3">
              <Label className="text-sm">Tier Availability</Label>
              <div className="space-y-2.5">
                <div className="flex items-center justify-between rounded-lg border p-3">
                  <div className="flex items-center gap-2">
                    <Shield className="h-4 w-4 text-slate-500" />
                    <span className="text-sm">Starter</span>
                  </div>
                  <Switch
                    checked={newStarterEnabled}
                    onCheckedChange={setNewStarterEnabled}
                    aria-label="Starter enabled"
                  />
                </div>
                <div className="flex items-center justify-between rounded-lg border p-3">
                  <div className="flex items-center gap-2">
                    <Sparkles className="h-4 w-4 text-blue-500" />
                    <span className="text-sm">Professional</span>
                  </div>
                  <Switch
                    checked={newProfessionalEnabled}
                    onCheckedChange={setNewProfessionalEnabled}
                    aria-label="Professional enabled"
                  />
                </div>
                <div className="flex items-center justify-between rounded-lg border p-3">
                  <div className="flex items-center gap-2">
                    <Shield className="h-4 w-4 text-purple-500" />
                    <span className="text-sm">Enterprise</span>
                  </div>
                  <Switch
                    checked={newEnterpriseEnabled}
                    onCheckedChange={setNewEnterpriseEnabled}
                    aria-label="Enterprise enabled"
                  />
                </div>
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                resetForm();
                setDialogOpen(false);
              }}
              className="text-sm"
            >
              Cancel
            </Button>
            <Button
              onClick={handleCreate}
              disabled={!isCreateFormValid || createMutation.isPending}
              className="text-sm gap-1.5"
            >
              {createMutation.isPending && (
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
              )}
              Create Feature
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
