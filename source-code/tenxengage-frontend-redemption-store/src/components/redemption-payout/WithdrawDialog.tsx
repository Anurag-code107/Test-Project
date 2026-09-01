import { useMemo, useState } from "react";
import { toast } from "sonner";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { AlertTriangle, CheckCircle2, CreditCard, Landmark, Loader2 } from "lucide-react";
import { useLinkedBanks, useLinkedCards } from "@/hooks/redemption-payout/useRedemptionProfile";
import {
  useInitiateWithdrawal,
  useConfirmWithdrawal,
  xtrmErrorCode,
  friendlyXtrmError,
} from "@/hooks/redemption-payout/useRedemptionProfileMutations";
import type { WithdrawalResult } from "@/types/redemption-payout/redemption-payout.types";
import { formatFiat } from "@/lib/formatFiat";

interface WithdrawDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Withdraw from the digital wallet (2-step OTP), as a modal launched from the Digital Wallet tab. The flow is
 * unchanged from the old WithdrawTab — pick a linked bank/card (payout default preselected), enter an amount,
 * receive a one-time code, confirm. State lives in {@link WithdrawForm} inside DialogContent, which Radix
 * unmounts on close, so it resets between opens for free.
 */
export function WithdrawDialog({ open, onOpenChange }: WithdrawDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Withdraw from your wallet</DialogTitle>
          <DialogDescription>
            Move funds from your digital wallet to a linked bank or card.
          </DialogDescription>
        </DialogHeader>
        <WithdrawForm onDone={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  );
}

