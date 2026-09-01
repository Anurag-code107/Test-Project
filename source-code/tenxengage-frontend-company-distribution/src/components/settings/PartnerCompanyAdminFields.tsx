import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { CompanyAdminIdentity } from "@/types/partner-company.types";

/**
 * The default company admin — five identity fields, rendered statically.
 *
 * Identity only — the five fields the admin's login needs. The address the payment provider also wants is
 * supplied by the admin themselves once they sign in, because a mistyped admin email is spent permanently
 * and the person who owns it should be the one to type it.
 *
 * Deliberately outside the dynamic "Partner Data" field system that drives the rest of this form. These
 * are a payment-provider integration's fixed contract, not tenant-configurable content: routed through the
 * dynamic system, a renamed or removed field would fall into `metadata` instead of the API field, and
 * provisioning would fail for no visible reason.
 *
 * Every tenant therefore sees all five, always.
 */

export const ADMIN_FIELD_KEYS = [
  "adminFirstName",
  "adminLastName",
  "adminEmail",
  "adminMobileNumber",
  "adminCountryIso2",
] as const;

export type AdminFieldKey = (typeof ADMIN_FIELD_KEYS)[number];

const FIELD_LABELS: Record<AdminFieldKey, string> = {
  adminFirstName: "Admin First Name",
  adminLastName: "Admin Last Name",
  adminEmail: "Admin Email",
  adminMobileNumber: "Admin Mobile",
  adminCountryIso2: "Country",
};

const FIELD_PLACEHOLDERS: Partial<Record<AdminFieldKey, string>> = {
  adminMobileNumber: "4085556245",
  adminCountryIso2: "US",
};

/**
 * All five or none.
 *
 * The server enforces the same rule and answers 422; checking here means the user is told which field is
 * missing while they are still looking at the form, instead of after submitting.
 *
 * @returns the missing field labels when the group is partially filled, otherwise an empty array
 */
export function findMissingAdminFields(
  values: CompanyAdminIdentity,
): string[] {
  const filled = ADMIN_FIELD_KEYS.filter(
    (k) => (values[k] ?? "").trim().length > 0,
  );
  if (filled.length === 0 || filled.length === ADMIN_FIELD_KEYS.length) {
    return [];
  }
  return ADMIN_FIELD_KEYS.filter(
    (k) => (values[k] ?? "").trim().length === 0,
  ).map((k) => FIELD_LABELS[k]);
}

/** True when every identity field is present — i.e. this admin can be given a login. */
export function hasCompleteAdminDetails(values: CompanyAdminIdentity): boolean {
  return ADMIN_FIELD_KEYS.every((k) => (values[k] ?? "").trim().length > 0);
}

export interface PartnerCompanyAdminFieldsProps {
  values: CompanyAdminIdentity;
  onChange: (key: AdminFieldKey, value: string) => void;
  idPrefix: string;
  disabled?: boolean;
}

export function PartnerCompanyAdminFields({
  values,
  onChange,
  idPrefix,
  disabled,
}: PartnerCompanyAdminFieldsProps) {
  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h4 className="text-sm font-medium">Company Admin</h4>
        <p className="text-xs text-muted-foreground">
          These details create the company admin&apos;s login. They&apos;ll sign in
          and finish the payout setup themselves — so the email must be one they
          can receive at. Leave blank if this company won&apos;t send rewards.
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3">
        {ADMIN_FIELD_KEYS.map((key) => (
          <div className="space-y-2" key={key}>
            <Label htmlFor={`${idPrefix}-${key}`}>{FIELD_LABELS[key]}</Label>
            <Input
              id={`${idPrefix}-${key}`}
              type={key === "adminEmail" ? "email" : "text"}
              value={values[key] ?? ""}
              placeholder={FIELD_PLACEHOLDERS[key]}
              disabled={disabled}
              maxLength={key === "adminCountryIso2" ? 2 : undefined}
              onChange={(e) => onChange(key, e.target.value)}
            />
          </div>
        ))}
      </div>
    </div>
  );
}
