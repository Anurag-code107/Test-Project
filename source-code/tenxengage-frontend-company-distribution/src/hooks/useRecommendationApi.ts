import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getTrainingRecommendations,
  getIncentiveRecommendations,
  completeRecommendation,
  dismissRecommendation,
  getRecommendationConfig,
  updateRecommendationConfig,
} from "@/services/recommendation.service";
import type { UpdateRecommendationConfigRequest } from "@/types/recommendation.types";

export function useTrainingRecommendations() {
  return useQuery({
    queryKey: ["recommendations", "training"],
    queryFn: getTrainingRecommendations,
    staleTime: 60_000,
  });
}

export function useIncentiveRecommendations() {
  return useQuery({
    queryKey: ["recommendations", "incentives"],
    queryFn: getIncentiveRecommendations,
    staleTime: 60_000,
  });
}

export function useCompleteRecommendation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ type, targetId }: { type: string; targetId: string }) =>
      completeRecommendation(type, targetId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["recommendations"] });
    },
  });
}

export function useDismissRecommendation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ type, targetId }: { type: string; targetId: string }) =>
      dismissRecommendation(type, targetId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["recommendations"] });
    },
  });
}

export function useRecommendationConfig() {
  return useQuery({
    queryKey: ["recommendation-config"],
    queryFn: getRecommendationConfig,
    staleTime: 300_000,
  });
}

export function useUpdateRecommendationConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: UpdateRecommendationConfigRequest) =>
      updateRecommendationConfig(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["recommendation-config"] });
    },
  });
}
