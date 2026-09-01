import api from "@/lib/axios";
import type {
  ApiResponse,
  PaginatedResponse,
  PaginationParams,
} from "@/types/api.types";
import type {
  NotificationResponse,
  UnreadCountResponse,
  NotificationTypeResponse,
  UserNotificationSettingResponse,
  UserNotificationPreferenceResponse,
  BulkUpdatePreferencesRequest,
} from "@/types/notification.types";

// ── Notifications ─────────────────────────────────────────────────────────

export async function getNotifications(
  params?: PaginationParams & { unreadOnly?: boolean },
): Promise<PaginatedResponse<NotificationResponse>> {
  const response = await api.get<
    ApiResponse<PaginatedResponse<NotificationResponse>>
  >("/notifications", { params });
  return response.data.data;
}

export async function getUnreadCount(): Promise<UnreadCountResponse> {
  const response = await api.get<ApiResponse<UnreadCountResponse>>(
    "/notifications/unread-count",
  );
  return response.data.data;
}

export async function markAsRead(id: string): Promise<NotificationResponse> {
  const response = await api.put<ApiResponse<NotificationResponse>>(
    `/notifications/${id}/read`,
  );
  return response.data.data;
}

export async function markAllAsRead(): Promise<{ markedAsRead: number }> {
  const response = await api.put<ApiResponse<{ markedAsRead: number }>>(
    "/notifications/read-all",
  );
  return response.data.data;
}

// ── Notification Types ────────────────────────────────────────────────────

export async function getNotificationTypes(): Promise<
  NotificationTypeResponse[]
> {
  const response = await api.get<ApiResponse<NotificationTypeResponse[]>>(
    "/notification-types",
  );
  return response.data.data;
}

// ── User Preferences ──────────────────────────────────────────────────────

export async function getGlobalSetting(): Promise<UserNotificationSettingResponse> {
  const response = await api.get<ApiResponse<UserNotificationSettingResponse>>(
    "/notification-preferences/global",
  );
  return response.data.data;
}

export async function updateGlobalSetting(
  notificationsEnabled: boolean,
): Promise<UserNotificationSettingResponse> {
  const response = await api.put<ApiResponse<UserNotificationSettingResponse>>(
    "/notification-preferences/global",
    { notificationsEnabled },
  );
  return response.data.data;
}

export async function getPreferences(): Promise<
  UserNotificationPreferenceResponse[]
> {
  const response = await api.get<
    ApiResponse<UserNotificationPreferenceResponse[]>
  >("/notification-preferences");
  return response.data.data;
}

export async function bulkUpdatePreferences(
  data: BulkUpdatePreferencesRequest,
): Promise<UserNotificationPreferenceResponse[]> {
  const response = await api.put<
    ApiResponse<UserNotificationPreferenceResponse[]>
  >("/notification-preferences/bulk", data);
  return response.data.data;
}
