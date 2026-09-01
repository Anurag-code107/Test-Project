import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type { PaginationParams } from "@/types/api.types";
import type {
  CreatePartnerCompanyRequest,
  UpdatePartnerCompanyRequest,
} from "@/types/partner-company.types";
import {
  getPartnerCompanies,
  getPartnerCompanyById,
  createPartnerCompany,
  updatePartnerCompany,
  deletePartnerCompany,
} from "@/services/partner-company.service";

export function usePartnerCompanies(
  params?: PaginationParams & { status?: string },
) {
  return useQuery({
    queryKey: ["partner-companies", params],
    queryFn: () => getPartnerCompanies(params),
  });
}

export function usePartnerCompany(id: string | undefined) {
  return useQuery({
    queryKey: ["partner-companies", id],
    queryFn: () => getPartnerCompanyById(id!),
    enabled: !!id,
  });
}

export function useCreatePartnerCompany() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreatePartnerCompanyRequest) =>
      createPartnerCompany(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["partner-companies"] }),
  });
}

export function useUpdatePartnerCompany() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      data,
    }: {
      id: string;
      data: UpdatePartnerCompanyRequest;
    }) => updatePartnerCompany(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["partner-companies"] }),
  });
}

export function useDeletePartnerCompany() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deletePartnerCompany(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["partner-companies"] }),
  });
}
