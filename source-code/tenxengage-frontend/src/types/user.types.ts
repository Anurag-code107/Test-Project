export type UserStatus = "ACTIVE" | "INACTIVE" | "SUSPENDED" | "PENDING";

export type TransactionType =
  | "REWARD"
  | "REDEMPTION"
  | "ADJUSTMENT"
  | "TRANSFER"
  | "BONUS";

export type TransactionStatus =
  | "PENDING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED"
  | "PROCESSING";

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
  phoneCountryIso2?: string;
  avatar?: string;
  status: UserStatus;
  roles: Array<{
    id: string;
    name: string;
  }>;
  permissions?: string[];
  clientRoleId: string | null;
  clientRoleName: string | null;
  organizationId: string | null;
  clientId: string | null;
  clientName: string | null;
  partnerCompanyId: string | null;
  partnerCompanyName: string | null;
  metadata?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  password?: string;
  /** National mobile number, digits only. Required by the API (needed for payout enrollment/withdrawal). */
  phone: string;
  /** 2-letter ISO country of the mobile. Required by the API. */
  phoneCountryIso2: string;
  roleIds: string[];
  partnerCompanyId?: string;
  clientRoleId?: string;
  metadata?: string;
}

export interface UpdateUserRequest {
  email?: string;
  firstName?: string;
  lastName?: string;
  phone?: string;
  phoneCountryIso2?: string;
  avatar?: string;
  status?: UserStatus;
  roleIds?: string[];
  clientRoleId?: string;
  metadata?: string;
}

export interface Transaction {
  id: string;
  userId: string;
  type: TransactionType;
  status: TransactionStatus;
  amount: number;
  currency: string;
  description: string;
  referenceId?: string;
  metadata?: Record<string, string>;
  createdAt: string;
  updatedAt: string;
  completedAt?: string;
}
