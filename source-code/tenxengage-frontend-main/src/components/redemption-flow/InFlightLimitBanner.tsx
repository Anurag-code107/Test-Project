// Adapted from: none — no production reference
import { useEffect } from "react";
import { toast } from "sonner";

interface InFlightLimitBannerProps {
  show: boolean;
  onDismiss?: () => void;
}

export function InFlightLimitBanner({ show, onDismiss }: InFlightLimitBannerProps) {
  useEffect(() => {
    if (show) {
      toast.error("Maximum in-flight redemptions reached");
      onDismiss?.();
    }
  }, [show, onDismiss]);

  return null;
}
