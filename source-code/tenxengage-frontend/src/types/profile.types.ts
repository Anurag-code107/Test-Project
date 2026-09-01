import type { FieldDataType } from "@/types/data-object.types";

export interface ProfileFieldResponse {
  fieldId: string | null;
  fieldName: string;
  dataType: FieldDataType;
  value: string | null;
  editable: boolean;
  sortOrder: number;
  sampleValues: string[] | null;
}

export interface UpdateProfileRequest {
  firstName?: string;
  lastName?: string;
  phone?: string;
  avatar?: string;
  customFields?: Record<string, string>;
}
