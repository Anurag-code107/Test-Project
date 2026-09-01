import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type {
  GlobalCatalogItemFilters,
  TenantCatalogConfigFilters,
  UpsertClientCatalogItemConfigRequest,
  UpdateTenantRedemptionSettingsRequest,
  UpsertRegionConfigRequest,
  CatalogBrowseFilters,
} from "@/types/redemption-catalog.types";
import * as catalogService from "@/services/redemption-catalog-admin.service";
import type { SyncJobResponse } from "@/types/redemption-catalog.types";
import * as tenantCatalogService from "@/services/redemption-catalog.service";

const QUERY_KEY = "global-catalog";

export function useGlobalCatalogItems(filters?: GlobalCatalogItemFilters) {
  return useQuery({
    queryKey: [QUERY_KEY, filters],
    queryFn: () => catalogService.getGlobalCatalogItems(filters),
    staleTime: 2 * 60 * 1000,
  });
}

const GIFT_CARD_CATALOG_KEY = "gift-card-catalog";

/**
 * XTRM gift-card SKUs for the catalog-creation picker. Server caches for 6h, so the client
 * holds it a long time too; `enabled` lets callers defer the fetch until the picker opens.
 */
export function useGiftCardCatalog(enabled = true) {
  return useQuery({
    queryKey: [GIFT_CARD_CATALOG_KEY],
    queryFn: () => catalogService.getGiftCardCatalog(),
    staleTime: 30 * 60 * 1000,
    enabled,
  });
}

export function useCreateCatalogItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: catalogService.createCatalogItem,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
    },
  });
}

export function useUpdateCatalogItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: Parameters<typeof catalogService.updateCatalogItem>[1] }) =>
      catalogService.updateCatalogItem(id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
    },
  });
}

export function useActivateCatalogItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => catalogService.activateCatalogItem(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
    },
  });
}

export function useDeactivateCatalogItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => catalogService.deactivateCatalogItem(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
    },
  });
}

export function useDeleteCatalogItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => catalogService.deleteCatalogItem(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
    },
  });
}

const TENANT_CATALOG_KEY = "redemption-catalog";
const TENANT_SETTINGS_KEY = "redemption-settings";

export function useTenantCatalogConfig(filters?: TenantCatalogConfigFilters) {
  return useQuery({
    queryKey: [TENANT_CATALOG_KEY, "config", filters],
    queryFn: () => tenantCatalogService.getTenantCatalogConfig(filters),
    staleTime: 5 * 60 * 1000,
  });
}

export function useCatalogItemConfig(catalogItemId: string) {
  return useQuery({
    queryKey: [TENANT_CATALOG_KEY, "config", catalogItemId],
    queryFn: () => tenantCatalogService.getTenantCatalogConfig({ search: catalogItemId }),
    staleTime: 5 * 60 * 1000,
  });
}

export function useTenantRedemptionSettings() {
  return useQuery({
    queryKey: [TENANT_SETTINGS_KEY],
    queryFn: () => tenantCatalogService.getTenantRedemptionSettings(),
    staleTime: 10 * 60 * 1000,
  });
}

export function useUpsertItemConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      catalogItemId,
      request,
    }: {
      catalogItemId: string;
      request: UpsertClientCatalogItemConfigRequest;
    }) => tenantCatalogService.upsertItemConfig(catalogItemId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TENANT_CATALOG_KEY, "config"] });
    },
  });
}

export function useUpdateTenantSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: UpdateTenantRedemptionSettingsRequest) =>
      tenantCatalogService.updateTenantRedemptionSettings(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [TENANT_SETTINGS_KEY] });
    },
  });
}

export function useRegionalConfig(catalogItemId: string) {
  return useQuery({
    queryKey: [TENANT_CATALOG_KEY, "regions", catalogItemId],
    queryFn: () => tenantCatalogService.getRegionalConfigs(catalogItemId),
    staleTime: 5 * 60 * 1000,
    enabled: !!catalogItemId,
  });
}

export function useUpsertRegionConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      catalogItemId,
      regionCode,
      request,
    }: {
      catalogItemId: string;
      regionCode: string;
      request: UpsertRegionConfigRequest;
    }) => tenantCatalogService.upsertRegionConfig(catalogItemId, regionCode, request),
    onSuccess: (_data, { catalogItemId }) => {
      queryClient.invalidateQueries({ queryKey: [TENANT_CATALOG_KEY, "regions", catalogItemId] });
    },
  });
}

export function useDeleteRegionConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      catalogItemId,
      regionCode,
    }: {
      catalogItemId: string;
      regionCode: string;
    }) => tenantCatalogService.deleteRegionConfig(catalogItemId, regionCode),
    onSuccess: (_data, { catalogItemId }) => {
      queryClient.invalidateQueries({ queryKey: [TENANT_CATALOG_KEY, "regions", catalogItemId] });
    },
  });
}

export function usePartnerCatalog(filters?: CatalogBrowseFilters) {
  return useQuery({
    queryKey: [TENANT_CATALOG_KEY, "browse", filters],
    queryFn: () => tenantCatalogService.getPartnerCatalog(filters),
    staleTime: 2 * 60 * 1000,
  });
}

export function usePartnerCatalogItem(id: string) {
  return useQuery({
    queryKey: [TENANT_CATALOG_KEY, "item", id],
    queryFn: () => tenantCatalogService.getPartnerCatalogItem(id),
    staleTime: 5 * 60 * 1000,
    enabled: !!id,
  });
}

const INTEGRATION_HEALTH_KEY = "redemption-integration-health";

export function useIntegrationHealth() {
  return useQuery({
    queryKey: [INTEGRATION_HEALTH_KEY],
    queryFn: () => catalogService.getIntegrationHealth(),
    staleTime: 60 * 1000,
  });
}

export function useTriggerCatalogSync() {
  const queryClient = useQueryClient();
  return useMutation<SyncJobResponse, unknown, void>({
    mutationFn: () => catalogService.triggerCatalogSync(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [INTEGRATION_HEALTH_KEY] });
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
    },
  });
}
