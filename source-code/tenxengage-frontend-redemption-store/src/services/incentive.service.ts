import axios from "axios";
import api from "@/lib/axios";
import type {
  ApiResponse,
  PaginatedResponse,
  PaginationParams,
} from "@/types/api.types";
import type {
  IncentiveResponse,
  IncentiveDetailResponse,
  CreateIncentiveRequest,
  UpdateIncentiveRequest,
  UpdateIncentiveStatusRequest,
  IncentiveType,
  IncentiveStatus,
  IncentiveForecast,
  DocumentSummary,
} from "@/types/incentive.types";

export interface IncentiveListParams extends PaginationParams {
  type?: IncentiveType;
  status?: IncentiveStatus;
}

export async function getIncentives(
  params?: IncentiveListParams,
): Promise<PaginatedResponse<IncentiveResponse>> {
  const response = await api.get<
    ApiResponse<PaginatedResponse<IncentiveResponse>>
  >("/incentives", { params });
  return response.data.data;
}

export async function getIncentiveById(
  id: string,
): Promise<IncentiveDetailResponse> {
  const response = await api.get<ApiResponse<IncentiveDetailResponse>>(
    `/incentives/${id}`,
  );
  const detail = response.data.data;

  // Compute trainingRequiredCount from courses if the backend doesn't provide it
  if (detail.trainingCourses && detail.trainingRequiredCount == null) {
    detail.trainingRequiredCount = detail.trainingCourses.filter(
      (c) => c.required,
    ).length;
  }

  return detail;
}

export async function createIncentive(
  data: CreateIncentiveRequest,
): Promise<IncentiveResponse> {
  const response = await api.post<ApiResponse<IncentiveResponse>>(
    "/incentives",
    data,
  );
  return response.data.data;
}

export async function updateIncentive(
  id: string,
  data: UpdateIncentiveRequest,
): Promise<IncentiveResponse> {
  const response = await api.put<ApiResponse<IncentiveResponse>>(
    `/incentives/${id}`,
    data,
  );
  return response.data.data;
}

export async function deleteIncentive(id: string): Promise<void> {
  await api.delete(`/incentives/${id}`);
}

export async function updateIncentiveStatus(
  id: string,
  data: UpdateIncentiveStatusRequest,
): Promise<IncentiveResponse> {
  const response = await api.patch<ApiResponse<IncentiveResponse>>(
    `/incentives/${id}/status`,
    data,
  );
  return response.data.data;
}

export async function cloneIncentive(
  id: string,
  name: string,
  description?: string,
): Promise<IncentiveResponse> {
  const response = await api.post<ApiResponse<IncentiveResponse>>(
    `/incentives/${id}/clone`,
    null,
    { params: { name, description } },
  );
  return response.data.data;
}

export async function submitForApproval(
  id: string,
): Promise<IncentiveResponse> {
  const response = await api.post<ApiResponse<IncentiveResponse>>(
    `/incentives/${id}/submit`,
  );
  return response.data.data;
}

export async function resendApprovalEmails(id: string): Promise<void> {
  await api.post(`/incentives/${id}/resend-approvals`);
}

export async function resendApprovalToApprover(
  id: string,
  email: string,
): Promise<void> {
  await api.post(`/incentives/${id}/resend-approval`, null, {
    params: { email },
  });
}

export async function resubmitForApproval(
  id: string,
): Promise<IncentiveResponse> {
  const response = await api.post<ApiResponse<IncentiveResponse>>(
    `/incentives/${id}/resubmit`,
  );
  return response.data.data;
}

export async function generateForecast(id: string): Promise<IncentiveForecast> {
  const response = await api.post<ApiResponse<IncentiveForecast>>(
    `/incentives/${id}/forecast`,
  );
  return response.data.data;
}

// --- Approval (public, no auth) ---

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api/v1";

export interface ApprovalReviewResponse {
  incentive: IncentiveDetailResponse;
  approverEmail: string;
  approverCategory: string;
}

export async function getIncentiveForApproval(
  token: string,
): Promise<ApprovalReviewResponse> {
  const response = await axios.get<ApprovalReviewResponse>(
    `${BASE_URL}/approvals/incentive`,
    { params: { token } },
  );
  return response.data;
}

export async function submitApprovalDecision(
  token: string,
  action: string,
  comment?: string,
): Promise<{ success: boolean; message: string; action: string }> {
  const response = await axios.post<{
    success: boolean;
    message: string;
    action: string;
  }>(`${BASE_URL}/approvals/decide`, null, {
    params: { token, action, comment },
  });
  return response.data;
}

// --- Document Upload ---

const ALLOWED_FILE_EXTENSIONS = new Set(["pdf", "xlsx", "xls", "docx", "doc"]);
const ALLOWED_MIME_TYPES = new Set([
  "application/pdf",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "application/vnd.ms-excel",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/msword",
]);
const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB per file
const MAX_TOTAL_SIZE = 50 * 1024 * 1024; // 50 MB total

function getFileExtension(filename: string): string {
  const parts = filename.split(".");
  return parts.length > 1 ? parts.pop()!.toLowerCase() : "";
}

export function validateFiles(
  files: { file: File; category: string }[],
): string | null {
  if (files.length === 0) return "No files provided.";

  let totalSize = 0;

  for (const { file } of files) {
    const ext = getFileExtension(file.name);
    if (!ALLOWED_FILE_EXTENSIONS.has(ext)) {
      return `File "${file.name}" has an unsupported type. Allowed: PDF, XLSX, XLS, DOCX, DOC.`;
    }

    if (file.type && !ALLOWED_MIME_TYPES.has(file.type)) {
      return `File "${file.name}" has an invalid MIME type.`;
    }

    if (file.size > MAX_FILE_SIZE) {
      return `File "${file.name}" exceeds the 10 MB size limit.`;
    }

    totalSize += file.size;
  }

  if (totalSize > MAX_TOTAL_SIZE) {
    return `Total upload size exceeds the 50 MB limit.`;
  }

  return null;
}

export async function uploadIncentiveDocuments(
  incentiveId: string,
  files: { file: File; category: string }[],
): Promise<DocumentSummary[]> {
  const formData = new FormData();
  files.forEach(({ file, category }) => {
    formData.append("files", file);
    formData.append("categories", category);
  });
  const response = await api.post<ApiResponse<DocumentSummary[]>>(
    `/incentives/${incentiveId}/documents`,
    formData,
    { headers: { "Content-Type": "multipart/form-data" } },
  );
  return response.data.data;
}

export async function downloadDocument(
  incentiveId: string,
  documentId: string,
  filename: string,
): Promise<void> {
  const response = await api.get(
    `/incentives/${incentiveId}/documents/${documentId}/download`,
    { responseType: "blob" },
  );
  const url = URL.createObjectURL(response.data);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export async function viewDocument(
  incentiveId: string,
  documentId: string,
): Promise<void> {
  const response = await api.get(
    `/incentives/${incentiveId}/documents/${documentId}/download`,
    { responseType: "blob" },
  );
  const url = URL.createObjectURL(response.data);
  window.open(url, "_blank");
}

export async function deleteIncentiveDocument(
  incentiveId: string,
  documentId: string,
): Promise<void> {
  await api.delete(`/incentives/${incentiveId}/documents/${documentId}`);
}
