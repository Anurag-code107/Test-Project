// shape: contracts/models/redemption-return.md + contracts/endpoints/redemption-returns.yaml
// Copied from contracts — do NOT hand-write or modify

export type ReturnStatus =
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'RETURN_CONFIRMED'
  | 'RETURN_REJECTED'
  | 'CANCELLED'
  | 'RETURN_TIMED_OUT';

export type ReturnResolution = 'CONFIRM' | 'REJECT';

// ── Request types ────────────────────────────────────────────────────────────

export interface SubmitReturnRequest {
  redemptionId: string;
  reason?: string;
}

export interface RejectReturnRequest {
  rejectionReason: string;
}

export interface ResolveTimedOutReturnRequest {
  resolution: ReturnResolution;
  notes?: string;
}

// ── Response types ───────────────────────────────────────────────────────────

// shape: contracts/models/redemption-return.md (ReturnSummaryResponse)
export interface ReturnSummaryResponse {
  id: string;
  redemptionId: string;
  catalogItemName: string;
  amount: string;
  currencyId: string;
  status: ReturnStatus;
  reason?: string;
  resolvedAt?: string;
  createdAt: string;
  updatedAt: string;
}

// shape: contracts/models/redemption-return.md (ReturnDetailResponse)
export interface ReturnDetailResponse {
  id: string;
  redemptionId: string;
  catalogItemName: string;
  partnerDisplayName: string;
  amount: string;
  currencyId: string;
  status: ReturnStatus;
  reason?: string;
  reviewedAt?: string;
  reviewNotes?: string;
  vendorReturnReference?: string;
  approvedAt?: string;
  timedOutAt?: string;
  confirmedAt?: string;
  rejectedAt?: string;
  cancelledAt?: string;
  createdAt: string;
  updatedAt: string;
}

// shape: contracts/endpoints/redemption-returns.yaml (ReturnQueueItemResponse)
export interface ReturnQueueItemResponse {
  id: string;
  catalogItemName: string;
  partnerDisplayName: string;
  partnerCompanyName: string;
  amount: string;
  currencyId: string;
  status: ReturnStatus;
  reason?: string;
  createdAt: string;
  updatedAt: string;
}

// ── Filter / param types ─────────────────────────────────────────────────────

export interface MyReturnsFilters {
  status?: ReturnStatus;
  page?: number;
  size?: number;
  sortBy?: 'createdAt' | 'amount';
  sortDirection?: 'ASC' | 'DESC';
}

export interface AdminReturnsFilters {
  status?: ReturnStatus;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
  sortBy?: 'createdAt' | 'amount';
  sortDirection?: 'ASC' | 'DESC';
}
