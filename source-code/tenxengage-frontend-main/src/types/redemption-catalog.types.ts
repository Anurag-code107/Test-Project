export type CatalogCategory = "CASH" | "NON_CASH";
export type ProcessingMode = "INSTANT" | "BATCH" | "APPROVAL_REQUIRED";

export interface RedemptionCatalogItemResponse {
  id: string;
  name: string;
  description?: string;
  category: CatalogCategory;
  currencyId: string;
  defaultMinRedemptionAmount: string;
  defaultProcessingMode: ProcessingMode;
  geographicScope: string[];
  providerItemId?: string;
  isReturnable: boolean;
  defaultReturnWindowDays: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RedemptionCatalogItemDetailResponse extends RedemptionCatalogItemResponse {
  xoxodayLastSyncedAt?: string;
}

export interface CreateRedemptionCatalogItemRequest {
  name: string;
  description?: string;
  category: CatalogCategory;
  currencyId: string;
  defaultMinRedemptionAmount: string;
  defaultProcessingMode?: ProcessingMode;
  geographicScope?: string[];
  providerItemId?: string;
  isReturnable?: boolean;
  defaultReturnWindowDays?: number;
}

export interface UpdateRedemptionCatalogItemRequest {
  name?: string;
  description?: string;
  currencyId?: string;
  defaultMinRedemptionAmount?: string;
  defaultProcessingMode?: ProcessingMode;
  geographicScope?: string[];
  providerItemId?: string;
  isReturnable?: boolean;
  defaultReturnWindowDays?: number;
}

export interface GlobalCatalogItemFilters {
  page?: number;
  pageSize?: number;
  category?: CatalogCategory;
  isActive?: boolean;
  search?: string;
}

export type BatchCadence = "DAILY" | "WEEKLY";

export interface UpsertClientCatalogItemConfigRequest {
  enabled: boolean;
  processingModeOverride?: ProcessingMode;
  minTransactionAmountOverride?: string;
  minWalletBalanceOverride?: string;
  returnWindowDaysOverride?: number;
}

export interface UpdateTenantRedemptionSettingsRequest {
  batchCadence: BatchCadence;
  maxInFlightRedemptions?: number;
}

export interface ClientCatalogItemConfigResponse {
  id: string;
  redemptionCatalogItemId: string;
  enabled: boolean;
  processingModeOverride?: ProcessingMode;
  minTransactionAmountOverride?: string;
  minWalletBalanceOverride?: string;
  returnWindowDaysOverride?: number;
  createdAt: string;
  updatedAt: string;
}

export interface TenantCatalogItemResponse {
  id: string;
  name: string;
  description?: string;
  category: CatalogCategory;
  currencyId: string;
  defaultMinRedemptionAmount: string;
  defaultProcessingMode: ProcessingMode;
  geographicScope: string[];
  isReturnable: boolean;
  defaultReturnWindowDays: number;
  isGloballyActive: boolean;
  configId: string | null;
  enabled: boolean;
  processingModeOverride?: ProcessingMode;
  minTransactionAmountOverride?: string;
  minWalletBalanceOverride?: string;
  returnWindowDaysOverride?: number;
  createdAt: string;
  updatedAt: string;
}

export interface TenantRedemptionSettingsResponse {
  id: string;
  batchCadence: BatchCadence;
  maxInFlightRedemptions: number;
  createdAt: string;
  updatedAt: string;
}

export interface TenantCatalogConfigFilters {
  page?: number;
  pageSize?: number;
  enabled?: boolean;
  category?: CatalogCategory;
  search?: string;
}

export interface CatalogBrowseItemResponse {
  id: string;
  name: string;
  description?: string;
  category: CatalogCategory;
  currencyId: string;
  effectiveMinTransactionAmount: string;
  effectiveProcessingMode: ProcessingMode;
  estimatedPayoutTimeline: string;
  canAfford: boolean;
  shortfallAmount: string;
  geographicScope: string[];
  isReturnable: boolean;
  effectiveReturnWindowDays: number;
  createdAt: string;
  updatedAt: string;
}

export interface CatalogBrowseFilters {
  currencyId?: string;
  region?: string;
  page?: number;
  pageSize?: number;
}

export interface UpsertRegionConfigRequest {
  enabled: boolean;
}

export interface ClientCatalogRegionConfigResponse {
  id: string;
  redemptionCatalogItemId: string;
  regionCode: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export type SyncJobStatus = "QUEUED" | "IN_PROGRESS" | "COMPLETED" | "FAILED";
export type IntegrationSyncStatus = "SUCCESS" | "FAILED" | "IN_PROGRESS" | "NEVER_SYNCED";
export type WebhookDeliveryStatus = "DELIVERED" | "FAILED" | "PENDING";

export interface SyncJobResponse {
  jobId: string;
  status: SyncJobStatus;
}

export interface WebhookLogEntry {
  id: string;
  vendor: string;
  eventType: string;
  status: WebhookDeliveryStatus;
  receivedAt: string;
}

export interface IntegrationHealthResponse {
  syncStatus: IntegrationSyncStatus;
  lastSyncAt: string | null;
  failedSyncCount: number;
  recentWebhooks: WebhookLogEntry[];
}
