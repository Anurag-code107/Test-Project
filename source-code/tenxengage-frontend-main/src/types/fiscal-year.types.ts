export type QuarterMethod = "MONTHS" | "WEEKS" | "DAYS" | "CUSTOM";

export interface FiscalYearConfigResponse {
  id: string;
  label: string;
  startDate: string;
  endDate: string;
  quarterMethod: QuarterMethod;
  quarterSize: number | null;
  q1StartDate: string;
  q1EndDate: string;
  q2StartDate: string;
  q2EndDate: string;
  q3StartDate: string;
  q3EndDate: string;
  q4StartDate: string;
  q4EndDate: string;
  createdAt: string;
  updatedAt: string;
}

export interface FiscalYearLabelResponse {
  label: string;
  startDate: string;
  endDate: string;
}

export interface SaveFiscalYearConfigRequest {
  label: string;
  startDate: string;
  endDate: string;
  quarterMethod: QuarterMethod;
  quarterSize: number | null;
  q1StartDate: string;
  q1EndDate: string;
  q2StartDate: string;
  q2EndDate: string;
  q3StartDate: string;
  q3EndDate: string;
  q4StartDate: string;
  q4EndDate: string;
}
