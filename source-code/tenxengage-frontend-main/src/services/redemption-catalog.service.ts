import api from "@/lib/axios";
import type { ApiResponse, PaginatedResponse } from "@/types/api.types";
import type {
  TenantCatalogItemResponse,
  TenantCatalogConfigFilters,
  UpsertClientCatalogItemConfigRequest,
  ClientCatalogItemConfigResponse,
  TenantRedemptionSettingsResponse,
  UpdateTenantRedemptionSettingsRequest,
  UpsertRegionConfigRequest,
  ClientCatalogRegionConfigResponse,
  CatalogBrowseItemResponse,
  CatalogBrowseFilters,
} from "@/types/redemption-catalog.types";

const CATALOG_CONFIG_BASE = "/redemption/catalog/config";
const SETTINGS_BASE = "/redemption/settings";

export async function getTenantCatalogConfig(
  filters?: TenantCatalogConfigFilters,
): Promise<PaginatedResponse<TenantCatalogItemResponse>> {
  const response = await api.get<ApiResponse<PaginatedResponse<TenantCatalogItemResponse>>>(
    CATALOG_CONFIG_BASE,
    { params: filters },
  );
  return response.data.data;
}

export async function upsertItemConfig(
  catalogItemId: string,
  request: UpsertClientCatalogItemConfigRequest,
): Promise<ClientCatalogItemConfigResponse> {
  const response = await api.put<ApiResponse<ClientCatalogItemConfigResponse>>(
    `${CATALOG_CONFIG_BASE}/${catalogItemId}`,
    request,
  );
  return response.data.data;
}

export async function getTenantRedemptionSettings(): Promise<TenantRedemptionSettingsResponse> {
  const response = await api.get<ApiResponse<TenantRedemptionSettingsResponse>>(SETTINGS_BASE);
  return response.data.data;
}

export async function updateTenantRedemptionSettings(
  request: UpdateTenantRedemptionSettingsRequest,
): Promise<TenantRedemptionSettingsResponse> {
  const response = await api.put<ApiResponse<TenantRedemptionSettingsResponse>>(
    SETTINGS_BASE,
    request,
  );
  return response.data.data;
}

export async function getRegionalConfigs(
  catalogItemId: string,
): Promise<ClientCatalogRegionConfigResponse[]> {
  const response = await api.get<ApiResponse<ClientCatalogRegionConfigResponse[]>>(
    `${CATALOG_CONFIG_BASE}/${catalogItemId}/regions`,
  );
  return response.data.data;
}

export async function upsertRegionConfig(
  catalogItemId: string,
  regionCode: string,
  request: UpsertRegionConfigRequest,
): Promise<ClientCatalogRegionConfigResponse> {
  const response = await api.put<ApiResponse<ClientCatalogRegionConfigResponse>>(
    `${CATALOG_CONFIG_BASE}/${catalogItemId}/regions/${regionCode}`,
    request,
  );
  return response.data.data;
}

export async function deleteRegionConfig(
  catalogItemId: string,
  regionCode: string,
): Promise<void> {
  await api.delete(`${CATALOG_CONFIG_BASE}/${catalogItemId}/regions/${regionCode}`);
}

const PARTNER_CATALOG_BASE = "/redemption/catalog";

export async function getPartnerCatalog(
  filters?: CatalogBrowseFilters,
): Promise<PaginatedResponse<CatalogBrowseItemResponse>> {
  const response = await api.get<ApiResponse<PaginatedResponse<CatalogBrowseItemResponse>>>(
    PARTNER_CATALOG_BASE,
    { params: filters },
  );
  return response.data.data;
}

export async function getPartnerCatalogItem(id: string): Promise<CatalogBrowseItemResponse> {
  const response = await api.get<ApiResponse<CatalogBrowseItemResponse>>(
    `${PARTNER_CATALOG_BASE}/${id}`,
  );
  return response.data.data;
}
