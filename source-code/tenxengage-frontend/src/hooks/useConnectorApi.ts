import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type {
  CreateConnectorRequest,
  UpdateConnectorRequest,
} from "@/types/connector.types";
import * as connectorService from "@/services/connector.service";

export function useConnectors() {
  return useQuery({
    queryKey: ["connectors"],
    queryFn: connectorService.getConnectors,
  });
}

export function useConnector(id: string) {
  return useQuery({
    queryKey: ["connectors", id],
    queryFn: () => connectorService.getConnectorById(id),
    enabled: !!id,
  });
}

export function useCreateConnector() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateConnectorRequest) =>
      connectorService.createConnector(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["connectors"] });
    },
  });
}

export function useUpdateConnector() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateConnectorRequest }) =>
      connectorService.updateConnector(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["connectors"] });
    },
  });
}

export function useDeleteConnector() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => connectorService.deleteConnector(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["connectors"] });
    },
  });
}

export function useTestConnection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => connectorService.testConnection(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["connectors"] });
    },
  });
}
