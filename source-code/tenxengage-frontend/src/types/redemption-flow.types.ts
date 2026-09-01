export type RedemptionStatus =
  | "PENDING_APPROVAL"
  | "RESERVED"
  | "PROCESSING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

export type RedemptionProcessingMode = "INSTANT" | "BATCH" | "APPROVAL_REQUIRED";

export type RedemptionCategory = "CASH" | "NON_CASH";

export type RedemptionWalletType = "INDIVIDUAL" | "COMPANY";

export interface SubmitPersonalRedemptionRequest {
  catalogItemId: string;
  amount: string;
  currencyId: string;
}

export interface SubmitCompanyRedemptionRequest {
  catalogItemId: string;
  amount: string;
  currencyId: string;
  companyId: string;
}

/**
 * Bank-transfer redemption — pay `amount` from the caller's cash reward wallet into their default
 * linked bank. The catalog item (the reserved per-client bank-transfer card) and currency are
 * resolved server-side, so the client sends only the funding wallet + amount.
 */
export interface SubmitBankTransferRedemptionRequest {
  walletId: string;
  amount: string;
  /** Which linked bank to pay (our bank id). Omit to pay the user's default bank. */
  bankId?: string;
  clientIdempotencyKey?: string;
}

export interface RedemptionSubmissionConfirmationResponse {
  id: string;
  status: RedemptionStatus;
  amount: string;
  currencyId: string;
  processingMode: RedemptionProcessingMode;
  estimatedDelivery: string;
  scheduledBatchDate?: string;
  submittedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface RedemptionRequestResponse {
  id: string;
  status: RedemptionStatus;
  amount: string;
  currencyId: string;
  catalogItemId: string;
  processingMode: RedemptionProcessingMode;
  submittedAt: string;
  scheduledBatchDate?: string;
  estimatedDelivery: string;
  createdAt: string;
  updatedAt: string;
}

export interface RedemptionRequestDetailResponse {
  id: string;
  status: RedemptionStatus;
  amount: string;
  currencyId: string;
  catalogItemId: string;
  catalogItemName: string;
  imageUrl?: string | null;
  /** Vendor brand image from the item's gift-card SKU — fallback when no image was uploaded. */
  providerImageUrl?: string | null;
  processingMode: RedemptionProcessingMode;
  category: RedemptionCategory;
  walletType: RedemptionWalletType;
  vendorReferenceId?: string;
  submittedAt: string;
  scheduledBatchDate?: string;
  completedAt?: string;
  failureReason?: string;
  estimatedDelivery: string;
  createdAt: string;
  updatedAt: string;
}

export interface RedemptionRequestListParams {
  page?: number;
  pageSize?: number;
  status?: RedemptionStatus;
  currencyId?: string;
  sortBy?: "submittedAt" | "amount" | "status";
  sortDirection?: "ASC" | "DESC";
}
