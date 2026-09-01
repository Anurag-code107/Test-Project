import { format, parseISO } from "date-fns";

export function formatCurrency(
  amount: number,
  currency: string = "USD",
): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(amount);
}

export function formatDate(date: string | Date): string {
  const parsed = typeof date === "string" ? parseISO(date) : date;
  return format(parsed, "MMM d, yyyy");
}

export function formatDateTime(date: string | Date): string {
  const parsed = typeof date === "string" ? parseISO(date) : date;
  return format(parsed, "MMM d, yyyy h:mm a");
}

export function formatNumber(num: number): string {
  return new Intl.NumberFormat("en-US").format(num);
}
