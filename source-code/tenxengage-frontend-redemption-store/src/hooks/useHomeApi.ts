import {
  useQuery,
  useInfiniteQuery,
  keepPreviousData,
} from "@tanstack/react-query";
import * as homeService from "@/services/home.service";
import type {
  HomeMetricsParams,
  IncentivePerformanceParams,
  ProgramPerformanceParams,
} from "@/types/home.types";

export function useParticipationMetrics(params: HomeMetricsParams) {
  return useQuery({
    queryKey: ["home", "participation", params],
    queryFn: () => homeService.getParticipationMetrics(params),
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });
}

export function useIncentivePerformance(params: IncentivePerformanceParams) {
  return useQuery({
    queryKey: ["home", "incentive-performance", params],
    queryFn: () => homeService.getIncentivePerformance(params),
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });
}

export function useProgramPerformance(params: ProgramPerformanceParams) {
  return useQuery({
    queryKey: ["home", "program-performance", params],
    queryFn: () => homeService.getProgramPerformance(params),
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });
}

export function usePartnerSearch(search: string, enabled = true) {
  return useInfiniteQuery({
    queryKey: ["partner-search", search],
    queryFn: ({ pageParam = 0 }) =>
      homeService.searchPartnerCompanies(search, pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.hasNext ? lastPage.page + 1 : undefined,
    enabled,
    staleTime: 10_000,
  });
}
