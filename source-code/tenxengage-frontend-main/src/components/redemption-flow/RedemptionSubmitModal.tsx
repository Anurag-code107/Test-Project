// Adapted from: none — no production reference
import { useState } from "react";
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
import type { CatalogBrowseItemResponse } from "@/types/redemption-catalog.types";
import type { RewardWalletResponse } from "@/types/wallet.types";

interface RedemptionSubmitModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  item: CatalogBrowseItemResponse;
  wallet: RewardWalletResponse;
  onSuccess: (redemptionId: string) => void;
  type?: "personal" | "company";
  companyId?: string;
}

export function RedemptionSubmitModal({
  open,
  onOpenChange,
  item,
  wallet,
  onSuccess,
  type = "personal",
  companyId,
}: RedemptionSubmitModalProps) {
  const [amount, setAmount] = useState(item.effectiveMinTransactionAmount);
  const [fieldError, setFieldError] = useState<string | null>(null);

  const mutation = useRedemptionSubmit({
    type,
    onSuccess: (id) => {
      onOpenChange(false);
      onSuccess(id);
    },
    onFieldError: (_field, message) => setFieldError(message),
    onInFlightError: () => onOpenChange(false),
  });

  const handleSubmit = () => {
    setFieldError(null);
    const base = { catalogItemId: item.id, walletId: wallet.id, amount, currencyId: item.currencyId };
    mutation.mutate(type === "company" && companyId ? { ...base, companyId } : base);
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
            <Label htmlFor="redemption-amount">Amount</Label>
            <Input
              id="redemption-amount"
              type="number"
              value={amount}
              min={item.effectiveMinTransactionAmount}
              onChange={(e) => {
                setAmount(e.target.value);
                setFieldError(null);
              }}
            />
            {fieldError ? (
              <p className="text-sm text-destructive" data-testid="field-error">
                {fieldError}
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
            disabled={mutation.isPending}
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
