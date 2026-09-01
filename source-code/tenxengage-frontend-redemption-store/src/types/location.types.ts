export interface LocationLevelResponse {
  id: string;
  name: string;
  depth: number;
  valueCount: number;
  useInBuilder: boolean;
  useInFilters: boolean;
  isRequired: boolean;
}

export interface LocationValueResponse {
  id: string;
  name: string;
  code: string | null;
  levelName: string;
  levelId: string;
  parentId: string | null;
  children: LocationValueResponse[];
}

export interface LocationHierarchyResponse {
  levels: LocationLevelResponse[];
  tree: LocationValueResponse[];
}

export interface CreateLocationLevelRequest {
  name: string;
}

export interface UpdateLocationLevelRequest {
  name: string;
}

export interface CreateLocationValueRequest {
  levelId: string;
  parentId?: string | null;
  name: string;
  code?: string | null;
}

export interface UpdateLocationValueRequest {
  name: string;
  code?: string | null;
}

export interface UpdateLocationLevelSettingsRequest {
  useInBuilder?: boolean;
  useInFilters?: boolean;
  isRequired?: boolean;
}

export interface LocationFilterOptionsResponse {
  levels: LocationFilterLevel[];
}

export interface LocationFilterLevel {
  levelId: string;
  levelName: string;
  depth: number;
  values: LocationFilterValue[];
}

export interface LocationFilterValue {
  id: string;
  name: string;
  code: string | null;
  parentId: string | null;
}
