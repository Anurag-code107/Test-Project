export type RedemptionRequestType = 'REDEMPTION' | 'RETURN';

export type ApprovalQueueWalletType = 'INDIVIDUAL' | 'COMPANY';

export interface ApprovalQueueItem {
  id: string;
  requestingUserDisplayName: string;
  catalogItemId: string;
  catalogItemName: string;
  currencyId: string;
  amount: string;
  walletType: ApprovalQueueWalletType;
  submittedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface ApprovalQueueFilters {
  startDate?: string;
  endDate?: string;
  currencyId?: string;
  catalogItemId?: string;
  requestType?: RedemptionRequestType;
  page?: number;
  size?: number;
}

export interface PaginationMeta {
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface RejectRedemptionRequest {
  rejectionReason: string;
}

export interface RedemptionRequestDetailResponse {
  id: string;
  status: string;
  amount: string;
  currencyId: string;
  catalogItemId: string;
  catalogItemName: string;
  processingMode: string;
  category: string;
  walletType: ApprovalQueueWalletType;
  vendorReferenceId?: string;
  submittedAt: string;
  scheduledBatchDate?: string;
  completedAt?: string;
  failureReason?: string;
  estimatedDelivery: string;
  reviewedBy?: string;
  reviewedAt?: string;
  rejectionReason?: string;
  createdAt: string;
  updatedAt: string;
}
