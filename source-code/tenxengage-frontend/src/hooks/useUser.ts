import { useQuery } from "@tanstack/react-query";
import { getUserById } from "@/services/user.service";

export function useUser(id: string | null | undefined) {
  return useQuery({
    queryKey: ["users", id],
    queryFn: () => getUserById(id!),
    staleTime: 10 * 60 * 1000,
    enabled: !!id,
    retry: false,
  });
}
