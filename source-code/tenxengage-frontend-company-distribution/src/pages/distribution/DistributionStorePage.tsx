import { useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { useAuth } from "@/hooks/useAuth";
import { useCompanyWallet } from "@/hooks/useWallet";
// Only the reason string is read here now: whether a rail is sendable is decided by `railSendBlocked`,
// which every rail goes through since the wallet rail was retired.
import { XTRM_RAIL_UNAVAILABLE_REASON } from "@/config/redemptionFeatures";
import {
  useCreateDistribution,
  useDistributableGiftCards,
  useDistributionRecipients,
} from "@/hooks/useCompanyDistribution";
import { useToast } from "@/hooks/use-toast";
import type { DistributionRail } from "@/types/company-distribution.types";
import { CompanyBalanceHeader } from "@/components/distribution/CompanyBalanceHeader";
import { GiftCardPicker } from "@/components/distribution/GiftCardPicker";
import { RecipientTable } from "@/components/distribution/RecipientTable";
import { ReviewStrip } from "@/components/distribution/ReviewStrip";
import { RAILS, DEFAULT_RAIL, railSendBlocked } from "./distributionRails";
import { useCompanyAdminProfile } from "@/hooks/useCompanyAdminProfile";

// Rails live in their own module so they can be reasoned about without this page's imports.

/**
 * Distribution Store — a partner admin spending the company wallet on their own sellers.
 *
 * <p>Separate from the personal store on purpose: the money comes from a different wallet, and showing
 * both on one page would put a personal and a company balance in the same view.</p>
 */
export default function DistributionStorePage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  // Until the company's payout account exists, every recipient reads ineligible with no clue why. Knowing
  // the status lets the page say where the fix is instead of just refusing everyone.
  const { data: payoutProfile } = useCompanyAdminProfile();
  // Prompt only on a profile we actually received. An absent one covers three cases that must not read as
  // "not set up yet": still loading, the request failed, and — the reason this is spelled out — a company
  // admin who is not the one the payout account belongs to, whom the server refuses. That admin can still
  // distribute, since the company account funds it; they just cannot set it up, so offering them "Finish
  // setup" would send them to a page that refuses them too.
  const payoutSetupNeeded =
    !!payoutProfile && payoutProfile.xtrmAccount?.status !== "CONNECTED";
  const { toast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  // Rail lives in the URL so a refresh or a shared link lands on the same one.
  const railParam = searchParams.get("rail") as DistributionRail | null;
  const rail: DistributionRail =
    railParam && RAILS.some((r) => r.value === railParam) ? railParam : DEFAULT_RAIL;

  // Holds the chosen provider SKU. The picker keys on it as its selection id.
  const [providerSku, setProviderSku] = useState<string | null>(null);
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const companyId = user?.partnerCompanyId ?? null;
  const { data: wallets, isLoading: walletLoading } = useCompanyWallet(companyId);
  const { data: recipients = [], isLoading: recipientsLoading } = useDistributionRecipients(rail);
  // The provider's full catalogue, not just the client's curated cards — a partner admin picks any SKU.
  const {
    data: catalog = [],
    isLoading: catalogLoading,
    isError: catalogError,
  } = useDistributableGiftCards(rail === "GIFT_CARD");
  const createDistribution = useCreateDistribution(companyId);

  const wallet = wallets?.[0] ?? null;
  const selectedCard = catalog.find((c) => c.id === providerSku) ?? null;

  // A FIXED denomination cannot be changed, so the field is pinned to the face value rather than
  // letting the admin type something the server will reject.
  const isFixed = selectedCard?.valueType === "FIXED";
  const effectiveAmount = isFixed ? (selectedCard?.minAmount ?? "") : amount;

  const setRail = (next: string) => {
    setSearchParams(
      (prev) => {
        const params = new URLSearchParams(prev);
        params.set("rail", next);
        return params;
      },
      { replace: true },
    );
    // Selections do not carry across rails: eligibility differs, so a seller selected under Wallet
    // Transfer may be ineligible under Gift Card.
    setSelected(new Set());
    setProviderSku(null);
  };

  const total = useMemo(() => {
    const parsed = Number(effectiveAmount);
    if (!Number.isFinite(parsed) || parsed <= 0) return 0;
    return parsed * selected.size;
  }, [effectiveAmount, selected.size]);

  const available = Number(wallet?.availableBalance ?? 0);
  const remaining = available - total;

  const blockedReason = (() => {
    // Ahead of the ordinary prompts: asking someone to pick a card and an amount, then refusing to send,
    // wastes their time on a rail that cannot be used at all yet.
    if (railSendBlocked(rail)) return XTRM_RAIL_UNAVAILABLE_REASON;
    if (!wallet) return "This company's wallet hasn't been funded yet.";
    if (rail === "GIFT_CARD" && catalogError) {
      return "The gift-card catalogue is unavailable right now.";
    }
    if (rail === "GIFT_CARD" && !providerSku) return "Choose a gift card to distribute.";
    if (!(Number(effectiveAmount) > 0)) return "Enter an amount for each recipient.";
    if (selected.size === 0) return "Select at least one recipient.";
    if (total > available) return "This distribution exceeds the company's available balance.";
    return null;
  })();

  const submit = () => {
    if (blockedReason || !wallet) return;
    createDistribution.mutate(
      {
        rail,
        sourceWalletId: wallet.id,
        providerSku: rail === "GIFT_CARD" ? providerSku : null,
        amount: effectiveAmount,
        userIds: [...selected],
        note: note.trim() || null,
        // Guards against a double-click reserving twice; the server returns the original distribution.
        clientIdempotencyKey: crypto.randomUUID(),
      },
      {
        onSuccess: (created) => {
          toast({
            title: "Distribution sent",
            description: `${created.recipientCount} recipient${created.recipientCount === 1 ? "" : "s"} — paying out now.`,
          });
          navigate(`/redemption/distribution/history?highlight=${created.id}`);
        },
        onError: (e: unknown) => {
          const message =
            (e as { response?: { data?: { errorMessage?: string } } })?.response?.data?.errorMessage ??
            "Could not send the distribution.";
          toast({ title: "Distribution failed", description: message, variant: "destructive" });
        },
      },
    );
  };

  return (
    <div className="animate-route-in p-6 space-y-6 pb-28" data-testid="distribution-store-page">
      <div>
        <h1 className="text-2xl font-semibold">Distribution Store</h1>
        <p className="text-sm text-muted-foreground mt-1">
          Send rewards to your sellers from the company wallet.
        </p>
      </div>

      <CompanyBalanceHeader
        wallet={wallet}
        isLoading={walletLoading}
        pendingTotal={total}
        remaining={remaining}
      />

      {/* An unfunded company has no wallet row at all, so there is nothing to select and no rail can
          work — say so plainly rather than showing an empty picker. */}
      {!walletLoading && !wallet ? (
        <Alert data-testid="unfunded-company">
          <AlertDescription>
            This company's wallet hasn't been funded yet. Ask a client admin to fund it before
            distributing.
          </AlertDescription>
        </Alert>
      ) : (
        <>
          <Tabs value={rail} onValueChange={setRail}>
            <TabsList>
              {RAILS.map((r) => (
                <TabsTrigger key={r.value} value={r.value} data-testid={`rail-${r.value}`}>
                  {r.label}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>

          {payoutSetupNeeded && (
            <Alert data-testid="payout-setup-needed">
              <AlertDescription className="flex items-center justify-between gap-3">
                <span>
                  Your company&apos;s payout account isn&apos;t set up yet, so no
                  seller can be paid on these rails.
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  className="shrink-0"
                  onClick={() => navigate("/redemption/distribution/payout-setup")}
                >
                  Finish setup
                </Button>
              </AlertDescription>
            </Alert>
          )}

          {railSendBlocked(rail) && (
            <Alert data-testid="rails-unavailable">
              <AlertDescription>
                This rail is {XTRM_RAIL_UNAVAILABLE_REASON.toLowerCase()} — you can review it and check
                which sellers are ready, but sending is disabled.
              </AlertDescription>
            </Alert>
          )}

          {rail === "GIFT_CARD" && (
            <GiftCardPicker
              items={catalog}
              isLoading={catalogLoading}
              selectedId={providerSku}
              onSelect={(id) => {
                setProviderSku(id);
                setAmount("");
              }}
            />
          )}

          <Card>
            <CardContent className="pt-6 space-y-4">
              <div className="max-w-xs space-y-2">
                <Label htmlFor="amount">Each recipient gets</Label>
                <Input
                  id="amount"
                  data-testid="amount-input"
                  inputMode="decimal"
                  value={effectiveAmount}
                  readOnly={isFixed}
                  onChange={(e) => setAmount(e.target.value)}
                  placeholder="0.00"
                />
                {isFixed ? (
                  <p className="text-xs text-muted-foreground">
                    Fixed denomination — the amount is set by the gift card.
                  </p>
                ) : selectedCard ? (
                  <p className="text-xs text-muted-foreground">
                    Between {selectedCard.minAmount}
                    {selectedCard.maxAmount ? ` and ${selectedCard.maxAmount}` : ""}
                  </p>
                ) : null}
              </div>

              <div className="max-w-xl space-y-2">
                <Label htmlFor="note">Message (optional)</Label>
                <Textarea
                  id="note"
                  data-testid="note-input"
                  value={note}
                  maxLength={500}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder="Shown to recipients with their reward"
                />
              </div>
            </CardContent>
          </Card>

          <RecipientTable
            recipients={recipients}
            isLoading={recipientsLoading}
            selected={selected}
            onChange={setSelected}
          />

          <ReviewStrip
            selectedCount={selected.size}
            amountEach={effectiveAmount}
            total={total}
            remaining={remaining}
            currency={wallet?.currencyId ?? ""}
            blockedReason={blockedReason}
            isSubmitting={createDistribution.isPending}
            onSubmit={submit}
          />
        </>
      )}
    </div>
  );
}
