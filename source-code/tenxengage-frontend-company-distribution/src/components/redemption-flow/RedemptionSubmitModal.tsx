// Adapted from: none — no production reference
import { useState, useMemo } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2 } from "lucide-react";
import { useRedemptionSubmit } from "@/hooks/useRedemptionSubmit";
import { getCurrency } from "@/config/currencies";
import { validateRedemptionAmount } from "@/utils/redemptionAmount";
import type { CatalogBrowseItemResponse } from "@/types/redemption-catalog.types";
import type { RewardWalletResponse } from "@/types/wallet.types";

interface RedemptionSubmitModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  item: CatalogBrowseItemResponse;
  wallet: RewardWalletResponse;
  onSuccess: (redemptionId: string) => void;
}

export function RedemptionSubmitModal({
  open,
  onOpenChange,
  item,
  wallet,
  onSuccess,
}: RedemptionSubmitModalProps) {
  // String(): the API sends this BigDecimal as a JSON number despite the declared decimal string.
  const [amount, setAmount] = useState(String(item.effectiveMinTransactionAmount));
  const [fieldError, setFieldError] = useState<string | null>(null);

  // FIXED gift cards are a single denomination — min == max, so the amount is not editable.
  const isFixedValue = item.valueType === "FIXED";
  const maxAmount = item.effectiveMaxTransactionAmount ?? null;

  const validationError = useMemo(
    () =>
      validateRedemptionAmount({
        amount,
        currencyId: item.currencyId,
        min: item.effectiveMinTransactionAmount,
        max: maxAmount,
        availableBalance: wallet.availableBalance,
      }),
    [amount, item.currencyId, item.effectiveMinTransactionAmount, maxAmount, wallet.availableBalance],
  );

  const mutation = useRedemptionSubmit({
    onSuccess: (id) => {
      onOpenChange(false);
      onSuccess(id);
    },
    onFieldError: (_field, message) => setFieldError(message),
    onInFlightError: () => onOpenChange(false),
  });

  const handleSubmit = () => {
    setFieldError(null);
    // Belt-and-braces behind the disabled button; the server re-validates regardless.
    if (validationError) {
      setFieldError(validationError);
      return;
    }
    const base = { catalogItemId: item.id, walletId: wallet.id, amount, currencyId: item.currencyId };
    mutation.mutate(base);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Redeem Reward</DialogTitle>
          <DialogDescription className="sr-only">
            Enter the amount you wish to redeem from your wallet.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <p className="text-sm text-muted-foreground">{item.name}</p>

          <div className="space-y-1.5">
            <Label htmlFor="redemption-amount">
              {isFixedValue ? "Amount (fixed)" : "Amount"}
            </Label>
            <div className="relative">
              {(() => {
                const currencyId = item.currencyId;
                const isPrefix = currencyId === "cash";
                const suffix = isPrefix ? "" : (() => {
                  const sample = getCurrency(currencyId).rewardFormat("0");
                  const extracted = sample.replace(/^[\d,.\s]+/, "").trim();
                  // If extracted is empty or contains $ (fmtUsd fallback), use currencyId
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
                      id="redemption-amount"
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
              <p className="text-sm text-destructive" data-testid="field-error">
                {fieldError ?? validationError}
              </p>
            ) : (
              <p className="text-sm text-muted-foreground">
                Available: {getCurrency(wallet.currencyId).rewardFormat(wallet.availableBalance)}
              </p>
            )}
          </div>
        </div>

        <DialogFooter className="gap-2">
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={mutation.isPending}
          >
            Cancel
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={validationError != null || mutation.isPending}
            data-testid="submit-button"
          >
            {mutation.isPending ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Submitting…
              </>
            ) : (
              "Submit Redemption"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
