import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type {
  CreateLocationLevelRequest,
  UpdateLocationLevelRequest,
  UpdateLocationLevelSettingsRequest,
  CreateLocationValueRequest,
  UpdateLocationValueRequest,
} from "@/types/location.types";
import * as locationService from "@/services/location.service";

// Root prefix for every location-related query. Mutations invalidate this
// prefix so TanStack Query's default prefix-matching cascades to every
// downstream cache (hierarchy, filter-options, builder-options) in one call.
const LOCATION_ROOT_KEY = ["location"] as const;

export function useLocationHierarchy() {
  return useQuery({
    queryKey: [...LOCATION_ROOT_KEY, "hierarchy"],
    queryFn: locationService.getLocationHierarchy,
  });
}

export function useCreateLocationLevel() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateLocationLevelRequest) =>
      locationService.createLocationLevel(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: LOCATION_ROOT_KEY });
    },
  });
}

export function useUpdateLocationLevel() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      data,
    }: {
      id: string;
      data: UpdateLocationLevelRequest;
    }) => locationService.updateLocationLevel(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: LOCATION_ROOT_KEY });
    },
  });
}

export function useDeleteLocationLevel() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => locationService.deleteLocationLevel(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: LOCATION_ROOT_KEY });
    },
  });
}

export function useCreateLocationValue() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateLocationValueRequest) =>
      locationService.createLocationValue(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: LOCATION_ROOT_KEY });
    },
  });
}

export function useUpdateLocationValue() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      data,
    }: {
      id: string;
      data: UpdateLocationValueRequest;
    }) => locationService.updateLocationValue(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: LOCATION_ROOT_KEY });
    },
  });
}

export function useDeleteLocationValue() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => locationService.deleteLocationValue(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: LOCATION_ROOT_KEY });
    },
  });
}

export function useUpdateLocationLevelSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      data,
    }: {
      id: string;
      data: UpdateLocationLevelSettingsRequest;
    }) => locationService.updateLocationLevelSettings(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: LOCATION_ROOT_KEY });
    },
  });
}

export function useLocationFilterOptions() {
  return useQuery({
    queryKey: [...LOCATION_ROOT_KEY, "filter-options"],
    queryFn: locationService.getLocationFilterOptions,
  });
}

export function useLocationBuilderOptions() {
  return useQuery({
    queryKey: [...LOCATION_ROOT_KEY, "builder-options"],
    queryFn: locationService.getLocationBuilderOptions,
    staleTime: 10 * 60 * 1000,
  });
}
