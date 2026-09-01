import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  TrainingRecommendationResponse,
  IncentiveRecommendationResponse,
  RecommendationConfigResponse,
  UpdateRecommendationConfigRequest,
  RecommendationCompletionResponse,
} from "@/types/recommendation.types";

export async function getTrainingRecommendations(): Promise<
  TrainingRecommendationResponse[]
> {
  const response = await api.get<ApiResponse<TrainingRecommendationResponse[]>>(
    "/recommendations/training",
  );
  return response.data.data;
}

export async function getIncentiveRecommendations(): Promise<
  IncentiveRecommendationResponse[]
> {
  const response = await api.get<
    ApiResponse<IncentiveRecommendationResponse[]>
  >("/recommendations/incentives");
  return response.data.data;
}

export async function completeRecommendation(
  type: string,
  targetId: string,
): Promise<RecommendationCompletionResponse> {
  const response = await api.post<
    ApiResponse<RecommendationCompletionResponse>
  >(`/recommendations/${type}/${targetId}/complete`);
  return response.data.data;
}

export async function dismissRecommendation(
  type: string,
  targetId: string,
): Promise<void> {
  await api.post(`/recommendations/${type}/${targetId}/interactions`, {
    interactionType: "DISMISSED",
  });
}

export async function recordView(
  type: string,
  targetId: string,
): Promise<void> {
  await api.post(`/recommendations/${type}/${targetId}/interactions`, {
    interactionType: "VIEWED",
  });
}

export async function getRecommendationConfig(): Promise<RecommendationConfigResponse> {
  const response = await api.get<ApiResponse<RecommendationConfigResponse>>(
    "/admin/recommendations/config",
  );
  return response.data.data;
}

export async function updateRecommendationConfig(
  data: UpdateRecommendationConfigRequest,
): Promise<RecommendationConfigResponse> {
  const response = await api.put<ApiResponse<RecommendationConfigResponse>>(
    "/admin/recommendations/config",
    data,
  );
  return response.data.data;
}
