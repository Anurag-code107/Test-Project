export type PartnerCompanyStatus = "ACTIVE" | "INACTIVE";

export type XtrmAccountStatus = "PENDING" | "CONNECTED" | "DISABLED";

/**
 * A partner company's connection to the payout provider.
 *
 * Carries identifiers and a failure reason only — never credentials.
 * `status` is itself the per-company enablement switch; there is no separate flag.
 */
export interface XtrmAccountSummary {
  status: XtrmAccountStatus;
  accountNumber?: string;
  identityLevel?: string;
  lastError?: string;
}

/**
 * The default company admin.
 *
 * Contact details for the payout provider, not a platform user — no invite, no role, no login.
 * Supplied as a group: all eight or none. A partial group is refused by the server with
 * INVALID_ADMIN_DETAILS, so the form checks it too rather than letting the user find out after submitting.
 */
/**
 * What a client admin supplies at company creation — exactly what the admin's login needs.
 *
 * The address the payment provider also wants is NOT here: the admin supplies that themselves once they
 * sign in, because a mistyped admin email is spent permanently and the person who owns it should type it.
 */
export interface CompanyAdminIdentity {
  adminFirstName?: string;
  adminLastName?: string;
  adminEmail?: string;
  adminMobileNumber?: string;
  adminCountryIso2?: string;
}

/** Identity plus the address the admin fills in later. What a stored company carries. */
export interface CompanyAdminDetails extends CompanyAdminIdentity {
  adminCity?: string;
  adminRegion?: string;
  adminPostalCode?: string;
}

/** The three address fields a company admin supplies to finish their own payout setup. */
export interface CompleteCompanyAdminProfileRequest {
  adminCity: string;
  adminRegion: string;
  adminPostalCode: string;
}

/** What the company admin sees on their own setup screen. Carries no credentials. */
export interface CompanyAdminProfile {
  companyName: string;
  adminEmail?: string;
  adminCity?: string;
  adminRegion?: string;
  adminPostalCode?: string;
  complete: boolean;
  xtrmAccount?: XtrmAccountSummary;
  /**
   * Where this admin completes identity verification at XTRM.
   *
   * Server-supplied, not a frontend constant: the server knows which XTRM this deployment talks to, and a
   * sandbox admin sent to the production portal would verify an account that does not exist here. Absent
   * when unconfigured, and the link is then not offered at all.
   */
  portalUrl?: string;
}

/** Body for the connect endpoint. Every field optional — the server decides what to do from stored state. */
export interface ConnectXtrmAccountRequest extends CompanyAdminDetails {
  xtrmWalletId?: string;
}

export interface PartnerCompanyLocationAssignment {
  locationValueId: string;
  locationValueName: string;
  locationLevelName: string;
  locationLevelId: string;
}

export interface PartnerCompany extends CompanyAdminDetails {
  id: string;
  name: string;
  externalPartnerId?: string;
  partnerType: string;
  clientId: string;
  clientName: string;
  status: PartnerCompanyStatus;
  website?: string;
  contactEmail?: string;
  contactPhone?: string;
  activeUserCount: number;
  locations: PartnerCompanyLocationAssignment[];
  metadata: string;
  createdAt: string;
  updatedAt: string;
  /** Populated on the detail and connect responses; absent on the list endpoint. */
  xtrmAccount?: XtrmAccountSummary;
}

export interface CreatePartnerCompanyRequest extends CompanyAdminIdentity {
  name: string;
  externalPartnerId?: string;
  locationValueIds: string[];
  partnerType: string;
  status?: PartnerCompanyStatus;
  website?: string;
  contactEmail?: string;
  contactPhone?: string;
  metadata?: string;
}

export interface UpdatePartnerCompanyRequest extends CompanyAdminIdentity {
  name?: string;
  externalPartnerId?: string;
  locationValueIds?: string[];
  partnerType?: string;
  status?: PartnerCompanyStatus;
  website?: string;
  contactEmail?: string;
  contactPhone?: string;
  metadata?: string;
}
