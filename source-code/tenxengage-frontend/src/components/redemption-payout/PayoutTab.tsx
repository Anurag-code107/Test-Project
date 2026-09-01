import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { toast } from "sonner";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { CheckCircle2, Clock, AlertTriangle, Loader2, Landmark } from "lucide-react";
import {
  useRedemptionProfile,
  useLinkedBanks,
} from "@/hooks/redemption-payout/useRedemptionProfile";
import {
  useRemoveBankAccount,
  xtrmErrorCode,
  friendlyXtrmError,
} from "@/hooks/redemption-payout/useRedemptionProfileMutations";
import { ProfileAddressSection } from "./ProfileAddressSection";
import { LinkBankForm } from "./LinkBankForm";

// Enrollment is a single, shared prerequisite for BOTH payout rails (gift card + bank transfer), so the
// copy names both to avoid the "is this for gift cards or bank transfers?" ambiguity.
const STATUS_UI = {
  ENROLLED: {
    label: "Ready",
    icon: CheckCircle2,
    description: "Your account is enrolled. Gift cards will be sent to your email address, and bank transfers will go to a linked bank account that you choose when you redeem.",
  },
  NOT_ENROLLED: {
    label: "Pending",
    icon: Clock,
    description: "Complete your Payout profile to enroll. This enables both gift-card and bank-transfer payouts — bank transfers also need a linked bank account.",
  },
  FAILED: {
    label: "Action needed",
    icon: AlertTriangle,
    description: "Enrollment didn't complete, so gift-card and bank-transfer payouts are paused. Re-save your payout address to retry.",
  },
} as const;

type PayoutSection = "profile" | "banks";

/**
 * Payout profile tab. Split into two sub-tabs under the shared enrollment status:
 *   - Payout profile: the payee address used for XTRM enrollment.
 *   - Bank accounts:  link/remove the banks a bank-transfer redemption can pay.
 * The active sub-tab is URL-driven (?section=profile|banks) so the store's empty states can deep-link
 * gift-card users to Payout profile and bank-transfer users to Bank accounts.
 *
 * No default-bank selection here: the redeemer picks which bank to use at transfer time, so a stored
 * default would be redundant (the server still auto-manages one as a fallback).
 */
export function PayoutTab() {
  const { data: profile, isLoading, isError } = useRedemptionProfile();
  const { data: banks = [], isLoading: banksLoading } = useLinkedBanks();
  const removeBank = useRemoveBankAccount();
  const [showLinkForm, setShowLinkForm] = useState(false);

  const [searchParams, setSearchParams] = useSearchParams();
  const section: PayoutSection = searchParams.get("section") === "banks" ? "banks" : "profile";
  const setSection = (value: string) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set("section", value);
        return next;
      },
      { replace: true },
    );
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12 text-muted-foreground">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading payout profile…
      </div>
    );
  }

  if (isError || !profile) {
    return (
      <Alert variant="destructive">
        <AlertTriangle className="h-4 w-4" />
        <AlertTitle>Couldn't load your payout profile</AlertTitle>
        <AlertDescription>Please refresh the page or try again later.</AlertDescription>
      </Alert>
    );
  }

  const status = STATUS_UI[profile.enrollmentStatus];
  const StatusIcon = status.icon;

  const handleRemoveBank = (bankId: string) => {
    removeBank.mutate(bankId, {
      onSuccess: () => toast.success("Bank account removed."),
      onError: (error) => toast.error(friendlyXtrmError(xtrmErrorCode(error))),
    });
  };

  return (
    <div className="space-y-6">
      {/* Enrollment status — the shared prerequisite for both payout rails. */}
      <Alert>
        <StatusIcon className="h-4 w-4" />
        <AlertTitle>Payout status: {status.label}</AlertTitle>
        <AlertDescription>{status.description}</AlertDescription>
      </Alert>

      <Tabs value={section} onValueChange={setSection}>
        <TabsList>
          <TabsTrigger value="profile">Payout profile</TabsTrigger>
          <TabsTrigger value="banks">Bank accounts</TabsTrigger>
        </TabsList>

        <TabsContent value="profile" className="mt-4">
          <Card>
            <CardContent className="pt-6">
              <ProfileAddressSection profile={profile} />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="banks" className="mt-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Bank accounts</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {banksLoading ? (
                <div className="flex items-center text-sm text-muted-foreground">
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" /> Loading bank accounts…
                </div>
              ) : banks.length > 0 ? (
                <ul className="space-y-2">
                  {banks.map((bank) => (
                    <li key={bank.id} className="flex items-center justify-between rounded-md border p-3">
                      <div className="flex items-center gap-3">
                        <Landmark className="h-4 w-4 text-muted-foreground" />
                        <span className="text-sm font-medium">{bank.label}</span>
                      </div>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleRemoveBank(bank.id)}
                        disabled={removeBank.isPending && removeBank.variables === bank.id}
                      >
                        {removeBank.isPending && removeBank.variables === bank.id && (
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        )}
                        Remove
                      </Button>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-sm text-muted-foreground">No bank accounts linked yet.</p>
              )}

              {showLinkForm ? (
                <LinkBankForm onLinked={() => setShowLinkForm(false)} onCancel={() => setShowLinkForm(false)} />
              ) : (
                <Button variant="outline" size="sm" onClick={() => setShowLinkForm(true)}>
                  {banks.length > 0 ? "Add another bank account" : "Link a bank account"}
                </Button>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
