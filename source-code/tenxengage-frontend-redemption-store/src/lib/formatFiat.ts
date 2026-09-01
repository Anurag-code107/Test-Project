/**
 * Format a fiat amount with the currency's narrow symbol, e.g. `formatFiat(90, "USD")` → "$90.00",
 * `formatFiat(0, "INR")` → "₹0.00", `formatFiat(0, "EUR")` → "€0.00".
 *
 * The app's `config/currencies.ts` covers REWARD currencies (cash/points/credits/tickets), NOT ISO fiat —
 * so XTRM wallet balances (USD/INR/EUR/…) use this. We use `currencyDisplay: "narrowSymbol"` so USD renders
 * as "$" (not "US$"); the ISO code is intentionally NOT appended — the wallet name already carries the
 * currency. Falls back to a plain number + code for an unknown/blank ISO code (where Intl throws).
 */
export function formatFiat(amount: number, isoCode: string): string {
  const code = (isoCode ?? "").toUpperCase();
  try {
    return new Intl.NumberFormat(undefined, {
      style: "currency",
      currency: code,
      currencyDisplay: "narrowSymbol",
    }).format(amount);
  } catch {
    // Unknown/blank ISO code → Intl throws; degrade to a plain 2-dp number + code.
    return `${amount.toFixed(2)} ${code}`.trim();
  }
}
