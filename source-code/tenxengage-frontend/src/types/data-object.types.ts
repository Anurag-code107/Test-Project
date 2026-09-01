export type FieldDataType =
  | "TEXT"
  | "NUMBER"
  | "CURRENCY"
  | "DATE"
  | "BOOLEAN"
  | "LIST";

export interface DataObjectResponse {
  id: string;
  name: string;
  description: string | null;
  isDefault: boolean;
  fieldCount: number;
  connectorName: string | null;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface DataObjectDetailResponse {
  id: string;
  name: string;
  description: string | null;
  isDefault: boolean;
  sortOrder: number;
  fields: DataObjectFieldResponse[];
  connectorMapping: ConnectorMappingDetail | null;
  createdAt: string;
  updatedAt: string;
}

export interface DataObjectFieldResponse {
  id: string;
  name: string;
  description: string | null;
  dataType: FieldDataType;
  ruleLabel: string | null;
  excludeFromRules: boolean;
  sampleValues: string[] | null;
  mandatory: boolean;
  sortOrder: number;
  visibleOnProfile: boolean;
  editableByUser: boolean;
  isLocationHierarchyField: boolean;
}

export interface ConnectorMappingDetail {
  connectorId: string;
  connectorName: string;
  connectorType: string;
  mappings: FieldMappingEntry[];
}

export interface FieldMappingEntry {
  fieldId: string;
  sourceTable: string;
  sourceField: string;
}

export interface CreateDataObjectRequest {
  name: string;
  description?: string;
}

export interface UpdateDataObjectRequest {
  name?: string;
  description?: string;
}

export interface CreateFieldRequest {
  name: string;
  description?: string;
  dataType: FieldDataType;
  ruleLabel?: string;
  excludeFromRules?: boolean;
  sampleValues?: string[];
  visibleOnProfile?: boolean;
  editableByUser?: boolean;
}

export interface UpdateFieldRequest {
  name?: string;
  description?: string;
  dataType?: FieldDataType;
  ruleLabel?: string;
  excludeFromRules?: boolean;
  sampleValues?: string[];
  visibleOnProfile?: boolean;
  editableByUser?: boolean;
}

export interface ConnectorMappingRequest {
  connectorId: string;
  mappings: { fieldId: string; sourceTable: string; sourceField: string }[];
}

export interface RuleFieldResponse {
  id: string;
  name: string;
  dataType: FieldDataType;
  ruleLabel: string;
  ruleWidget: string | null;
  sampleValues: string[] | null;
  dataObjectId: string;
  dataObjectName: string;
}

/** Convenience alias for accessing fields by ID in rule components */
export type { RuleFieldResponse as RuleField };
