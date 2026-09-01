export type DataUploadSource = "MANUAL" | "CONNECTOR";
export type DataUploadStatus = "PROCESSING" | "COMPLETED" | "FAILED";
export type TaggingJobStatus = "RUNNING" | "COMPLETED" | "FAILED";
export type SyncCadence = "MANUAL" | "HOURLY" | "DAILY" | "WEEKLY" | "MONTHLY";

export interface DataUploadResponse {
  id: string;
  fileName: string;
  source: DataUploadSource;
  status: DataUploadStatus;
  totalRows: number;
  newRows: number;
  updatedRows: number;
  skippedRows: number;
  errorMessage: string | null;
  createdAt: string;
}

export interface TaggingJobResponse {
  id: string;
  status: TaggingJobStatus;
  posAnalyzed: number;
  eligibleDeals: number;
  incentivesMatched: number;
  productsDiscovered: number;
  errorMessage: string | null;
  createdAt: string;
}

export interface SyncScheduleResponse {
  id: string | null;
  dataObjectId: string;
  enabled: boolean;
  cadence: SyncCadence;
  lastRunAt: string | null;
  nextRunAt: string | null;
}

export interface UpdateSyncScheduleRequest {
  enabled: boolean;
  cadence: SyncCadence;
}
