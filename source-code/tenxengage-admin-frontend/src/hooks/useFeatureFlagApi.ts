import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import * as featureFlagService from "@/services/feature-flag.service";
import type { CreateFeatureFlagRequest, UpdateFeatureFlagRequest } from "@/types/client.types";

const QUERY_KEY = ["feature-flags"] as const;

export function useFeatureFlags() {
  return useQuery({
    queryKey: QUERY_KEY,
    queryFn: featureFlagService.getAllFeatureFlags,
  });
}

export function useCreateFeatureFlag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateFeatureFlagRequest) => featureFlagService.createFeatureFlag(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
  });
}

export function useUpdateFeatureFlag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateFeatureFlagRequest }) =>
      featureFlagService.updateFeatureFlag(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
  });
}