function WithdrawForm({ onDone }: { onDone: () => void }) {
  const { data: banks = [] } = useLinkedBanks();
  const { data: cards = [] } = useLinkedCards();
  const initiate = useInitiateWithdrawal();
  const confirm = useConfirmWithdrawal();

  const destinations = useMemo(
    () => [
      ...banks.map((b) => ({ key: `BANK:${b.id}`, type: "BANK" as const, id: b.id, label: b.label, isDefault: b.isDefault })),
      ...cards.map((c) => ({ key: `CARD:${c.id}`, type: "CARD" as const, id: c.id, label: c.label, isDefault: c.isDefault })),
    ],
    [banks, cards],
  );
  const defaultKey = destinations.find((d) => d.isDefault)?.key ?? destinations[0]?.key ?? null;

  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const effectiveKey = selectedKey ?? defaultKey;
  const [amount, setAmount] = useState("");
  const [otp, setOtp] = useState("");
  const [step, setStep] = useState<"form" | "otp">("form");
  const [amountError, setAmountError] = useState<string | null>(null);
  const [completed, setCompleted] = useState<WithdrawalResult | null>(null);

  const selectedDest = destinations.find((d) => d.key === effectiveKey) ?? null;
  const initiateError = initiate.isError ? friendlyXtrmError(xtrmErrorCode(initiate.error)) : null;
  const confirmError = confirm.isError ? friendlyXtrmError(xtrmErrorCode(confirm.error)) : null;

  if (destinations.length === 0) {
    return (
      <Alert>
        <AlertTriangle className="h-4 w-4" />
        <AlertTitle>No withdrawal destination</AlertTitle>
        <AlertDescription>
          Link a bank account or card in the Bank Accounts or Cards tab before withdrawing.
        </AlertDescription>
      </Alert>
    );
  }

  if (completed && completed.amountNet != null) {
    return (
      <div className="space-y-4">
        <Alert>
          <CheckCircle2 className="h-4 w-4" />
          <AlertTitle>Withdrawal complete</AlertTitle>
          <AlertDescription>
            {formatFiat(completed.amountNet, completed.currency ?? "USD")} was sent to{" "}
            {completed.destinationLabel ?? "your account"}
            {completed.fee != null && Number(completed.fee) > 0
              ? ` (fee ${formatFiat(completed.fee, completed.currency ?? "USD")})`
              : ""}
            .
          </AlertDescription>
        </Alert>
        <Button className="w-full" onClick={onDone}>
          Done
        </Button>
      </div>
    );
  }

  const handleContinue = () => {
    const amt = Number(amount);
    if (!Number.isFinite(amt) || amt <= 0) {
      setAmountError("Enter an amount greater than 0.");
      return;
    }
    if (!selectedDest) return;
    setAmountError(null);
    initiate.mutate(
      { amount: amt, destinationType: selectedDest.type, destinationId: selectedDest.id },
      {
        onSuccess: (res) => {
          if (res.otpRequired) {
            setOtp("");
            setStep("otp");
          } else {
            setCompleted(res);
          }
        },
      },
    );
  };

  const handleConfirm = () => {
    if (!selectedDest || !otp.trim()) return;
    confirm.mutate(
      { amount: Number(amount), destinationType: selectedDest.type, destinationId: selectedDest.id, otp: otp.trim() },
      {
        onSuccess: (res) => {
          setCompleted(res);
          toast.success("Withdrawal complete.");
        },
      },
    );
  };

  const locked = step === "otp";

  return (
    <div className="space-y-4">
      {/* Destination */}
      <div className="space-y-2">
        <Label>Send to</Label>
        <RadioGroup
          value={effectiveKey ?? undefined}
          onValueChange={setSelectedKey}
          disabled={locked}
          className="space-y-2"
        >
          {destinations.map((d) => (
            <div key={d.key} className="flex items-center gap-3 rounded-md border p-3">
              <RadioGroupItem value={d.key} id={`wd-dest-${d.key}`} />
              {d.type === "BANK" ? (
                <Landmark className="h-4 w-4 text-muted-foreground" />
              ) : (
                <CreditCard className="h-4 w-4 text-muted-foreground" />
              )}
              <Label htmlFor={`wd-dest-${d.key}`} className="text-sm font-medium">
                {d.label}
              </Label>
              {d.isDefault && (
                <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">Default</span>
              )}
            </div>
          ))}
        </RadioGroup>
      </div>

      {/* Amount */}
      <div className="space-y-1.5">
        <Label htmlFor="wd-amount">Amount (USD)</Label>
        <Input
          id="wd-amount"
          type="number"
          min="0"
          step="0.01"
          inputMode="decimal"
          value={amount}
          disabled={locked}
          aria-invalid={!!amountError}
          onChange={(e) => {
            setAmount(e.target.value);
            if (amountError) setAmountError(null);
          }}
          placeholder="0.00"
        />
        {amountError && <p className="text-xs text-destructive">{amountError}</p>}
        <p className="text-xs text-muted-foreground">
          The provider's fee is deducted from this amount; you receive the net.
        </p>
      </div>

      {step === "form" ? (
        <>
          {initiateError && (
            <Alert variant="destructive">
              <AlertTriangle className="h-4 w-4" />
              <AlertDescription>{initiateError}</AlertDescription>
            </Alert>
          )}
          <Button className="w-full" onClick={handleContinue} disabled={initiate.isPending || !selectedDest}>
            {initiate.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            Continue
          </Button>
        </>
      ) : (
        <div className="space-y-3 rounded-md border border-dashed p-4">
          <p className="text-sm text-muted-foreground">
            Enter the one-time code we sent to your email and phone to confirm withdrawing{" "}
            <span className="font-medium text-foreground">{formatFiat(Number(amount), "USD")}</span> to{" "}
            {selectedDest?.label}.
          </p>
          <div className="space-y-1.5">
            <Label htmlFor="wd-otp">One-time code</Label>
            <Input
              id="wd-otp"
              value={otp}
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={20}
              onChange={(e) => setOtp(e.target.value)}
              placeholder="Enter code"
            />
          </div>
          {confirmError && (
            <Alert variant="destructive">
              <AlertTriangle className="h-4 w-4" />
              <AlertDescription>{confirmError}</AlertDescription>
            </Alert>
          )}
          <div className="flex gap-2">
            <Button onClick={handleConfirm} disabled={confirm.isPending || !otp.trim()}>
              {confirm.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Confirm withdrawal
            </Button>
            <Button variant="outline" onClick={() => { setStep("form"); setOtp(""); }} disabled={confirm.isPending}>
              Back
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
