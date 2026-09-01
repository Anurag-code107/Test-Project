import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Download, Trash2, Loader2, ShieldCheck } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import {
  exportMyData,
  getMyConsent,
  updateMyConsent,
  requestAccountDeletion,
} from "@/services/privacy.service";
import { formatDateTime } from "@/utils/formatters";

// ─── Consent type labels ────────────────────────────────────────────────────

const CONSENT_LABELS: Record<string, { label: string; description: string }> = {
  CONSENT_AI: {
    label: "AI Recommendations",
    description: "Allow AI-powered deal and product recommendations",
  },
  CONSENT_MARKETING: {
    label: "Marketing Email",
    description: "Receive marketing communications and program updates",
  },
  CONSENT_ANALYTICS: {
    label: "Analytics",
    description: "Allow usage analytics to improve your experience",
  },
};

// ─── Component ──────────────────────────────────────────────────────────────

export function ProfilePrivacySection() {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [deletionRequested, setDeletionRequested] = useState(false);

  // ── Data export ─────────────────────────────────────────────────────────

  const exportMutation = useMutation({
    mutationFn: exportMyData,
    onSuccess: (data) => {
      const blob = new Blob([JSON.stringify(data, null, 2)], {
        type: "application/json",
      });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `my-data-export-${new Date().toISOString().slice(0, 10)}.json`;
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      URL.revokeObjectURL(url);

      toast({
        title: "Export complete",
        description: "Your data has been downloaded.",
      });
    },
    onError: () => {
      toast({
        title: "Export failed",
        description: "Could not export your data. Please try again.",
        variant: "destructive",
      });
    },
  });

  // ── Account deletion ────────────────────────────────────────────────────

  const deletionMutation = useMutation({
    mutationFn: requestAccountDeletion,
    onSuccess: () => {
      setDeletionRequested(true);
      toast({
        title: "Request submitted",
        description:
          "Your account deletion request has been submitted. You will receive a confirmation email.",
      });
    },
    onError: () => {
      toast({
        title: "Request failed",
        description:
          "Could not submit your deletion request. Please try again.",
        variant: "destructive",
      });
    },
  });

  // ── Consent preferences ─────────────────────────────────────────────────

  const consentQuery = useQuery({
    queryKey: ["my-consent"],
    queryFn: getMyConsent,
    retry: false,
  });

  const consentMutation = useMutation({
    mutationFn: updateMyConsent,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["my-consent"] });
      toast({
        title: "Preference saved",
        description: "Your consent preference has been updated.",
      });
    },
    onError: () => {
      toast({
        title: "Update failed",
        description: "Could not update your preference. Please try again.",
        variant: "destructive",
      });
    },
  });

  const handleConsentToggle = (consentType: string, granted: boolean) => {
    consentMutation.mutate({ [consentType]: granted });
  };

  const consentPreferences = consentQuery.data ?? [];
  const hasVisibleConsent = consentPreferences.length > 0;

  // ── Render ──────────────────────────────────────────────────────────────

  return (
    <div className="space-y-6">
      {/* ── Consent Preferences (conditional) ───────────────────────────── */}
      {hasVisibleConsent && (
        <Card>
          <CardHeader>
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-primary/10">
                <ShieldCheck className="h-5 w-5 text-primary" />
              </div>
              <div>
                <CardTitle>Consent Preferences</CardTitle>
                <CardDescription>
                  Control how your data is used. Changes are saved
                  automatically.
                </CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent className="space-y-1">
            {consentPreferences.map((pref, index) => {
              const meta = CONSENT_LABELS[pref.consentType] ?? {
                label: pref.consentType,
                description: "",
              };

              return (
                <div key={pref.consentType}>
                  {index > 0 && <Separator className="my-4" />}
                  <div className="flex items-center justify-between gap-4">
                    <div className="space-y-0.5">
                      <p className="text-sm font-medium text-foreground">
                        {meta.label}
                      </p>
                      {meta.description && (
                        <p className="text-sm text-muted-foreground">
                          {meta.description}
                        </p>
                      )}
                      {pref.lastUpdated && (
                        <p className="text-xs text-muted-foreground">
                          Last updated: {formatDateTime(pref.lastUpdated)}
                        </p>
                      )}
                    </div>
                    <Switch
                      checked={pref.granted}
                      onCheckedChange={(checked) =>
                        handleConsentToggle(pref.consentType, checked)
                      }
                      disabled={consentMutation.isPending}
                    />
                  </div>
                </div>
              );
            })}
          </CardContent>
        </Card>
      )}

      {/* ── Privacy & Data Section ──────────────────────────────────────── */}
      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-primary/10">
              <ShieldCheck className="h-5 w-5 text-primary" />
            </div>
            <div>
              <CardTitle>Privacy & Data</CardTitle>
              <CardDescription>
                Manage your personal data and account
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Export */}
          <div className="flex items-start justify-between gap-4">
            <div className="space-y-1">
              <p className="text-sm font-medium text-foreground">
                Export My Data
              </p>
              <p className="text-sm text-muted-foreground">
                Export all personal data we hold about you as a JSON file.
              </p>
            </div>
            <Button
              variant="outline"
              onClick={() => exportMutation.mutate()}
              disabled={exportMutation.isPending}
            >
              {exportMutation.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Download className="mr-2 h-4 w-4" />
              )}
              Export
            </Button>
          </div>

          <Separator />

          {/* Deletion */}
          <div className="flex items-start justify-between gap-4">
            <div className="space-y-1">
              <p className="text-sm font-medium text-foreground">
                Request Account Deletion
              </p>
              <p className="text-sm text-muted-foreground">
                Request permanent deletion of your account and personal data.
                This action cannot be undone.
              </p>
            </div>

            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button
                  variant="destructive"
                  disabled={deletionMutation.isPending || deletionRequested}
                >
                  {deletionMutation.isPending ? (
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  ) : (
                    <Trash2 className="mr-2 h-4 w-4" />
                  )}
                  {deletionRequested ? "Request Submitted" : "Delete Account"}
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>
                    Are you sure you want to delete your account?
                  </AlertDialogTitle>
                  <AlertDialogDescription>
                    This will submit a request to permanently delete your
                    account and all associated personal data. This action cannot
                    be undone. You will receive a confirmation email before any
                    data is removed.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Cancel</AlertDialogCancel>
                  <AlertDialogAction
                    onClick={() => deletionMutation.mutate()}
                    className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                  >
                    Yes, delete my account
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
