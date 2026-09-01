// Adapted from: src/hooks/redemption/useRedemptionApproval.ts (TanStack Query pattern)
import { useMutation } from "@tanstack/react-query";
import { triggerExport } from "@/services/redemption-history/redemption-history.service";
import type { TriggerExportRequest } from "@/types/redemption-history/redemption-history.types";

export function useTriggerExport() {
  return useMutation({
    mutationFn: (request: TriggerExportRequest) => triggerExport(request),
  });
}
