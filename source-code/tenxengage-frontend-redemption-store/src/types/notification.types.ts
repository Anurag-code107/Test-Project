export interface NotificationResponse {
  id: string;
  notificationTypeKey: string | null;
  category: string | null;
  title: string;
  message: string;
  resourceType: string | null;
  resourceId: string | null;
  isRead: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface UnreadCountResponse {
  count: number;
}

export interface NotificationTypeResponse {
  id: string;
  key: string;
  category: string;
  title: string;
  description: string;
  defaultRoles: string[];
}

export interface UserNotificationSettingResponse {
  id: string;
  userId: string;
  notificationsEnabled: boolean;
}

export interface UserNotificationPreferenceResponse {
  id: string;
  notificationTypeId: string;
  notificationTypeKey: string;
  optedOut: boolean;
}

export interface UpdateNotificationPreferenceRequest {
  notificationTypeId: string;
  optedOut: boolean;
}

export interface BulkUpdatePreferencesRequest {
  preferences: UpdateNotificationPreferenceRequest[];
}

export type NotificationCategory =
  | "INCENTIVE"
  | "BUDGET"
  | "CLAIMS"
  | "REWARDS"
  | "DATA"
  | "INTEGRATION"
  | "USER_MANAGEMENT";
