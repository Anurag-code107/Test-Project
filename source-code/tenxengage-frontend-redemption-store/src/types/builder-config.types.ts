export interface BuilderFieldConfigResponse {
  id: string;
  fieldKey: string;
  displayName: string;
  fieldType: string;
  helperText: string | null;
  isMandatory: boolean;
  isSystem: boolean;
  isEligibility: boolean;
  dataObjectFieldId: string | null;
  dataObjectFieldName: string | null;
  dataObjectName: string | null;
  valueSource: string | null;
  valueSourceConfig: string | null;
  supportsExcelUpload: boolean;
  sortOrder: number;
}

export interface BuilderSectionConfigResponse {
  id: string;
  incentiveType: string;
  sectionKey: string;
  displayName: string;
  subtitle: string | null;
  sortOrder: number;
  isLocked: boolean;
  isVisible: boolean;
  fields: BuilderFieldConfigResponse[];
}

export interface BuilderConfigResponse {
  incentiveType: string;
  sections: BuilderSectionConfigResponse[];
}

export interface FieldValueOption {
  value: string;
  label: string;
}

export interface ActivityCategoryResponse {
  id: string;
  name: string;
  description: string | null;
  sortOrder: number;
}

export interface CreateBuilderFieldRequest {
  fieldKey: string;
  displayName: string;
  fieldType: string;
  helperText?: string;
  isMandatory: boolean;
  isEligibility: boolean;
  dataObjectFieldId?: string;
  valueSource?: string;
  valueSourceConfig?: string;
  supportsExcelUpload: boolean;
}

export interface UpdateBuilderFieldRequest {
  displayName?: string;
  helperText?: string;
  isMandatory?: boolean;
  isEligibility?: boolean;
  dataObjectFieldId?: string;
  valueSource?: string;
  valueSourceConfig?: string;
  supportsExcelUpload?: boolean;
}

export interface UpdateSectionRequest {
  displayName?: string;
  subtitle?: string;
}

export interface CreateActivityCategoryRequest {
  name: string;
  description?: string;
}
