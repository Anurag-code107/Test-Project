export interface DealQualifierRequest {
  dealValue: number;
  productSkus: string[];
  customerSegment: string;
  closeDate: string; // ISO instant string
}

export interface DealQualifierResponse {
  results: QualifiedIncentiveResult[];
  partnerRegion: string;
  partnerType: string;
}

export interface QualifiedIncentiveResult {
  incentiveId: string;
  incentiveName: string;
  incentiveDescription: string;
  rewardMessage: string;
  startDate: string;
  endDate: string;
  matchPercentage: number;
  estimatedReward: number;
  rewardCurrency: string;
  payoutType: string | null;
  metCriteria: CriterionResult[];
  unmetCriteria: CriterionResult[];
  payoutBreakdown: PayoutBreakdown | null;
}

export interface CriterionResult {
  ruleType: string;
  description: string;
  hint: string | null;
}

export interface PayoutBreakdown {
  currentTierMin: number | null;
  currentTierMax: number | null;
  currentTierPayoutValue: number | null;
  currentTierPayoutType: string | null;
  nextTierMin: number | null;
  nextTierPayoutValue: number | null;
  gapToNextTier: number | null;
  maxPerDeal: number | null;
}

export interface InvoiceExtractionResponse {
  lineItems: ExtractedLineItem[];
  totalValue: number | null;
  customerName: string | null;
  customerSegment: string | null;
  invoiceDate: string | null;
  skuMappings: SkuMapping[];
}

export interface ExtractedLineItem {
  productName: string;
  quantity: number;
  unitPrice: number | null;
  lineTotal: number | null;
}

export interface SkuMapping {
  extractedName: string;
  matchedSku: string | null;
  matchedProductName: string | null;
  confidence: number;
}

export interface PartnerContextResponse {
  region: string;
  partnerType: string;
  customerSegmentOptions: string[];
}
