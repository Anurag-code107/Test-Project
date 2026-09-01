// Adapted from: none — no production reference
import { useParams, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { RedemptionConfirmationCard } from "@/components/redemption-flow/RedemptionConfirmationCard";
import { useRedemptionRequest } from "@/hooks/useRedemptionRequest";

export default function RedemptionConfirmationPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data, isLoading, isError } = useRedemptionRequest(id ?? "");

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-4 w-64" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4">
        <p className="text-muted-foreground">Unable to load redemption details.</p>
        <Button variant="outline" onClick={() => navigate(-1)}>
          Go back
        </Button>
      </div>
    );
  }

  return (
    <div className="animate-route-in flex flex-col items-center justify-center min-h-[60vh] gap-6 px-4">
      <RedemptionConfirmationCard redemption={data} />
      <Button variant="outline" onClick={() => navigate("/redemption-store")}>
        Back to store
      </Button>
    </div>
  );
}
