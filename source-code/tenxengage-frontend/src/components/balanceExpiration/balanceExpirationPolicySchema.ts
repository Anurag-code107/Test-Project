import { z } from "zod";

/**
 * Zod schema for BalanceExpirationPolicyForm.
 * Mirrors service-layer validation rules (FR-09.9) so the FE catches errors
 * before the server roundtrip.
 *
 * CONTRACT NOTES (contracts/endpoints/balance-expiration.yaml):
 * - enabled, expirationMode, leadTimeDays are required.
 * - inactivityDays: nullable, required when INACTIVITY, bounds [30, 1825].
 * - fixedExpiryDate: nullable, required when FIXED_DATE, must be in the future.
 * - leadTimeDays: >= 1; < inactivityDays when INACTIVITY.
 */
export const balanceExpirationPolicySchema = z
  .object({
    enabled: z.boolean(),
    expirationMode: z.enum(["INACTIVITY", "FIXED_DATE"]),
    // Use z.preprocess to treat empty string as undefined so coerce doesn't silently give 0
    inactivityDays: z.preprocess(
      (v) => (v === "" || v == null ? undefined : v),
      z.coerce
        .number()
        .int()
        .min(30, "Inactivity period must be between 30 and 1825 days")
        .max(1825, "Inactivity period must be between 30 and 1825 days")
        .optional(),
    ),
    fixedExpiryDate: z.string().nullable().optional(),
    leadTimeDays: z.preprocess(
      (v) => (v === "" || v == null ? undefined : v),
      z.coerce
        .number()
        .int()
        .min(1, "Lead time must be at least 1 day and less than the inactivity period"),
    ),
  })
  .superRefine((data, ctx) => {
    if (!data.enabled) return; // disabled policies skip cross-field checks

    if (data.expirationMode === "INACTIVITY") {
      if (data.inactivityDays == null) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["inactivityDays"],
          message: "Inactivity period is required for this mode",
        });
      } else if (data.leadTimeDays != null && data.leadTimeDays >= data.inactivityDays) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["leadTimeDays"],
          message:
            "Lead time must be at least 1 day and less than the inactivity period",
        });
      }
      if (data.fixedExpiryDate) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["fixedExpiryDate"],
          message: "Fixed expiry date must be empty for inactivity mode",
        });
      }
    }

    if (data.expirationMode === "FIXED_DATE") {
      if (!data.fixedExpiryDate) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["fixedExpiryDate"],
          message: "Fixed expiry date is required for this mode",
        });
      } else {
        // Must be a future date. Parse YYYY-MM-DD into LOCAL calendar fields —
        // new Date("YYYY-MM-DD") parses as UTC midnight, which mis-compares against
        // local `today` for users east of UTC (PROJECT-CONTEXT date-only anti-pattern).
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        const [fy, fm, fd] = data.fixedExpiryDate.split("-").map(Number);
        const selectedDate = new Date(fy ?? 2000, (fm ?? 1) - 1, fd ?? 1);
        if (selectedDate <= today) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            path: ["fixedExpiryDate"],
            message: "Fixed expiry date must be in the future",
          });
        }
      }
    }
  });

export type BalanceExpirationPolicyFormValues = z.infer<
  typeof balanceExpirationPolicySchema
>;
