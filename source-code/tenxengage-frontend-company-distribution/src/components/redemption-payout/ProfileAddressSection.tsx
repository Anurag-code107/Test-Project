import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2 } from "lucide-react";
import { useSaveAddress } from "@/hooks/redemption-payout/useRedemptionProfileMutations";
import type {
  RedemptionProfileResponse,
  SaveRedemptionAddressRequest,
} from "@/types/redemption-payout/redemption-payout.types";

interface ProfileAddressSectionProps {
  profile: RedemptionProfileResponse;
}

type AddressForm = {
  addressLine1: string;
  addressLine2: string;
  city: string;
  region: string;
  postalCode: string;
  countryIso2: string;
};

function toForm(p: RedemptionProfileResponse): AddressForm {
  return {
    addressLine1: p.addressLine1 ?? "",
    addressLine2: p.addressLine2 ?? "",
    city: p.city ?? "",
    region: p.region ?? "",
    postalCode: p.postalCode ?? "",
    countryIso2: p.countryIso2 ?? "",
  };
}

/**
 * Collects the payout address (line1 + country required) and saves it, which enrolls the payee for
 * payouts. Pre-fills from the saved address (the user's own PII, returned by the self-only profile
 * endpoint). Enrollment is non-blocking; the status banner reflects the outcome.
 */
export function ProfileAddressSection({ profile }: ProfileAddressSectionProps) {
  const [form, setForm] = useState(() => toForm(profile));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const saveAddress = useSaveAddress();

  const isEnrolled = profile.enrollmentStatus === "ENROLLED";
  const hasSavedAddress = !!profile.addressLine1;
  // Collapse to a one-line summary once enrolled with a saved address (set-and-forget); "Edit" expands the form.
  const [editing, setEditing] = useState(!(isEnrolled && hasSavedAddress));

  // Re-sync when the SAVED address changes (after a save, or a profile refetch). Keyed on the values so
  // a no-op refetch doesn't clobber an in-progress edit.
  useEffect(() => {
    setForm(toForm(profile));
    setErrors({});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    profile.addressLine1,
    profile.addressLine2,
    profile.city,
    profile.region,
    profile.postalCode,
    profile.countryIso2,
  ]);

  const setField = (name: keyof AddressForm, value: string) => {
    setForm((prev) => ({ ...prev, [name]: value }));
    if (errors[name]) setErrors((prev) => ({ ...prev, [name]: "" }));
  };

  const validate = (): boolean => {
    const next: Record<string, string> = {};
    if (!form.addressLine1.trim()) next.addressLine1 = "Address line 1 is required";
    if (!/^[A-Z]{2}$/.test(form.countryIso2)) next.countryIso2 = "Enter a 2-letter country code (e.g. US)";
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSave = () => {
    if (!validate()) return;
    const payload: SaveRedemptionAddressRequest = {
      addressLine1: form.addressLine1.trim(),
      addressLine2: form.addressLine2.trim() || undefined,
      city: form.city.trim() || undefined,
      region: form.region.trim() || undefined,
      postalCode: form.postalCode.trim() || undefined,
      countryIso2: form.countryIso2,
    };
    saveAddress.mutate(payload, {
      onSuccess: (data) => {
        toast.success(
          data.enrollmentStatus === "ENROLLED"
            ? "Payout address saved — you're enrolled for payouts."
            : "Payout address saved. Enrollment will complete shortly.",
        );
        if (data.enrollmentStatus === "ENROLLED") setEditing(false);
      },
      onError: () => toast.error("Couldn't save your address — please try again."),
    });
  };

  // Collapsed summary — set-and-forget once enrolled.
  if (isEnrolled && hasSavedAddress && !editing) {
    const summary = [profile.addressLine1, profile.city, profile.countryIso2]
      .filter(Boolean)
      .join(", ");
    return (
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <h4 className="text-sm font-medium">Payout profile</h4>
          <p className="truncate text-sm text-muted-foreground">📍 {summary}</p>
        </div>
        <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
          Edit
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div>
        <h4 className="text-sm font-medium">Payout profile</h4>
        <p className="text-sm text-muted-foreground">
          {isEnrolled
            ? "Update the address for your digital wallet payout account."
            : "Add your address to enroll for payouts. Line 1 and country are required."}
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2 space-y-1.5">
          <Label htmlFor="addressLine1">Address line 1 *</Label>
          <Input
            id="addressLine1"
            value={form.addressLine1}
            onChange={(e) => setField("addressLine1", e.target.value)}
            aria-invalid={!!errors.addressLine1}
            aria-describedby={errors.addressLine1 ? "addressLine1-error" : undefined}
          />
          {errors.addressLine1 && (
            <p id="addressLine1-error" className="text-xs text-destructive">
              {errors.addressLine1}
            </p>
          )}
        </div>

        <div className="sm:col-span-2 space-y-1.5">
          <Label htmlFor="addressLine2">Address line 2</Label>
          <Input
            id="addressLine2"
            value={form.addressLine2}
            onChange={(e) => setField("addressLine2", e.target.value)}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="city">City</Label>
          <Input id="city" value={form.city} onChange={(e) => setField("city", e.target.value)} />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="region">Region</Label>
          <Input id="region" value={form.region} onChange={(e) => setField("region", e.target.value)} />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="postalCode">Postal code</Label>
          <Input
            id="postalCode"
            value={form.postalCode}
            onChange={(e) => setField("postalCode", e.target.value)}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="countryIso2">Country *</Label>
          <Input
            id="countryIso2"
            value={form.countryIso2}
            maxLength={2}
            placeholder="US"
            onChange={(e) => setField("countryIso2", e.target.value.toUpperCase())}
            aria-invalid={!!errors.countryIso2}
            aria-describedby={errors.countryIso2 ? "countryIso2-error" : undefined}
          />
          {errors.countryIso2 && (
            <p id="countryIso2-error" className="text-xs text-destructive">
              {errors.countryIso2}
            </p>
          )}
        </div>
      </div>

      <div className="flex gap-2">
        <Button onClick={handleSave} disabled={saveAddress.isPending}>
          {saveAddress.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          {isEnrolled ? "Update address" : "Save & enroll"}
        </Button>
        {isEnrolled && hasSavedAddress && (
          <Button variant="outline" onClick={() => setEditing(false)} disabled={saveAddress.isPending}>
            Cancel
          </Button>
        )}
      </div>
    </div>
  );
}
