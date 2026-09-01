import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import * as clientService from "@/services/client.service";
import type { ClientListParams } from "@/services/client.service";
import type {
  CreateClientRequest,
  UpdateClientRequest,
  SetFeatureOverrideRequest,
} from "@/types/client.types";

export function useClients(params?: ClientListParams) {
  return useQuery({
    queryKey: ["clients", params],
    queryFn: () => clientService.getClients(params),
  });
}

export function useClient(id: string | undefined) {
  return useQuery({
    queryKey: ["clients", id],
    queryFn: () => clientService.getClientById(id!),
    enabled: !!id,
  });
}

export function useClientStats() {
  return useQuery({
    queryKey: ["client-stats"],
    queryFn: clientService.getClientStats,
    staleTime: 30 * 1000,
  });
}

export function useCreateClient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateClientRequest) => clientService.createClient(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["clients"] });
      queryClient.invalidateQueries({ queryKey: ["client-stats"] });
    },
  });
}

export function useUpdateClient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateClientRequest }) =>
      clientService.updateClient(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["clients"] });
      queryClient.invalidateQueries({ queryKey: ["client-stats"] });
    },
  });
}

export function useDeleteClient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => clientService.deleteClient(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["clients"] });
      queryClient.invalidateQueries({ queryKey: ["client-stats"] });
    },
  });
}

export function useClientOverrides(clientId: string | undefined) {
  return useQuery({
    queryKey: ["client-overrides", clientId],
    queryFn: () => clientService.getClientOverrides(clientId!),
    enabled: !!clientId,
  });
}

export function useSetClientOverrides() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      clientId,
      overrides,
    }: {
      clientId: string;
      overrides: SetFeatureOverrideRequest[];
    }) => clientService.setClientOverrides(clientId, overrides),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: ["client-overrides", variables.clientId],
      });
    },
  });
}
