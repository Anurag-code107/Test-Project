import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  listPartnerUsers,
  getSellerPermissions,
  updateSellerPermissions,
} from "@/services/partner-user.service";
import type { UpdatePermissionsRequest } from "@/types/permission.types";

export function usePartnerUsers() {
  return useQuery({
    queryKey: ["partner-users"],
    queryFn: listPartnerUsers,
  });
}

export function useSellerPermissions(userId: string | undefined) {
  return useQuery({
    queryKey: ["partner-users", userId, "permissions"],
    queryFn: () => getSellerPermissions(userId!),
    enabled: !!userId,
  });
}

export function useUpdateSellerPermissions() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      userId,
      data,
    }: {
      userId: string;
      data: UpdatePermissionsRequest;
    }) => updateSellerPermissions(userId, data),
    onSuccess: (_, variables) =>
      queryClient.invalidateQueries({
        queryKey: ["partner-users", variables.userId, "permissions"],
      }),
  });
}
