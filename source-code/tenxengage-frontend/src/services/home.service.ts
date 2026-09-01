import api from "@/lib/axios";
import type { ApiResponse, PaginatedResponse } from "@/types/api.types";
import type {
  ParticipationMetricsResponse,
  IncentivePerformanceResponse,
  ProgramPerformanceResponse,
  HomeMetricsParams,
  IncentivePerformanceParams,
  ProgramPerformanceParams,
  PartnerCompanySearchResult,
} from "@/types/home.types";

export async function getParticipationMetrics(
  params: HomeMetricsParams,
): Promise<ParticipationMetricsResponse> {
  const response = await api.get<ApiResponse<ParticipationMetricsResponse>>(
    "/home/participation",
    { params },
  );
  return response.data.data;
}

export async function getIncentivePerformance(
  params: IncentivePerformanceParams,
): Promise<IncentivePerformanceResponse> {
  const response = await api.get<ApiResponse<IncentivePerformanceResponse>>(
    "/home/incentive-performance",
    { params },
  );
  return response.data.data;
}

export async function getProgramPerformance(
  params: ProgramPerformanceParams,
): Promise<ProgramPerformanceResponse> {
  const response = await api.get<ApiResponse<ProgramPerformanceResponse>>(
    "/home/program-performance",
    { params },
  );
  return response.data.data;
}

export interface PartnerSearchPage {
  data: PartnerCompanySearchResult[];
  hasNext: boolean;
  page: number;
}

export async function searchPartnerCompanies(
  search: string,
  page = 0,
): Promise<PartnerSearchPage> {
  const response = await api.get<
    ApiResponse<PaginatedResponse<PartnerCompanySearchResult>>
  >("/partner-companies", {
    params: { search, page, size: 20, status: "ACTIVE", sort: "name,asc" },
  });
  const paginated = response.data.data;
  return { data: paginated.data, hasNext: paginated.hasNext, page };
}
