import { useState, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { Loader2, Landmark } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useLinkedBanks } from "@/hooks/redemption-payout/useRedemptionProfile";
import { useMyWallets } from "@/hooks/useWallet";
import { useBankTransferRedeem } from "@/hooks/useBankTransferRedeem";
import { getCurrency } from "@/config/currencies";
import { validateRedemptionAmount } from "@/utils/redemptionAmount";

// Mirrors BankTransferCardService.CARD_MIN_AMOUNT — the reserved bank-transfer card's floor.
const MIN_AMOUNT = "1";

/**
 * Bank Transfer mode of the redemption store. With no linked bank, shows an empty state + a CTA to the
 * Payout tab. With a linked bank, shows the default bank + an amount input (min $1) that submits a
 * bank-transfer redemption from the user's cash reward wallet into their default linked bank.
 */
export function BankTransferPanel() {
  const navigate = useNavigate();
  const { data: banks = [], isLoading: banksLoading } = useLinkedBanks();
  const { data: wallets } = useMyWallets();
  const [amount, setAmount] = useState<string>(MIN_AMOUNT);
  const [fieldError, setFieldError] = useState<string | null>(null);

  // Bank transfer is funded from the user's cash INDIVIDUAL reward wallet (walletId is required).
  const cashWallet = useMemo(
    () => wallets?.find((w) => w.currencyId === "cash" && w.walletType === "INDIVIDUAL"),
    [wallets],
  );
  const defaultBank = useMemo(() => banks.find((b) => b.isDefault) ?? banks[0], [banks]);
  // Which bank the transfer pays. Defaults to the user's default; a selector lets them switch when
  // more than one bank is linked.
  const [selectedBankId, setSelectedBankId] = useState<string | null>(null);
  const activeBankId = selectedBankId ?? defaultBank?.id ?? null;
  const activeBank = useMemo(
    () => banks.find((b) => b.id === activeBankId) ?? defaultBank,
    [banks, activeBankId, defaultBank],
  );

  // A transfer can never be zero/negative, below the card's floor, or more than the cash the user
  // actually holds. Drives the inline message and the Submit button's enabled state.
  const validationError = useMemo(
    () =>
      validateRedemptionAmount({
        amount,
        currencyId: "cash",
        min: MIN_AMOUNT,
        availableBalance: cashWallet?.availableBalance ?? null,
      }),
    [amount, cashWallet],
  );

  const redeem = useBankTransferRedeem({
    onSuccess: (id) => navigate(`/redemption/confirmation/${id}`),
    onFieldError: (_field, message) => setFieldError(message),
  });

  const handleSubmit = () => {
    if (!cashWallet) return;
    setFieldError(null);
    // Belt-and-braces behind the disabled button; the server re-validates regardless.
    if (validationError) {
      setFieldError(validationError);
      return;
    }
    redeem.mutate({
      walletId: cashWallet.id,
      amount,
      // Only send an explicit bank when there's an actual choice; with a single bank the server pays the default.
      ...(banks.length > 1 && activeBankId ? { bankId: activeBankId } : {}),
    });
  };

  if (banksLoading) {
    return (
      <div className="flex items-center justify-center py-12 text-muted-foreground">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading your bank accounts…
      </div>
    );
  }

  // Empty state — no linked bank yet.
  if (banks.length === 0) {
    return (
      <Card data-testid="bank-transfer-empty">
        <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
          <Landmark className="h-8 w-8 text-muted-foreground" />
          <p className="text-sm font-medium">No bank account linked</p>
          <p className="max-w-sm text-sm text-muted-foreground">
            Link a bank account to transfer your balance directly to your bank.
          </p>
          <Button onClick={() => navigate("/settings/profile?tab=payout&section=banks")}>
            Link a bank account
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="max-w-md">
      <CardContent className="space-y-4 pt-6">
        <div>
          <p className="text-xs text-muted-foreground uppercase tracking-wide mb-1">
            Transfer to
          </p>
          {banks.length > 1 ? (
            <Select value={activeBankId ?? undefined} onValueChange={setSelectedBankId}>
              <SelectTrigger data-testid="bank-select" aria-label="Bank account">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {banks.map((b) => (
                  <SelectItem key={b.id} value={b.id}>
                    {b.label}
                    {b.isDefault ? " · Default" : ""}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : (
            <div className="flex items-center gap-2 rounded-md border p-3">
              <Landmark className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm font-medium">{activeBank?.label}</span>
            </div>
          )}
        </div>

        <div>
          <Label
            htmlFor="bank-transfer-amount"
            className="text-xs text-muted-foreground uppercase tracking-wide mb-1 block"
          >
            Amount
          </Label>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground pointer-events-none">
              $
            </span>
            <Input
              id="bank-transfer-amount"
              type="number"
              value={amount}
              min={MIN_AMOUNT}
              max={cashWallet?.availableBalance ?? undefined}
              className="pl-7"
              onChange={(e) => {
                setAmount(e.target.value);
                setFieldError(null);
              }}
            />
          </div>
          {(fieldError ?? validationError) ? (
            <p className="text-sm text-destructive mt-1.5" data-testid="field-error">
              {fieldError ?? validationError}
            </p>
          ) : cashWallet ? (
            <p className="text-sm text-muted-foreground mt-1.5">
              Available: {getCurrency("cash").rewardFormat(cashWallet.availableBalance)}
            </p>
          ) : (
            <p className="text-sm text-muted-foreground mt-1.5">
              You don't have a cash balance to transfer yet.
            </p>
          )}
        </div>

        <Button
          className="w-full"
          onClick={handleSubmit}
          disabled={!cashWallet || validationError != null || redeem.isPending}
          data-testid="bank-transfer-submit"
        >
          {redeem.isPending ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Submitting…
            </>
          ) : (
            "Submit"
          )}
        </Button>
      </CardContent>
    </Card>
  );
}
