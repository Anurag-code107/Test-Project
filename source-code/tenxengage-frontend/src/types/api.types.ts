export interface ApiResponse<T> {
  data: T;
  message: string;
  success: boolean;
  timestamp: string;
}

export interface PaginatedResponse<T> {
  data: T[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface ErrorResponse {
  errorCode: string;
  errorMessage: string;
  status: number;
  timestamp: string;
  path: string;
  details?: Record<string, string>;
}

export interface PaginationParams {
  page?: number;
  pageSize?: number;
  sortBy?: string;
  sortDirection?: "ASC" | "DESC";
  search?: string;
  partnerCompanyId?: string;
  internal?: boolean;
}
