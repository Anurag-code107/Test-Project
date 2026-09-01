/**
 * How a distribution reaches its recipient.
 *
 * `WALLET_CREDIT` is retired — the store no longer offers it and the API refuses new distributions on it.
 * The member stays because existing distributions carry that rail and history still renders them.
 */
export type DistributionRail = "GIFT_CARD" | "BANK_TRANSFER" | "WALLET_CREDIT";

/** Per-recipient status. `RESERVED` means earmarked on the company wallet but not yet settled. */
export type DistributionItemStatus =
  | "RESERVED"
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

/**
 * Rolled up from the recipient rows. `PARTIALLY_COMPLETED` is expected, not exceptional — some
 * recipients paid, others released back to the company wallet.
 */
export type DistributionRollupStatus =
  | "PROCESSING"
  | "COMPLETED"
  | "PARTIALLY_COMPLETED"
  | "FAILED";

export interface DistributionRecipient {
  userId: string;
  fullName: string;
  email: string;
  /** Whether the currently-selected rail can actually reach this person. */
  eligible: boolean;
  /** Null when eligible. Otherwise phrased for the admin — show it, don't hide the row. */
  ineligibleReason: string | null;
  /** Gift-card email, masked bank label, or "Cash wallet". */
  destination: string | null;
}

/**
 * One gift-card SKU from the provider's catalogue — the full list a partner admin picks from, rather than
 * only the cards a client admin has curated.
 */
export interface DistributionGiftCardSku {
  sku: string;
  rewardName: string;
  brandName: string | null;
  brandImageUrl: string | null;
  currencyCode: string | null;
  valueType: "FIXED" | "VARIABLE" | null;
  /** Set for `FIXED`; the denomination is pinned to it. */
  faceValue: string | null;
  minValue: string | null;
  maxValue: string | null;
}

export interface DistributionCatalogItem {
  id: string;
  name: string;
  description: string | null;
  /** Uploaded image, already proxied by the API. */
  imageUrl: string | null;
  /** Vendor brand image, used when there is no uploaded one. */
  providerImageUrl: string | null;
  currencyId: string;
  /** `FIXED` means min === max: the denomination is pinned and the amount field is read-only. */
  valueType: "FIXED" | "VARIABLE" | null;
  minAmount: string;
  /** Null means open-value — no ceiling. */
  maxAmount: string | null;
}

export interface CompanyDistributionItem {
  itemId: string;
  recipientUserId: string;
  recipientName: string | null;
  recipientEmail: string | null;
  amount: string;
  status: DistributionItemStatus;
  destination: string | null;
  /** Payout rails only, and only once COMPLETED. */
  paymentTransactionId: string | null;
  failureReason: string | null;
  settledAt: string | null;
}

export interface CompanyDistribution {
  id: string;
  rail: DistributionRail;
  railDisplayName: string;
  catalogItemId: string | null;
  catalogItemName: string | null;
  currencyId: string;
  amountPerRecipient: string;
  recipientCount: number;
  /** What was submitted. */
  requestedTotal: string;
  /**
   * What actually left the wallet — lower than `requestedTotal` after a partial failure. Always show
   * both; showing only the requested figure makes a half-failed distribution read as fully paid.
   */
  settledTotal: string;
  status: DistributionRollupStatus;
  initiatedByUserId: string;
  initiatedByName: string | null;
  note: string | null;
  createdAt: string;
  /** Empty in the list response; populated on the detail endpoint. */
  items: CompanyDistributionItem[];
}

/** One amount for every recipient — the admin types 50 once and each selected seller receives 50. */
export interface CreateCompanyDistributionRequest {
  rail: DistributionRail;
  sourceWalletId: string;
  /** Legacy curated-catalog path. Prefer `providerSku`; never send both. */
  catalogItemId?: string | null;
  /** The provider gift-card SKU. The preferred way to choose a card for `GIFT_CARD`. */
  providerSku?: string | null;
  amount: string;
  userIds: string[];
  note?: string | null;
  clientIdempotencyKey?: string | null;
}

/** The seller-side view of a distribution item. */
export interface CompanyAward {
  awardId: string;
  receivedAt: string;
  rail: DistributionRail;
  railDisplayName: string;
  /** Gift-card name, or the rail's label for bank/wallet transfers. */
  rewardName: string | null;
  amount: string;
  currencyId: string | null;
  status: DistributionItemStatus;
  destination: string | null;
  awardedByName: string | null;
  companyName: string | null;
  note: string | null;
  failureReason: string | null;
  paymentTransactionId: string | null;
}

export interface FundCompanyWalletRequest {
  currencyId: string;
  amount: string;
  /** Required — this is the idempotency key, so a double-submit credits once. */
  reference: string;
  note?: string | null;
}
