import type { DistributionRail } from "@/types/company-distribution.types";
import { XTRM_PAYOUT_RAILS_ENABLED } from "@/config/redemptionFeatures";

/**
 * The rails a partner admin can distribute on.
 *
 * Lives apart from the store page so it can be reasoned about — and tested — without pulling in the page's
 * eighteen imports. The wallet rail was retired on 2026-08-26: existing distributions on it still render in
 * history, but it is no longer offered and the API refuses new ones.
 */
export const RAILS: { value: DistributionRail; label: string; needsXtrm: boolean }[] = [
  { value: "GIFT_CARD", label: "Gift Card", needsXtrm: true },
  { value: "BANK_TRANSFER", label: "Bank Transfer", needsXtrm: true },
];

/**
 * Whether a rail can currently be *sent*. Deliberately not whether it can be opened: the tabs stay browsable
 * so a partner admin can see the gift-card picker and their sellers' payout readiness even when sending is
 * withheld. Only the send button is blocked.
 */
export const railSendBlocked = (r: DistributionRail) =>
  RAILS.some((x) => x.value === r && x.needsXtrm) && !XTRM_PAYOUT_RAILS_ENABLED;

/**
 * Land on a rail that can actually be sent, so the default path is a working one.
 *
 * Both remaining rails need XTRM, so when the payout rails are switched off there is no sendable rail at
 * all. Falling back to a rail that IS offered is the point — the page must never name one it does not list,
 * which is what the old `?? "WALLET_CREDIT"` would now do. Named rather than `RAILS[0]` so the fallback is
 * a rail this module can be seen to offer, instead of whichever one happens to be first.
 */
export const DEFAULT_RAIL: DistributionRail =
  RAILS.find((r) => !railSendBlocked(r.value))?.value ?? "GIFT_CARD";
