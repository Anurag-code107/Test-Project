import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import * as brandingService from "@/services/branding.service";
import type { UpdateBrandingRequest } from "@/types/branding.types";

const QUERY_KEY = ["branding"] as const;

export function useBranding(enabled = true) {
  return useQuery({
    queryKey: QUERY_KEY,
    queryFn: brandingService.getBranding,
    enabled,
  });
}

export function useUpdateBranding() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: UpdateBrandingRequest) =>
      brandingService.updateBranding(data),
    onSuccess: (saved) => {
      queryClient.setQueryData(QUERY_KEY, saved);
    },
  });
}

export function useUploadBrandingLogo() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (file: File) => brandingService.uploadBrandingLogo(file),
    onSuccess: (saved) => {
      queryClient.setQueryData(QUERY_KEY, saved);
    },
  });
}

export function useRemoveBrandingLogo() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: brandingService.removeBrandingLogo,
    onSuccess: (saved) => {
      queryClient.setQueryData(QUERY_KEY, saved);
    },
  });
}
