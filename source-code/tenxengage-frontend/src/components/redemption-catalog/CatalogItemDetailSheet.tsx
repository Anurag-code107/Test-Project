import { useState, useMemo, useEffect } from "react";
import { Clock, RotateCcw, Loader2 } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { getCurrency } from "@/config/currencies";
import { validateRedemptionAmount } from "@/utils/redemptionAmount";
import { usePartnerCatalogItem } from "@/hooks/useRedemptionCatalog";
import { useMyWallets, useCompanyWallet } from "@/hooks/useWallet";
import { useRedemptionSubmit } from "@/hooks/useRedemptionSubmit";
import { useAuth } from "@/hooks/useAuth";
import { useNavigate } from "react-router-dom";
// Company redemption still runs through the modal (gated off by COMPANY_REDEMPTION_ENABLED);
// the personal store flow now submits inline in this drawer.
import { RedemptionSubmitModal } from "@/components/redemption-flow/RedemptionSubmitModal";
import { ShortfallBadge } from "./ShortfallBadge";
import { COMPANY_REDEMPTION_ENABLED } from "@/config/redemptionFeatures";

interface Props {
  itemId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CatalogItemDetailSheet({ itemId, open, onOpenChange }: Props) {
  const { data: item, isLoading, isError, error } = usePartnerCatalogItem(itemId);
  const { data: wallets } = useMyWallets();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [redeemCompanyModalOpen, setRedeemCompanyModalOpen] = useState(false);
  const [amount, setAmount] = useState<string>("");
  const [fieldError, setFieldError] = useState<string | null>(null);

  // SKU-driven value type. FIXED locks the amount to the single denomination (min == max);
  // VARIABLE allows any value within [min, max]. Legacy items have no value type (min only).
  const isFixedValue = item?.valueType === "FIXED";
  const maxAmount = item?.effectiveMaxTransactionAmount ?? null;

  const canRedeem = user?.permissions.includes("action.redemption.redeem") ?? false;
  // Company redemption is not yet supported end-to-end — hide the surface until it ships.
  // Folding the flag in here switches off the button, its modal, and the company-wallet fetch together.
  const canRedeemCompany =
    COMPANY_REDEMPTION_ENABLED &&
    (user?.permissions.includes("action.redemption.redeem_company") ?? false);
  const wallet = useMemo(
    () => (item ? wallets?.find((w) => w.currencyId === item.currencyId) : undefined),
    [item, wallets],
  );

  // Live client-side validation of the typed amount against the item's bounds and the wallet
  // balance. Drives both the inline message and the Redeem button's enabled state, so an
  // out-of-range amount can never be submitted. FIXED items can't be edited, so the only failure
  // they can hit is an unaffordable denomination.
  const validationError = useMemo(() => {
    if (!item) return null;
    return validateRedemptionAmount({
      amount,
      currencyId: item.currencyId,
      min: item.effectiveMinTransactionAmount,
      max: maxAmount,
      availableBalance: wallet?.availableBalance ?? null,
    });
  }, [item, amount, maxAmount, wallet]);

  const redeemMutation = useRedemptionSubmit({
    type: "personal",
    onSuccess: (id) => {
      onOpenChange(false);
      navigate(`/redemption/confirmation/${id}`);
    },
    onFieldError: (_field, message) => setFieldError(message),
    onInFlightError: () => onOpenChange(false),
  });

  // Pre-fill the amount with the item's minimum whenever a (different) item loads.
  // String(): the API sends these BigDecimal fields as JSON numbers even though the type (and the
  // contract) say decimal string — keep `amount` a genuine string so the input and validator agree.
  useEffect(() => {
    if (item) {
      setAmount(String(item.effectiveMinTransactionAmount));
      setFieldError(null);
    }
  }, [item?.id, item?.effectiveMinTransactionAmount]);

  const handleRedeem = () => {
    if (!item || !wallet) return;
    setFieldError(null);
    // Belt-and-braces: the button is already disabled while invalid, but a stale click must not
    // reach the server. The server re-checks all of this and stays authoritative.
    if (validationError) {
      setFieldError(validationError);
      return;
    }
    // Intermediate variable (not a direct literal) so the extra walletId doesn't
    // trip TS excess-property checking — mirrors RedemptionSubmitModal.
    const req = { catalogItemId: item.id, walletId: wallet.id, amount, currencyId: item.currencyId };
    redeemMutation.mutate(req);
  };

  const { data: companyWallets } = useCompanyWallet(canRedeemCompany ? user?.partnerCompanyId : null);
  const companyWallet = useMemo(
    () => (item ? companyWallets?.find((w) => w.currencyId === item.currencyId) : undefined),
    [item, companyWallets],
  );

  const is404 =
    isError &&
    (error as { response?: { status?: number } })?.response?.status === 404;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full sm:max-w-md overflow-y-auto">
        {isLoading && (
          <div className="space-y-4 pt-6">
            <Skeleton className="h-6 w-3/4" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-2/3" />
          </div>
        )}

        {is404 && (
          <div className="pt-8 flex flex-col items-center gap-3 text-center">
            <p className="text-sm text-muted-foreground">This item is no longer available.</p>
            <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>
              Close
            </Button>
          </div>
        )}

        {isError && !is404 && (
          <div className="pt-8 flex flex-col items-center gap-3 text-center">
            <p className="text-sm text-destructive">Could not load item details. Please try again.</p>
            <Button variant="outline" size="sm" onClick={() => onOpenChange(false)}>
              Close
            </Button>
          </div>
        )}

        {item && (
          <>
            <SheetHeader className="mb-4">
              <SheetTitle>{item.name}</SheetTitle>
              {item.description && (
                <SheetDescription>{item.description}</SheetDescription>
              )}
            </SheetHeader>

            <div className="space-y-4">
              {canRedeem ? (
                <div>
                  <Label
                    htmlFor="redeem-amount"
                    className="text-xs text-muted-foreground uppercase tracking-wide mb-1 block"
                  >
                    {isFixedValue ? "Amount (fixed)" : "Desired Amount"}
                  </Label>
                  <div className="relative">
                    {(() => {
                      const currencyId = item.currencyId;
                      const isPrefix = currencyId === "cash";
                      const suffix = isPrefix
                        ? ""
                        : (() => {
                            const sample = getCurrency(currencyId).rewardFormat("0");
                            const extracted = sample.replace(/^[\d,.\s]+/, "").trim();
                            return extracted && !extracted.includes("$") ? extracted : currencyId;
                          })();
                      return (
                        <>
                          {isPrefix && (
                            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground pointer-events-none">
                              $
                            </span>
                          )}
                          <Input
                            id="redeem-amount"
                            type="number"
                            value={amount}
                            min={item.effectiveMinTransactionAmount}
                            max={isFixedValue ? item.effectiveMinTransactionAmount : (maxAmount ?? undefined)}
                            readOnly={isFixedValue}
                            aria-readonly={isFixedValue}
                            className={`${isPrefix ? "pl-7" : "pr-20"}${isFixedValue ? " bg-muted cursor-not-allowed" : ""}`}
                            onChange={(e) => {
                              if (isFixedValue) return;
                              setAmount(e.target.value);
                              setFieldError(null);
                            }}
                          />
                          {suffix && (
                            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground pointer-events-none">
                              {suffix}
                            </span>
                          )}
                        </>
                      );
                    })()}
                  </div>
                  {(fieldError ?? validationError) ? (
                    <p className="text-sm text-destructive mt-1.5" data-testid="field-error">
                      {fieldError ?? validationError}
                    </p>
                  ) : wallet ? (
                    <p className="text-sm text-muted-foreground mt-1.5">
                      Available: {getCurrency(wallet.currencyId).rewardFormat(wallet.availableBalance)}
                    </p>
                  ) : null}
                  {!isFixedValue && item.valueType === "VARIABLE" && maxAmount != null && (
                    <p className="text-xs text-muted-foreground mt-1">
                      Enter an amount between{" "}
                      {getCurrency(item.currencyId).rewardFormat(item.effectiveMinTransactionAmount)} and{" "}
                      {getCurrency(item.currencyId).rewardFormat(maxAmount)}.
                    </p>
                  )}
                </div>
              ) : (
                <div>
                  <p className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                    {isFixedValue ? "Amount" : "Minimum amount"}
                  </p>
                  <p className="text-sm font-medium">
                    {isFixedValue || maxAmount == null
                      ? getCurrency(item.currencyId).rewardFormat(item.effectiveMinTransactionAmount)
                      : `${getCurrency(item.currencyId).rewardFormat(item.effectiveMinTransactionAmount)}–${getCurrency(item.currencyId).rewardFormat(maxAmount)}`}
                  </p>
                </div>
              )}

              <div>
                <p className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                  Estimated payout
                </p>
                <div className="flex items-center gap-1 text-sm">
                  <Clock className="w-3.5 h-3.5 text-muted-foreground" />
                  {item.estimatedPayoutTimeline}
                </div>
              </div>

              {item.isReturnable && (
                <div>
                  <p className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
                    Return window
                  </p>
                  <div className="flex items-center gap-1 text-sm">
                    <RotateCcw className="w-3.5 h-3.5 text-muted-foreground" />
                    {item.effectiveReturnWindowDays} days
                  </div>
                </div>
              )}

              {!item.canAfford && (
                <ShortfallBadge
                  shortfallAmount={item.shortfallAmount}
                  currencyId={item.currencyId}
                />
              )}

              {canRedeem && (
                <div className="pt-2">
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <span className="inline-block w-full">
                          <Button
                            className="w-full"
                            disabled={
                              !item.canAfford ||
                              !wallet ||
                              validationError != null ||
                              redeemMutation.isPending
                            }
                            onClick={handleRedeem}
                            data-testid="redeem-button"
                          >
                            {redeemMutation.isPending ? (
                              <>
                                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                Submitting…
                              </>
                            ) : (
                              "Redeem"
                            )}
                          </Button>
                        </span>
                      </TooltipTrigger>
                      {!wallet ? (
                        <TooltipContent>Earn {item.currencyId.toUpperCase()} to unlock redemption</TooltipContent>
                      ) : !item.canAfford ? (
                        <TooltipContent>Insufficient balance</TooltipContent>
                      ) : null}
                    </Tooltip>
                  </TooltipProvider>
                </div>
              )}

              {canRedeemCompany && (
                <div className="pt-2">
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <span className="inline-block w-full">
                          <Button
                            className="w-full"
                            variant="outline"
                            disabled={!companyWallet}
                            onClick={() => companyWallet && setRedeemCompanyModalOpen(true)}
                          >
                            Redeem (Company)
                          </Button>
                        </span>
                      </TooltipTrigger>
                      {!companyWallet && (
                        <TooltipContent>No company wallet available</TooltipContent>
                      )}
                    </Tooltip>
                  </TooltipProvider>
                </div>
              )}
            </div>

            {canRedeemCompany && companyWallet && redeemCompanyModalOpen && (
              <RedemptionSubmitModal
                open={redeemCompanyModalOpen}
                onOpenChange={setRedeemCompanyModalOpen}
                item={item}
                wallet={companyWallet}
                type="company"
                companyId={user?.partnerCompanyId ?? undefined}
                onSuccess={(id) => {
                  setRedeemCompanyModalOpen(false);
                  onOpenChange(false);
                  navigate(`/redemption/confirmation/${id}`);
                }}
              />
            )}
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
