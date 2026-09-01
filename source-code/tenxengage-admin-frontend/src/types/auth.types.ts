export type RoleName = "TENX_ADMIN" | "CLIENT_ADMIN" | "ACTIVITY_APPROVER" | "PARTNER_ADMIN" | "PARTNER_SELLER";

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  expiresIn: number;
  user: AuthUser;
  enabledFeatures: string[];
}

export interface AuthUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: UserRole[];
  permissions: string[];
  clientRoleId: string | null;
  clientRoleName: string | null;
  organizationId: string | null;
  clientId: string | null;
  clientName: string | null;
  partnerCompanyId: string | null;
  partnerCompanyName: string | null;
  status: "ACTIVE" | "INACTIVE" | "SUSPENDED";
}

export interface UserRole {
  id: string;
  name: RoleName;
}
