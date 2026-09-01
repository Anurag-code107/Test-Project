import { useState } from "react";
import { toast } from "sonner";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { AlertTriangle, Loader2, Smartphone } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { SUPPORTED_MOBILE_COUNTRIES, dialCodeFor } from "@/lib/mobileCountries";
import {
  useInitiatePhoneUpdate,
  useConfirmPhoneUpdate,
  phoneErrorCode,
  friendlyPhoneError,
} from "@/hooks/useProfilePhone";

/**
 * Edit the current user's mobile number. For an XTRM-enrolled payee this is a 2-step, OTP-confirmed change
 * (the code is texted to the NEW number); a not-yet-enrolled user's number is saved immediately. Required for
 * payout enrollment and to confirm withdrawals.
 */
export function MobileNumberSection() {
  const { user, refreshUser } = useAuth();
  const currentPhone = (user as { phone?: string } | null)?.phone ?? null;

  const [editing, setEditing] = useState(false);
  const [country, setCountry] = useState("US");
  const [number, setNumber] = useState("");
  const [otp, setOtp] = useState("");
  const [step, setStep] = useState<"form" | "otp">("form");
  const [numberError, setNumberError] = useState<string | null>(null);

  const initiate = useInitiatePhoneUpdate();
  const confirm = useConfirmPhoneUpdate();

  const initiateError = initiate.isError ? friendlyPhoneError(phoneErrorCode(initiate.error)) : null;
  const confirmError = confirm.isError ? friendlyPhoneError(phoneErrorCode(confirm.error)) : null;

  const reset = () => {
    setEditing(false);
    setStep("form");
    setNumber("");
    setOtp("");
    setNumberError(null);
  };

  const handleSend = () => {
    if (!/^[0-9]{7,20}$/.test(number.trim())) {
      setNumberError("Enter 7–20 digits (national number, no country code).");
      return;
    }
    setNumberError(null);
    initiate.mutate(
      { phone: number.trim(), phoneCountryIso2: country },
      {
        onSuccess: (res) => {
          if (res.otpRequired) {
            setOtp("");
            setStep("otp");
          } else {
            toast.success("Mobile number updated.");
            void refreshUser();
            reset();
          }
        },
      },
    );
  };

  const handleConfirm = () => {
    if (!otp.trim()) return;
    confirm.mutate(
      { phone: number.trim(), phoneCountryIso2: country, otp: otp.trim() },
      {
        onSuccess: () => {
          toast.success("Mobile number updated.");
          void refreshUser();
          reset();
        },
      },
    );
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl">Mobile number</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground">
          Used for payout enrollment and to confirm withdrawals. Changing it sends a one-time code to the new
          number.
        </p>

        {!editing ? (
          <div className="flex items-center justify-between rounded-md border p-3">
            <div className="flex items-center gap-2">
              <Smartphone className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm font-medium">{currentPhone || "Not set"}</span>
            </div>
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              {currentPhone ? "Change" : "Add"}
            </Button>
          </div>
        ) : step === "form" ? (
          <div className="space-y-3">
            <div className="grid gap-3 sm:grid-cols-[minmax(0,220px)_1fr]">
              <div className="space-y-1.5">
                <Label htmlFor="mobile-country">Country</Label>
                <Select value={country} onValueChange={setCountry}>
                  <SelectTrigger id="mobile-country">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {SUPPORTED_MOBILE_COUNTRIES.map((c) => (
                      <SelectItem key={c.iso2} value={c.iso2}>
                        {c.name} (+{c.dialCode})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="mobile-number">Mobile number</Label>
                <Input
                  id="mobile-number"
                  inputMode="numeric"
                  value={number}
                  aria-invalid={!!numberError}
                  placeholder="4085551284"
                  onChange={(e) => {
                    setNumber(e.target.value);
                    if (numberError) setNumberError(null);
                  }}
                />
                {numberError && <p className="text-xs text-destructive">{numberError}</p>}
              </div>
            </div>
            {initiateError && (
              <Alert variant="destructive">
                <AlertTriangle className="h-4 w-4" />
                <AlertDescription>{initiateError}</AlertDescription>
              </Alert>
            )}
            <div className="flex gap-2">
              <Button onClick={handleSend} disabled={initiate.isPending}>
                {initiate.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Save
              </Button>
              <Button variant="outline" onClick={reset} disabled={initiate.isPending}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-3 rounded-md border border-dashed p-4">
            <p className="text-sm text-muted-foreground">
              Enter the one-time code we sent to +{dialCodeFor(country)} {number}.
            </p>
            <div className="space-y-1.5">
              <Label htmlFor="mobile-otp">One-time code</Label>
              <Input
                id="mobile-otp"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={20}
                value={otp}
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
                Confirm
              </Button>
              <Button
                variant="outline"
                onClick={() => {
                  setStep("form");
                  setOtp("");
                }}
                disabled={confirm.isPending}
              >
                Back
              </Button>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
