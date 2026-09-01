import api from "@/lib/axios";
import type { ApiResponse, PaginatedResponse } from "@/types/api.types";
import type {
  RedemptionCatalogItemResponse,
  RedemptionCatalogItemDetailResponse,
  CreateRedemptionCatalogItemRequest,
  UpdateRedemptionCatalogItemRequest,
  GlobalCatalogItemFilters,
  SyncJobResponse,
  IntegrationHealthResponse,
  GiftCardSkuResponse,
} from "@/types/redemption-catalog.types";

const BASE = "/admin/redemption-catalog";

/**
 * List the XTRM digital gift-card SKUs available for the catalog-creation picker.
 * Server-side cached (6h) and filtered to Active USD gift cards.
 */
export async function getGiftCardCatalog(): Promise<GiftCardSkuResponse[]> {
  const response = await api.get<ApiResponse<GiftCardSkuResponse[]>>(
    `${BASE}/gift-card-catalog`,
  );
  return response.data.data;
}

export async function getGlobalCatalogItems(
  filters?: GlobalCatalogItemFilters,
): Promise<PaginatedResponse<RedemptionCatalogItemResponse>> {
  const response = await api.get<ApiResponse<PaginatedResponse<RedemptionCatalogItemResponse>>>(
    BASE,
    { params: filters },
  );
  return response.data.data;
}

export async function getCatalogItemDetail(
  id: string,
): Promise<RedemptionCatalogItemDetailResponse> {
  const response = await api.get<ApiResponse<RedemptionCatalogItemDetailResponse>>(
    `${BASE}/${id}`,
  );
  return response.data.data;
}

export async function createCatalogItem(
  request: CreateRedemptionCatalogItemRequest,
): Promise<RedemptionCatalogItemDetailResponse> {
  const response = await api.post<ApiResponse<RedemptionCatalogItemDetailResponse>>(
    BASE,
    request,
  );
  return response.data.data;
}

export async function updateCatalogItem(
  id: string,
  request: UpdateRedemptionCatalogItemRequest,
): Promise<RedemptionCatalogItemDetailResponse> {
  const response = await api.put<ApiResponse<RedemptionCatalogItemDetailResponse>>(
    `${BASE}/${id}`,
    request,
  );
  return response.data.data;
}

export async function activateCatalogItem(
  id: string,
): Promise<RedemptionCatalogItemResponse> {
  const response = await api.patch<ApiResponse<RedemptionCatalogItemResponse>>(
    `${BASE}/${id}/activate`,
  );
  return response.data.data;
}

export async function deactivateCatalogItem(
  id: string,
): Promise<RedemptionCatalogItemResponse> {
  const response = await api.patch<ApiResponse<RedemptionCatalogItemResponse>>(
    `${BASE}/${id}/deactivate`,
  );
  return response.data.data;
}

/** Soft-delete a catalog item (owner-scoped on the server; history is preserved). Returns 204. */
export async function deleteCatalogItem(id: string): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}

export async function triggerCatalogSync(): Promise<SyncJobResponse> {
  const response = await api.post<ApiResponse<SyncJobResponse>>(`${BASE}/sync`);
  return response.data.data;
}

export async function getIntegrationHealth(): Promise<IntegrationHealthResponse> {
  const response = await api.get<ApiResponse<IntegrationHealthResponse>>(
    `${BASE}/integration-health`,
  );
  return response.data.data;
}

export async function uploadCatalogItemImage(
  id: string,
  file: File,
): Promise<RedemptionCatalogItemResponse> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await api.post<ApiResponse<RedemptionCatalogItemResponse>>(
    `${BASE}/${id}/image`,
    formData,
    { headers: { "Content-Type": undefined } },
  );
  return response.data.data;
}
