// Adapted from: src/hooks/redemption-history/useRedemptionDetail.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { getExportJob } from "@/services/redemption-history/redemption-history.service";

export function useExportJob(jobId: string | null) {
  return useQuery({
    queryKey: ['redemption-history', 'export-job', jobId],
    queryFn: () => getExportJob(jobId!),
    staleTime: 0,
    enabled: jobId !== null,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'PENDING' || status === 'PROCESSING' ? 3000 : false;
    },
  });
}
