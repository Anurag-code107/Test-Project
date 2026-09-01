import { useState } from "react";
import { toast } from "sonner";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { AlertTriangle, Loader2 } from "lucide-react";
import {
  useLinkBankAccount,
  xtrmErrorCode,
  friendlyXtrmError,
} from "@/hooks/redemption-payout/useRedemptionProfileMutations";
import type { LinkBankAccountRequest } from "@/types/redemption-payout/redemption-payout.types";

interface LinkBankFormProps {
  onLinked?: () => void;
  onCancel?: () => void;
}

const EMPTY = {
  contactName: "",
  contactPhone: "",
  accountNumber: "",
  routingNumber: "",
  swiftBic: "",
  institutionName: "",
  addressLine1: "",
  addressLine2: "",
  city: "",
  region: "",
  postalCode: "",
  countryIso2: "",
};

const REQUIRED: (keyof typeof EMPTY)[] = [
  "contactName",
  "contactPhone",
  "accountNumber",
  "routingNumber",
  "institutionName",
  "addressLine1",
  "city",
  "region",
  "postalCode",
];

/**
 * Bank/ACH linking form. Raw account + routing numbers are pass-through to XTRM and never persisted.
 * XTRM domain rejections (422 — duplicate bank, invalid routing, not enrolled) surface inline, not as a toast.
 */
export function LinkBankForm({ onLinked, onCancel }: LinkBankFormProps) {
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const linkBank = useLinkBankAccount();

  const inlineError = linkBank.isError
    ? friendlyXtrmError(xtrmErrorCode(linkBank.error))
    : null;

  const setField = (name: keyof typeof EMPTY, value: string) => {
    setForm((prev) => ({ ...prev, [name]: value }));
    if (errors[name]) setErrors((prev) => ({ ...prev, [name]: "" }));
  };

  const validate = (): boolean => {
    const next: Record<string, string> = {};
    for (const field of REQUIRED) {
      if (!form[field].trim()) next[field] = "Required";
    }
    if (!/^[A-Z]{2}$/.test(form.countryIso2)) next.countryIso2 = "2-letter code (e.g. US)";
    if (form.contactPhone.trim() && !/^[0-9]{7,20}$/.test(form.contactPhone.trim()))
      next.contactPhone = "7–20 digits, no spaces or symbols";
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = () => {
    if (!validate()) return;
    const payload: LinkBankAccountRequest = {
      contactName: form.contactName.trim(),
      contactPhone: form.contactPhone.trim(),
      accountNumber: form.accountNumber.trim(),
      routingNumber: form.routingNumber.trim(),
      swiftBic: form.swiftBic.trim() || undefined,
      institutionName: form.institutionName.trim(),
      addressLine1: form.addressLine1.trim(),
      addressLine2: form.addressLine2.trim() || undefined,
      city: form.city.trim() || undefined,
      region: form.region.trim() || undefined,
      postalCode: form.postalCode.trim() || undefined,
      countryIso2: form.countryIso2,
      // v1 Bank rail is ACH; XTRM withdraw type is fixed for now.
      withdrawType: "ACH",
    };
    linkBank.mutate(payload, {
      onSuccess: () => {
        toast.success("Bank account linked.");
        setForm(EMPTY);
        onLinked?.();
      },
      // 422 rendered inline via inlineError; nothing to do here.
    });
  };

  const field = (
    name: keyof typeof EMPTY,
    label: string,
    opts: {
      required?: boolean;
      maxLength?: number;
      placeholder?: string;
      uppercase?: boolean;
      inputMode?: "numeric" | "text";
    } = {},
  ) => (
    <div className="space-y-1.5">
      <Label htmlFor={name}>
        {label}
        {opts.required ? " *" : ""}
      </Label>
      <Input
        id={name}
        value={form[name]}
        maxLength={opts.maxLength}
        placeholder={opts.placeholder}
        // Sensitive financial fields — keep them out of browser/password-manager autofill.
        autoComplete="off"
        inputMode={opts.inputMode}
        aria-invalid={!!errors[name]}
        aria-describedby={errors[name] ? `${name}-error` : undefined}
        onChange={(e) =>
          setField(name, opts.uppercase ? e.target.value.toUpperCase() : e.target.value)
        }
      />
      {errors[name] && (
        <p id={`${name}-error`} className="text-xs text-destructive">
          {errors[name]}
        </p>
      )}
    </div>
  );

  return (
    <div className="space-y-4">
      {inlineError && (
        <Alert variant="destructive">
          <AlertTriangle className="h-4 w-4" />
          <AlertDescription>{inlineError}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        {field("contactName", "Account holder name", { required: true, maxLength: 140 })}
        {field("contactPhone", "Contact phone", { required: true, maxLength: 20, inputMode: "numeric", placeholder: "14085551234" })}
        {field("institutionName", "Bank name", { required: true, maxLength: 140 })}
        {field("accountNumber", "Account number", { required: true, maxLength: 34, inputMode: "numeric" })}
        {field("routingNumber", "Routing number", { required: true, maxLength: 34, inputMode: "numeric" })}
        {field("swiftBic", "SWIFT / BIC", { maxLength: 11 })}
        {field("addressLine1", "Address line 1", { required: true, maxLength: 255 })}
        {field("addressLine2", "Address line 2", { maxLength: 255 })}
        {field("city", "City", { required: true, maxLength: 120 })}
        {field("region", "State / region", { required: true, maxLength: 120 })}
        {field("postalCode", "Postal code", { required: true, maxLength: 20 })}
        {field("countryIso2", "Country", { required: true, maxLength: 2, placeholder: "US", uppercase: true })}
      </div>

      <div className="flex gap-2">
        <Button onClick={handleSubmit} disabled={linkBank.isPending}>
          {linkBank.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Link bank account
        </Button>
        {onCancel && (
          <Button variant="outline" onClick={onCancel} disabled={linkBank.isPending}>
            Cancel
          </Button>
        )}
      </div>
    </div>
  );
}
