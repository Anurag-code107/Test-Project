// Adapted from: src/hooks/useRedemptionRequest.ts (TanStack Query pattern)
import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { exportUnredeemedBalances } from "@/services/redemption-analytics.service";
import { isAxiosError } from "axios";
import { toast } from "sonner";

export function useAnalyticsExport() {
  const [retryAfter, setRetryAfter] = useState<number | null>(null);
  const [isServerError, setIsServerError] = useState(false);

  const mutation = useMutation({
    mutationFn: exportUnredeemedBalances,
    onSuccess: (blob) => {
      setRetryAfter(null);
      setIsServerError(false);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "redemption-unredeemed-balances.csv";
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      toast.success("CSV downloaded successfully.");
    },
    onError: (error) => {
      if (isAxiosError(error) && error.response?.status === 429) {
        const header = error.response.headers?.["retry-after"];
        const seconds = header ? parseInt(header, 10) : 60;
        setRetryAfter(seconds);
        setIsServerError(false);
      } else {
        setIsServerError(true);
        setRetryAfter(null);
      }
    },
  });

  return {
    exportCsv: mutation.mutate,
    isPending: mutation.isPending,
    retryAfter,
    isServerError,
  };
}
