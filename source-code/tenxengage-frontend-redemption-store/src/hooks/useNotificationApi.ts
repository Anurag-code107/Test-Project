import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type { PaginationParams } from "@/types/api.types";
import type { BulkUpdatePreferencesRequest } from "@/types/notification.types";
import {
  getNotifications,
  getUnreadCount,
  markAsRead,
  markAllAsRead,
  getNotificationTypes,
  getGlobalSetting,
  updateGlobalSetting,
  getPreferences,
  bulkUpdatePreferences,
} from "@/services/notification.service";

// ── Notifications ─────────────────────────────────────────────────────────

export function useNotifications(
  params?: PaginationParams & { unreadOnly?: boolean },
) {
  return useQuery({
    queryKey: ["notifications", params],
    queryFn: () => getNotifications(params),
  });
}

export function useUnreadCount() {
  return useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: getUnreadCount,
    refetchInterval: 30_000, // Poll every 30 seconds
  });
}

export function useMarkAsRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => markAsRead(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["notifications"] });
    },
  });
}

export function useMarkAllAsRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: markAllAsRead,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["notifications"] });
    },
  });
}

// ── Notification Types ────────────────────────────────────────────────────

export function useNotificationTypes() {
  return useQuery({
    queryKey: ["notification-types"],
    queryFn: getNotificationTypes,
    staleTime: 5 * 60 * 1000, // 5 min cache
  });
}

// ── User Preferences ──────────────────────────────────────────────────────

export function useGlobalNotificationSetting() {
  return useQuery({
    queryKey: ["notification-preferences", "global"],
    queryFn: getGlobalSetting,
  });
}

export function useUpdateGlobalNotificationSetting() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (enabled: boolean) => updateGlobalSetting(enabled),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["notification-preferences"] });
    },
  });
}

export function useNotificationPreferences() {
  return useQuery({
    queryKey: ["notification-preferences", "per-type"],
    queryFn: getPreferences,
  });
}

export function useBulkUpdatePreferences() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: BulkUpdatePreferencesRequest) =>
      bulkUpdatePreferences(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["notification-preferences"] });
    },
  });
}
