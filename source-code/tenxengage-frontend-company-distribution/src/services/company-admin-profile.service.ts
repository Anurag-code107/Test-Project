import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  CompanyAdminProfile,
  CompleteCompanyAdminProfileRequest,
} from "@/types/partner-company.types";

/**
 * A company admin's own payout setup.
 *
 * No company id in the path: the server reads it from the session, so this cannot be aimed at another
 * company by changing a URL.
 */
export async function getCompanyAdminProfile(): Promise<CompanyAdminProfile> {
  const response = await api.get<ApiResponse<CompanyAdminProfile>>(
    "/company-admin/profile",
  );
  return response.data.data;
}

/** Saves the address and provisions the company's payout account. */
export async function completeCompanyAdminProfile(
  data: CompleteCompanyAdminProfileRequest,
): Promise<CompanyAdminProfile> {
  const response = await api.put<ApiResponse<CompanyAdminProfile>>(
    "/company-admin/profile",
    data,
  );
  return response.data.data;
}
