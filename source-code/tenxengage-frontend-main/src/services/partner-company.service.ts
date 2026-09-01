import api from "@/lib/axios";
import type {
  ApiResponse,
  PaginatedResponse,
  PaginationParams,
} from "@/types/api.types";
import type {
  PartnerCompany,
  CreatePartnerCompanyRequest,
  UpdatePartnerCompanyRequest,
} from "@/types/partner-company.types";

export async function getPartnerCompanies(
  params?: PaginationParams & { status?: string },
): Promise<PaginatedResponse<PartnerCompany>> {
  const response = await api.get<
    ApiResponse<PaginatedResponse<PartnerCompany>>
  >("/partner-companies", { params });
  return response.data.data;
}

export async function getPartnerCompanyById(
  id: string,
): Promise<PartnerCompany> {
  const response = await api.get<ApiResponse<PartnerCompany>>(
    `/partner-companies/${id}`,
  );
  return response.data.data;
}

export async function createPartnerCompany(
  data: CreatePartnerCompanyRequest,
): Promise<PartnerCompany> {
  const response = await api.post<ApiResponse<PartnerCompany>>(
    "/partner-companies",
    data,
  );
  return response.data.data;
}

export async function updatePartnerCompany(
  id: string,
  data: UpdatePartnerCompanyRequest,
): Promise<PartnerCompany> {
  const response = await api.put<ApiResponse<PartnerCompany>>(
    `/partner-companies/${id}`,
    data,
  );
  return response.data.data;
}

export async function deletePartnerCompany(id: string): Promise<void> {
  await api.delete(`/partner-companies/${id}`);
}
