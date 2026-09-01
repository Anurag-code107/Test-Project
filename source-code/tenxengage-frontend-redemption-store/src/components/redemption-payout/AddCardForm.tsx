import { useState } from "react";
import { toast } from "sonner";
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
import { AlertTriangle, Loader2, ShieldCheck } from "lucide-react";
import {
  useAddCard,
  xtrmErrorCode,
  friendlyXtrmError,
} from "@/hooks/redemption-payout/useRedemptionProfileMutations";
import type { AddCardRequest } from "@/types/redemption-payout/redemption-payout.types";

interface AddCardFormProps {
  onLinked?: () => void;
  onCancel?: () => void;
}

// XTRM's LinkCard rejects any CardType other than these exact strings
// ("Invalid card type. Card type must be Visa Card or Master Card"), so the value
// is chosen from a fixed list rather than free-typed.
const CARD_TYPES = ["Visa Card", "Master Card"] as const;

const EMPTY = {
  cardNumber: "",
  expMonth: "",
  expYear: "",
  cvv: "",
  cardType: "",
  nameOnCard: "",
  firstName: "",
  lastName: "",
  addressLine1: "",
  addressLine2: "",
  city: "",
  region: "",
  postalCode: "",
  countryIso2: "",
};

const REQUIRED: (keyof typeof EMPTY)[] = [
  "cardNumber",
  "expMonth",
  "expYear",
  "cvv",
  "cardType",
  "nameOnCard",
  "firstName",
  "lastName",
  "addressLine1",
  "city",
  "region",
  "postalCode",
];

/**
 * Card linking form. ⚠️ PCI: the raw card number / CVV / expiry are pass-through to XTRM and NEVER stored —
 * the form state is cleared right after a successful submit and is never logged. XTRM domain rejections
 * (422 — declined card, duplicate, not enrolled) surface inline, not as a toast.
 */
export function AddCardForm({ onLinked, onCancel }: AddCardFormProps) {
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const addCard = useAddCard();

  const inlineError = addCard.isError ? friendlyXtrmError(xtrmErrorCode(addCard.error)) : null;

  const setField = (name: keyof typeof EMPTY, value: string) => {
    setForm((prev) => ({ ...prev, [name]: value }));
    if (errors[name]) setErrors((prev) => ({ ...prev, [name]: "" }));
  };

  const validate = (): boolean => {
    const next: Record<string, string> = {};
    for (const field of REQUIRED) {
      if (!form[field].trim()) next[field] = "Required";
    }
    if (form.cardNumber.trim() && !/^[0-9]{12,19}$/.test(form.cardNumber.trim()))
      next.cardNumber = "12–19 digits, no spaces";
    if (form.expMonth.trim() && !/^(0[1-9]|1[0-2])$/.test(form.expMonth.trim()))
      next.expMonth = "MM (01–12)";
    if (form.expYear.trim() && !/^[0-9]{4}$/.test(form.expYear.trim()))
      next.expYear = "YYYY (e.g. 2029)";
    if (form.cvv.trim() && !/^[0-9]{3,4}$/.test(form.cvv.trim())) next.cvv = "3–4 digits";
    if (!/^[A-Z]{2}$/.test(form.countryIso2)) next.countryIso2 = "2-letter code (e.g. US)";
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = () => {
    if (!validate()) return;
    const payload: AddCardRequest = {
      cardNumber: form.cardNumber.trim(),
      expMonth: form.expMonth.trim(),
      expYear: form.expYear.trim(),
      cvv: form.cvv.trim(),
      cardType: form.cardType.trim(),
      nameOnCard: form.nameOnCard.trim(),
      firstName: form.firstName.trim(),
      lastName: form.lastName.trim(),
      addressLine1: form.addressLine1.trim(),
      addressLine2: form.addressLine2.trim() || undefined,
      city: form.city.trim(),
      region: form.region.trim(),
      postalCode: form.postalCode.trim(),
      countryIso2: form.countryIso2,
    };
    addCard.mutate(payload, {
      onSuccess: () => {
        toast.success("Card linked.");
        // ⚠️ PCI: clear the raw card out of component state immediately.
        setForm(EMPTY);
        onLinked?.();
      },
      // 422 rendered inline via inlineError.
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
        // ⚠️ PCI: keep card fields out of browser/password-manager autofill and history.
        autoComplete="off"
        inputMode={opts.inputMode}
        aria-invalid={!!errors[name]}
        aria-describedby={errors[name] ? `${name}-error` : undefined}
        onChange={(e) => setField(name, opts.uppercase ? e.target.value.toUpperCase() : e.target.value)}
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
      <Alert>
        <ShieldCheck className="h-4 w-4" />
        <AlertDescription>
          Your card details are sent securely to our payments provider and are never stored on TenXEngage —
          we keep only a masked last-4 for display.
        </AlertDescription>
      </Alert>

      {inlineError && (
        <Alert variant="destructive">
          <AlertTriangle className="h-4 w-4" />
          <AlertDescription>{inlineError}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        {field("cardNumber", "Card number", { required: true, maxLength: 19, inputMode: "numeric", placeholder: "4111111111111111" })}
        <div className="space-y-1.5">
          <Label htmlFor="cardType">Card type *</Label>
          <Select value={form.cardType} onValueChange={(v) => setField("cardType", v)}>
            <SelectTrigger
              id="cardType"
              aria-label="Card type"
              aria-invalid={!!errors.cardType}
              aria-describedby={errors.cardType ? "cardType-error" : undefined}
            >
              <SelectValue placeholder="Select card type" />
            </SelectTrigger>
            <SelectContent>
              {CARD_TYPES.map((t) => (
                <SelectItem key={t} value={t}>{t}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          {errors.cardType && (
            <p id="cardType-error" className="text-xs text-destructive">
              {errors.cardType}
            </p>
          )}
        </div>
        {field("expMonth", "Expiry month", { required: true, maxLength: 2, inputMode: "numeric", placeholder: "12" })}
        {field("expYear", "Expiry year", { required: true, maxLength: 4, inputMode: "numeric", placeholder: "2029" })}
        {field("cvv", "CVV", { required: true, maxLength: 4, inputMode: "numeric", placeholder: "123" })}
        {field("nameOnCard", "Name on card", { required: true, maxLength: 140 })}
        {field("firstName", "First name", { required: true, maxLength: 140 })}
        {field("lastName", "Last name", { required: true, maxLength: 140 })}
        {field("addressLine1", "Address line 1", { required: true, maxLength: 255 })}
        {field("addressLine2", "Address line 2", { maxLength: 255 })}
        {field("city", "City", { required: true, maxLength: 120 })}
        {field("region", "State / region", { required: true, maxLength: 120 })}
        {field("postalCode", "Postal code", { required: true, maxLength: 20 })}
        {field("countryIso2", "Country", { required: true, maxLength: 2, placeholder: "US", uppercase: true })}
      </div>

      <div className="flex gap-2">
        <Button onClick={handleSubmit} disabled={addCard.isPending}>
          {addCard.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Link card
        </Button>
        {onCancel && (
          <Button variant="outline" onClick={onCancel} disabled={addCard.isPending}>
            Cancel
          </Button>
        )}
      </div>
    </div>
  );
}
