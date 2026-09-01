import api from "@/lib/axios";
import type { ApiResponse } from "@/types/api.types";
import type { AuditLogPageData, AuditLogParams } from "@/types/audit.types";

export async function getAuditLogs(
  params?: AuditLogParams,
): Promise<AuditLogPageData> {
  const response = await api.get<ApiResponse<AuditLogPageData>>("/audit-logs", {
    params,
  });
  return response.data.data;
}
