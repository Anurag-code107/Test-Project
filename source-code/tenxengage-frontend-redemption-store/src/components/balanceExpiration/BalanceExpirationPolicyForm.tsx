// Adapted from: src/pages/client-admin/MyProfilePage.tsx (form shell + Card structure)
// Production analog: Settings / config page → form sections pattern
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import {
  Card,
  CardContent,
  CardHeader,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { getCurrency } from "@/config/currencies";
import { formatDate } from "@/utils/formatters";
import { useUpsertBalanceExpirationPolicy } from "@/hooks/useUpsertBalanceExpirationPolicy";
import {
  balanceExpirationPolicySchema,
  type BalanceExpirationPolicyFormValues,
} from "@/components/balanceExpiration/balanceExpirationPolicySchema";
import type { BalanceExpirationPolicyResponse } from "@/types/balanceExpiration.types";

/**
 * YYYY-MM-DD from local calendar fields — avoids the toISOString() UTC shift
 * (PROJECT-CONTEXT.md date-only anti-pattern: toISOString() can yield the wrong
 * calendar day for users east of UTC). Mirrors BreakageReportTable's helper.
 */
function toLocalDateString(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

interface BalanceExpirationPolicyFormProps {
  currencyId: string;
  /** Undefined = no saved policy (show disabled defaults) */
  policy: BalanceExpirationPolicyResponse | undefined;
}

/**
 * BalanceExpirationPolicyForm
 *
 * Covers AC-2, AC-3, AC-6 — per-currency expiration policy configuration card.
 * Renders the mode-conditional fields (AC conditional rendering):
 *   - INACTIVITY: show inactivityDays, hide fixedExpiryDate
 *   - FIXED_DATE: show fixedExpiryDate, hide inactivityDays
 * All four currencies are always presented (AC-6); currencies with no saved
 * policy render as disabled/unconfigured defaults (never hidden).
 *
 * Accessibility: RadioGroup has aria-labelledby; loading spinner has aria-hidden;
 * error paragraphs have role="alert".
 */
export function BalanceExpirationPolicyForm({
  currencyId,
  policy,
}: BalanceExpirationPolicyFormProps) {
  const currency = getCurrency(currencyId.toLowerCase());
  const { upsert, isPending } = useUpsertBalanceExpirationPolicy();

  const {
    register,
    handleSubmit,
    control,
    reset,
    setValue,
    setError,
    formState: { errors, isDirty, isValid },
  } = useForm<BalanceExpirationPolicyFormValues>({
    resolver: zodResolver(balanceExpirationPolicySchema),
    mode: "onChange",
    values: {
      enabled: policy?.enabled ?? false,
      expirationMode: policy?.expirationMode ?? "INACTIVITY",
      inactivityDays: policy?.inactivityDays ?? undefined,
      fixedExpiryDate: policy?.fixedExpiryDate ?? null,
      leadTimeDays: policy?.leadTimeDays ?? 30,
    },
  });

  // Single useWatch call for both reactive values — one subscription vs two
  const [enabled, expirationMode] = useWatch({ control, name: ["enabled", "expirationMode"] });

  // Min selectable date for the fixed-expiry picker — computed once (lazy) in local time
  const [minFixedDate] = useState(() => toLocalDateString(new Date()));

  const onSubmit = async (values: BalanceExpirationPolicyFormValues) => {
    // isPending from TanStack Query is authoritative for in-flight state
    if (isPending) return;
    try {
      const success = await upsert(
        currencyId,
        {
          enabled: values.enabled,
          expirationMode: values.expirationMode,
          inactivityDays: values.inactivityDays ?? null,
          fixedExpiryDate: values.fixedExpiryDate ?? null,
          leadTimeDays: values.leadTimeDays,
        },
        setError,
      );
      if (success) {
        toast.success("Expiration policy saved");
        reset(values); // clear dirty state
      }
    } catch {
      toast.error("Could not save expiration policy — please try again");
    }
  };

  const statusCaption = policy?.enabled && policy.enabledAt
    ? `Active since ${formatDate(policy.enabledAt)}`
    : "Not configured";

  return (
    <Card className="border border-border">
      <form onSubmit={handleSubmit(onSubmit)}>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          {/* (a) Currency header */}
          <div className="flex items-center gap-3">
            <span className={cn("h-8 w-8 flex items-center justify-center rounded-lg", currency.iconBgClass)}>
              <currency.icon className={cn("h-4 w-4", currency.iconClass)} aria-hidden="true" />
            </span>
            <div>
              <p className="text-sm font-medium text-foreground">{currency.label}</p>
              <p className="text-xs text-muted-foreground">{statusCaption}</p>
            </div>
          </div>
          {/* Enable toggle */}
          <div className="flex items-center gap-2">
            <Label
              htmlFor={`enabled-${currencyId}`}
              className="text-xs text-muted-foreground"
            >
              {enabled ? "Enabled" : "Disabled"}
            </Label>
            <Switch
              id={`enabled-${currencyId}`}
              checked={enabled}
              onCheckedChange={(checked) => setValue("enabled", checked, { shouldDirty: true })}
            />
          </div>
        </div>
      </CardHeader>

        <CardContent className="space-y-4">
          {/* (b) Mode radio — disabled when not enabled */}
          <fieldset disabled={!enabled} className="space-y-2">
            <Label
              id={`mode-label-${currencyId}`}
              className="text-xs font-medium text-muted-foreground"
            >
              Expiration mode
            </Label>
            <RadioGroup
              aria-labelledby={`mode-label-${currencyId}`}
              value={expirationMode}
              onValueChange={(val) => {
                const mode = val as "INACTIVITY" | "FIXED_DATE";
                setValue("expirationMode", mode, { shouldDirty: true });
                // Clear the field that no longer applies (event handler, not useEffect)
                if (mode === "INACTIVITY") {
                  setValue("fixedExpiryDate", null);
                } else {
                  setValue("inactivityDays", undefined);
                }
              }}
              className="flex flex-wrap gap-4"
            >
              <div className="flex items-center gap-1.5">
                <RadioGroupItem value="INACTIVITY" id={`inactivity-${currencyId}`} />
                <Label htmlFor={`inactivity-${currencyId}`} className="text-sm">
                  Inactivity period
                </Label>
              </div>
              <div className="flex items-center gap-1.5">
                <RadioGroupItem value="FIXED_DATE" id={`fixed-${currencyId}`} />
                <Label htmlFor={`fixed-${currencyId}`} className="text-sm">
                  Fixed calendar date
                </Label>
              </div>
            </RadioGroup>
          </fieldset>

          {/* (c) Mode params — conditional */}
          {expirationMode === "INACTIVITY" ? (
            <div className="space-y-1">
              <Label
                htmlFor={`inactivityDays-${currencyId}`}
                className="text-xs text-muted-foreground"
              >
                Inactivity period (days)
              </Label>
              <Input
                id={`inactivityDays-${currencyId}`}
                type="number"
                min={30}
                max={1825}
                disabled={!enabled}
                placeholder="e.g. 365"
                {...register("inactivityDays")}
                className={cn(errors.inactivityDays && "border-destructive")}
              />
              {errors.inactivityDays && (
                <p
                  role="alert"
                  className="text-xs text-destructive mt-1"
                >
                  {errors.inactivityDays.message}
                </p>
              )}
            </div>
          ) : (
            <div className="space-y-1">
              <Label
                htmlFor={`fixedExpiryDate-${currencyId}`}
                className="text-xs text-muted-foreground"
              >
                Fixed expiry date
              </Label>
              <Input
                id={`fixedExpiryDate-${currencyId}`}
                type="date"
                min={minFixedDate}
                disabled={!enabled}
                {...register("fixedExpiryDate")}
                className={cn(errors.fixedExpiryDate && "border-destructive")}
              />
              {errors.fixedExpiryDate && (
                <p
                  role="alert"
                  className="text-xs text-destructive mt-1"
                >
                  {errors.fixedExpiryDate.message}
                </p>
              )}
            </div>
          )}

          {/* (d) Lead time */}
          <div className="space-y-1">
            <Label
              htmlFor={`leadTimeDays-${currencyId}`}
              className="text-xs text-muted-foreground"
            >
              Lead time (days)
            </Label>
            <Input
              id={`leadTimeDays-${currencyId}`}
              type="number"
              min={1}
              disabled={!enabled}
              placeholder="e.g. 30"
              {...register("leadTimeDays")}
              className={cn(errors.leadTimeDays && "border-destructive")}
            />
            <p className="text-xs text-muted-foreground">
              How many days before expiry the partner is notified
            </p>
            {errors.leadTimeDays && (
              <p
                role="alert"
                className="text-xs text-destructive mt-1"
              >
                {errors.leadTimeDays.message}
              </p>
            )}
          </div>

          {/* Save / Cancel */}
          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => reset()}
              disabled={!isDirty || isPending}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={!isDirty || !isValid || isPending}
            >
              {isPending && (
                <Loader2
                  aria-hidden="true"
                  className="mr-2 h-4 w-4 animate-spin motion-reduce:animate-none"
                />
              )}
              Save
            </Button>
          </div>
        </CardContent>
      </form>
    </Card>
  );
}
