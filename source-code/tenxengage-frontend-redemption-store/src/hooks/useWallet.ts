// Adapted from: src/hooks/useRedemptionCatalog.ts (TanStack Query pattern)
import { useQuery } from "@tanstack/react-query";
import { getMyWallets, getCompanyWallet } from "@/services/wallet.service";

export function useMyWallets() {
  return useQuery({
    queryKey: ["wallet-balance"],
    queryFn: getMyWallets,
    staleTime: 30 * 1000,
  });
}

export function useCompanyWallet(companyId: string | null | undefined) {
  return useQuery({
    queryKey: ["company-wallet", companyId],
    queryFn: () => getCompanyWallet(companyId!),
    enabled: !!companyId,
    staleTime: 30 * 1000,
  });
}
