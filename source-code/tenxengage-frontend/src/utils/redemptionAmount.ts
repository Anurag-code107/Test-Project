import { getCurrency } from "@/config/currencies";

export interface RedemptionAmountRules {
  /**
   * Raw input value exactly as typed — may be "", "abc" or "-5". Accepts a number too: the API
   * serializes these BigDecimal fields as JSON numbers despite the contract declaring decimal
   * strings, so a pre-filled amount can arrive un-stringified. Never call string methods on it
   * without coercing.
   */
  amount: string | number;
  currencyId: string;
  /** Inclusive floor: the item's effective min (or the bank-transfer card's $1). */
  min?: string | number | null;
  /** Inclusive ceiling: the item's effective max. Null for open-value/legacy items. */
  max?: string | number | null;
  /** Wallet ceiling — a user can never redeem more than they actually hold. */
  availableBalance?: string | number | null;
}

/**
 * Client-side mirror of the server's amount rules (RedemptionSubmissionService):
 * positive → inside [effectiveMin, effectiveMax] → inside the wallet's available balance.
 * Returns the message to render inline, or null when the amount is submittable.
 *
 * The server stays authoritative; this exists so an out-of-range amount produces a
 * field-level message on the offending input instead of a round-trip and a generic error.
 */
export function validateRedemptionAmount({
  amount,
  currencyId,
  min,
  max,
  availableBalance,
}: RedemptionAmountRules): string | null {
  const fmt = getCurrency(currencyId).rewardFormat;

  const raw = String(amount ?? "").trim();
  if (raw === "") return "Enter an amount.";

  const value = Number(raw);
  if (!Number.isFinite(value)) return "Enter a valid amount.";
  if (value <= 0) return "Amount must be greater than 0.";

  if (min != null && value < Number(min)) {
    return `Amount must be at least ${fmt(min)}.`;
  }
  if (max != null && value > Number(max)) {
    return `Amount must be at most ${fmt(max)}.`;
  }
  // Balance last: a range violation is the more actionable message when both apply.
  if (availableBalance != null && value > Number(availableBalance)) {
    return `Amount exceeds your available balance of ${fmt(availableBalance)}.`;
  }

  return null;
}
