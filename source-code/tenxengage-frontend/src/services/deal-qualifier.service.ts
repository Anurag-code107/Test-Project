import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type {
  DealQualifierRequest,
  DealQualifierResponse,
  InvoiceExtractionResponse,
  PartnerContextResponse,
} from "@/types/deal-qualifier.types";

export async function evaluateDeal(
  data: DealQualifierRequest,
): Promise<DealQualifierResponse> {
  const response = await api.post<ApiResponse<DealQualifierResponse>>(
    "/deal-qualifier/evaluate",
    data,
  );
  return response.data.data;
}

export async function uploadInvoice(
  file: File,
): Promise<InvoiceExtractionResponse> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await api.post<ApiResponse<InvoiceExtractionResponse>>(
    "/deal-qualifier/upload-invoice",
    formData,
    { headers: { "Content-Type": "multipart/form-data" } },
  );
  return response.data.data;
}

export async function getPartnerContext(): Promise<PartnerContextResponse> {
  const response = await api.get<ApiResponse<PartnerContextResponse>>(
    "/deal-qualifier/partner-context",
  );
  return response.data.data;
}
