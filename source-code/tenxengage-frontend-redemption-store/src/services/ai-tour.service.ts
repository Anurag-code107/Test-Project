import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";

export interface TextGuideStep {
  title: string;
  description: string;
}

export interface AiTourMatchResponse {
  tourId: string | null;
  confidence: number;
  textGuide: TextGuideStep[] | null;
}

export async function matchTourWithAi(
  query: string,
  role: string,
): Promise<AiTourMatchResponse> {
  const response = await api.post<ApiResponse<AiTourMatchResponse>>(
    "/ai/tour-match",
    {
      query,
      role,
    },
  );
  return response.data.data;
}
