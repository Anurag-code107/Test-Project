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
