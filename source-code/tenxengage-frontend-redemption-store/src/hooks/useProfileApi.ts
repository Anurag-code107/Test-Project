import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getMyProfileFields,
  updateMyProfileDynamic,
} from "@/services/profile.service";
import type { UpdateProfileRequest } from "@/types/profile.types";

export function useProfileFields() {
  return useQuery({
    queryKey: ["profile-fields"],
    queryFn: getMyProfileFields,
  });
}

export function useUpdateProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: UpdateProfileRequest) => updateMyProfileDynamic(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["profile-fields"] });
    },
  });
}
