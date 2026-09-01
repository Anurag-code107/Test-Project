import { useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type { SaveRewardCurrencyRequest } from "@/types/reward-currency.types";
import * as rewardCurrencyService from "@/services/reward-currency.service";
import { hydrateCurrencies } from "@/config/currencies";

const QUERY_KEY = ["currencies"] as const;

export function useRewardCurrencies(options?: { enabled?: boolean }) {
  const query = useQuery({
    queryKey: QUERY_KEY,
    queryFn: rewardCurrencyService.getRewardCurrencies,
    staleTime: 5 * 60 * 1000,
    enabled: options?.enabled ?? true,
  });

  useEffect(() => {
    if (query.data) {
      hydrateCurrencies(query.data);
    }
  }, [query.data]);

  return query;
}

export function useCreateRewardCurrency() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: SaveRewardCurrencyRequest) =>
      rewardCurrencyService.createRewardCurrency(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
  });
}

export function useUpdateRewardCurrency() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      data,
    }: {
      id: string;
      data: SaveRewardCurrencyRequest;
    }) => rewardCurrencyService.updateRewardCurrency(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
  });
}

export function useDeleteRewardCurrency() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => rewardCurrencyService.deleteRewardCurrency(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
  });
}
