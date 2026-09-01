import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2, Save } from "lucide-react";
import { XtrmAccountStatus } from "@/components/settings/XtrmAccountStatus";
import {
  useCompanyAdminProfile,
  useCompleteCompanyAdminProfile,
  isNotYourPayoutSetup,
} from "@/hooks/useCompanyAdminProfile";

const FIELDS = [
  { key: "adminCity", label: "City" },
  { key: "adminRegion", label: "State / Region" },
  { key: "adminPostalCode", label: "Postal Code" },
] as const;

type FieldKey = (typeof FIELDS)[number]["key"];

/**
 * A company admin finishing their own payout setup.
 *
 * Address only: name, email and mobile came from the login their client admin created. The email is shown
 * but never editable — it has already been spent at the payment provider, which will not reuse it.
 */
export default function CompanyPayoutSetupPage() {
  const { data: profile, isLoading, error } = useCompanyAdminProfile();
  const complete = useCompleteCompanyAdminProfile();
  const [values, setValues] = useState<Record<FieldKey, string>>({
    adminCity: "",
    adminRegion: "",
    adminPostalCode: "",
  });

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 p-6 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" />
        Loading your payout setup…
      </div>
    );
  }

  // Not a fault — this admin simply isn't the one the account belongs to. Saying so stops them hunting for
  // a broken page, and stops them asking an administrator to "fix" a working setup.
  if (isNotYourPayoutSetup(error)) {
    return (
      <Card>
        <CardContent className="pt-6">
          <p className="text-sm font-medium">
            Someone else manages your company&apos;s payout account
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            A company has one payout account, set up by the admin whose email it
            was opened with. You can still send rewards — you just can&apos;t
            change the account they&apos;re paid from.
          </p>
        </CardContent>
      </Card>
    );
  }

  // Say so rather than showing an empty form. Without this the page rendered nothing at all when the
  // request failed, which is indistinguishable from a broken tab.
  if (error || !profile) {
    return (
      <Card>
        <CardContent className="pt-6">
          <p className="text-sm font-medium">Payout setup is unavailable</p>
          <p className="mt-1 text-xs text-muted-foreground">
            We couldn&apos;t load your company&apos;s payout details. If this
            persists, your account may not be linked to a partner company yet.
          </p>
        </CardContent>
      </Card>
    );
  }

  const valueFor = (k: FieldKey) => values[k] || (profile?.[k] ?? "");
  const missing = FIELDS.filter((f) => !valueFor(f.key).trim());

  const onSubmit = async () => {
    if (missing.length > 0) {
      toast.error(`Still needed: ${missing.map((f) => f.label).join(", ")}`);
      return;
    }
    try {
      await complete.mutateAsync({
        adminCity: valueFor("adminCity"),
        adminRegion: valueFor("adminRegion"),
        adminPostalCode: valueFor("adminPostalCode"),
      });
      toast.success("Payout setup submitted");
    } catch {
      toast.error("Could not complete payout setup");
    }
  };

  return (
    <Card>
      <CardContent className="space-y-6 pt-6">
        <div className="space-y-1">
          <h3 className="text-sm font-medium">
            Payout setup for {profile?.companyName}
          </h3>
          <p className="text-xs text-muted-foreground">
            Signed in as {profile?.adminEmail}. Add your address to finish setting
            up your company&apos;s payout account.
          </p>
        </div>

        <div className="grid grid-cols-2 gap-3">
          {FIELDS.map((f) => (
            <div className="space-y-2" key={f.key}>
              <Label htmlFor={f.key}>{f.label}</Label>
              <Input
                id={f.key}
                value={valueFor(f.key)}
                disabled={complete.isPending}
                onChange={(e) =>
                  setValues((prev) => ({ ...prev, [f.key]: e.target.value }))
                }
              />
            </div>
          ))}
        </div>

        <Button
          onClick={onSubmit}
          disabled={complete.isPending}
          className="gap-2"
        >
          {complete.isPending ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Save className="h-4 w-4" />
          )}
          Finish setup
        </Button>

        <XtrmAccountStatus
          account={profile?.xtrmAccount}
          isConnecting={complete.isPending}
          onConnect={onSubmit}
          portalUrl={profile?.portalUrl}
        />
      </CardContent>
    </Card>
  );
}
