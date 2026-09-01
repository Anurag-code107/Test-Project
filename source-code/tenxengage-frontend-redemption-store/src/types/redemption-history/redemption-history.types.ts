export type RedemptionStatus =
  | 'PENDING_APPROVAL'
  | 'RESERVED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export type RedemptionCategory = 'CASH' | 'NON_CASH';

export type RedemptionProcessingMode = 'INSTANT' | 'BATCH' | 'APPROVAL_REQUIRED';

export type RedemptionWalletType = 'INDIVIDUAL' | 'COMPANY';

export type RedemptionSortBy = 'submittedAt' | 'amount' | 'status';

export interface RedemptionRequestResponse {
  id: string;
  status: RedemptionStatus;
  amount: string;
  currencyId: string;
  catalogItemId: string;
  catalogItemName: string;
  /** CASH vs NON_CASH — drives the Actions cell: CASH → "N/A", NON_CASH → Request Return. */
  category?: RedemptionCategory;
  processingMode: RedemptionProcessingMode;
  submittedAt: string;
  completedAt?: string;
  scheduledBatchDate?: string;
  estimatedDelivery: string;
  /** F-06: true when eligible for a non-cash return. Drives "Request Return" CTA visibility. */
  isReturnEligible?: boolean;
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
  linkedReturnId?: string;
  reviewedBy?: string;
  /** Reviewer's display name (resolved server-side); falls back to reviewedBy id when absent. */
  reviewedByName?: string;
  reviewedAt?: string;
  rejectionReason?: string;
  createdAt: string;
  updatedAt: string;
}

export type ExportFormat = 'CSV' | 'XLSX';
export type ExportJobStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type ExportJobScope = 'PERSONAL' | 'COMPANY' | 'ALL_TENANT';

export interface RedemptionAdminHistoryResponse {
  id: string;
  status: RedemptionStatus;
  amount: string;
  currencyId: string;
  catalogItemId: string;
  catalogItemName: string;
  processingMode: RedemptionProcessingMode;
  userId: string;
  userDisplayName: string;
  partnerCompanyId?: string;
  partnerCompanyName?: string;
  submittedAt: string;
  completedAt?: string;
  scheduledBatchDate?: string;
  estimatedDelivery: string;
  createdAt: string;
  updatedAt: string;
}

export interface RedemptionAdminHistoryFilters {
  dateFrom?: string;
  dateTo?: string;
  status?: RedemptionStatus;
  category?: RedemptionCategory;
  userId?: string;
  companyId?: string;
  userName?: string;
  companyName?: string;
  sortBy?: RedemptionSortBy;
  sortDirection?: 'ASC' | 'DESC';
}

export interface TriggerExportRequest {
  format: ExportFormat;
  scope?: ExportJobScope;
  dateFrom?: string;
  dateTo?: string;
  status?: RedemptionStatus;
  category?: RedemptionCategory;
  userId?: string;
  companyId?: string;
  userName?: string;
  companyName?: string;
}

export interface RedemptionExportJobResponse {
  id: string;
  status: ExportJobStatus;
  format: ExportFormat;
  scope: ExportJobScope;
  rowCount?: number;
  expiresAt?: string;
  failureReason?: string;
  createdAt: string;
  updatedAt: string;
}

export interface RedemptionExportJobDetailResponse extends RedemptionExportJobResponse {
  downloadUrl?: string;
}

export type ExportTriggerResult =
  | { kind: 'sync'; blob: Blob; filename: string }
  | { kind: 'async'; job: RedemptionExportJobResponse };

export interface RedemptionHistoryFilters {
  dateFrom?: string;
  dateTo?: string;
  status?: RedemptionStatus;
  currencyId?: string;
  category?: RedemptionCategory;
  sortBy?: RedemptionSortBy;
  sortDirection?: 'ASC' | 'DESC';
}
