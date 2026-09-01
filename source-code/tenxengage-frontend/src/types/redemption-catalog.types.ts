export type CatalogCategory = "CASH" | "NON_CASH";
export type ProcessingMode = "INSTANT" | "BATCH" | "APPROVAL_REQUIRED";

/**
 * How a catalog item's redemption amount is bounded, derived from the XTRM gift-card SKU:
 * - `FIXED`    — a single denomination; the amount is locked to the SKU's face value.
 * - `VARIABLE` — any amount within the SKU's [min, max] range.
 * `null`/absent on legacy items created before SKU-driven value types existed.
 */
export type RedemptionValueType = "FIXED" | "VARIABLE";

/**
 * One selectable XTRM digital gift-card SKU for the catalog-creation picker.
 * BigDecimal amounts serialize as JSON numbers at runtime. For FIXED the amount is `faceValue`;
 * for VARIABLE it is any value within [`minValue`, `maxValue`].
 */
export interface GiftCardSkuResponse {
  sku: string;
  rewardName: string;
  brandName: string;
  brandImageUrl: string | null;
  currencyCode: string;
  valueType: RedemptionValueType;
  faceValue: number | null;
  minValue: number | null;
  maxValue: number | null;
}

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
  imageUrl?: string | null;
  /**
   * Brand image URL from the item's gift-card SKU, stamped server-side at create time. Shown on the
   * card when no image was uploaded; null for NON_CASH, unknown SKUs and pre-existing items.
   */
  providerImageUrl?: string | null;
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
  imageUrl?: string;
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
  imageUrl?: string | null;
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
  // Overrides may only NARROW the item's range: min ≥ defaultMinRedemptionAmount,
  // max ≤ defaultMaxRedemptionAmount. Omit either to inherit the item default.
  minTransactionAmountOverride?: string;
  maxTransactionAmountOverride?: string;
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
  maxTransactionAmountOverride?: string;
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
  /** Platform ceiling the client's override may not exceed. Null on open-value/legacy items. */
  defaultMaxRedemptionAmount?: string | null;
  defaultProcessingMode: ProcessingMode;
  geographicScope: string[];
  isReturnable: boolean;
  defaultReturnWindowDays: number;
  isGloballyActive: boolean;
  configId: string | null;
  enabled: boolean;
  processingModeOverride?: ProcessingMode;
  minTransactionAmountOverride?: string;
  maxTransactionAmountOverride?: string;
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
  imageUrl?: string | null;
  /** Vendor brand image from the item's gift-card SKU — the card's fallback when `imageUrl` is absent. */
  providerImageUrl?: string | null;
  category: CatalogCategory;
  currencyId: string;
  effectiveMinTransactionAmount: string;
  // SKU-driven value type + upper bound. `valueType` null on legacy items; `effectiveMaxTransactionAmount`
  // is set for FIXED (== min) and VARIABLE, null when unbounded.
  valueType?: RedemptionValueType | null;
  effectiveMaxTransactionAmount?: string | null;
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
