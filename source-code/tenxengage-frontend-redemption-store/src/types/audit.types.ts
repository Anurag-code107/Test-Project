export type UserType = "Internal" | "Partner";

export type AuditActionType =
  | "Created"
  | "Edited"
  | "Deleted"
  | "Activated"
  | "Deactivated"
  | "Submitted"
  | "Approved"
  | "Rejected"
  | "Expired"
  | "Claimed"
  | "Unclaimed"
  | "Uploaded"
  | "Synced"
  | "Logged In"
  | "Logged Out";

export interface ActivityLogEntry {
  id: string;
  user: string;
  email: string;
  userType: UserType | null;
  company: string | null;
  action: string;
  actionDescription: string;
  target: string | null;
  date: string;
}

export interface AuditLogParams {
  userType?: UserType;
  action?: string;
  search?: string;
  dateFrom?: string;
  dateTo?: string;
  page?: number;
  pageSize?: number;
}

export interface AuditLogPageData {
  data: ActivityLogEntry[];
  totalElements: number;
  page: number;
  pageSize: number;
  totalPages: number;
}
