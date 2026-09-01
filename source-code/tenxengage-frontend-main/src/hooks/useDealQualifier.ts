import { useQuery, useMutation } from "@tanstack/react-query";
import {
  evaluateDeal,
  getPartnerContext,
  uploadInvoice,
} from "@/services/deal-qualifier.service";
import type { DealQualifierRequest } from "@/types/deal-qualifier.types";

export function useEvaluateDeal() {
  return useMutation({
    mutationFn: (data: DealQualifierRequest) => evaluateDeal(data),
  });
}

export function usePartnerContext() {
  return useQuery({
    queryKey: ["deal-qualifier", "partner-context"],
    queryFn: getPartnerContext,
  });
}

export function useInvoiceUpload() {
  return useMutation({
    mutationFn: (file: File) => uploadInvoice(file),
  });
}
