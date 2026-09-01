export type PartnerCompanyStatus = "ACTIVE" | "INACTIVE";

export interface PartnerCompanyLocationAssignment {
  locationValueId: string;
  locationValueName: string;
  locationLevelName: string;
  locationLevelId: string;
}

export interface PartnerCompany {
  id: string;
  name: string;
  externalPartnerId?: string;
  region: string;
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
}

export interface CreatePartnerCompanyRequest {
  name: string;
  externalPartnerId?: string;
  region: string;
  partnerType: string;
  status?: PartnerCompanyStatus;
  website?: string;
  contactEmail?: string;
  contactPhone?: string;
  metadata?: string;
}

export interface UpdatePartnerCompanyRequest {
  name?: string;
  externalPartnerId?: string;
  region?: string;
  partnerType?: string;
  status?: PartnerCompanyStatus;
  website?: string;
  contactEmail?: string;
  contactPhone?: string;
  metadata?: string;
}
