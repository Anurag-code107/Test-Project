import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { useBuilder } from "@/contexts/BuilderContext";
import {
  useCreateIncentive,
  useUpdateIncentive,
} from "@/hooks/useIncentiveApi";
import { useLocationHierarchy } from "@/hooks/useLocationApi";
import {
  buildCreateRequest,
  buildUpdateRequest,
} from "@/utils/builderRequestMapper";

interface UseCreateFromBuilderOptions {
  navigateTo?: string;
  onSuccess?: () => void;
}

export function useCreateFromBuilder({
  navigateTo,
  onSuccess,
}: UseCreateFromBuilderOptions) {
  const navigate = useNavigate();
  const { state, dispatch } = useBuilder();
  const createMutation = useCreateIncentive();
  const updateMutation = useUpdateIncentive();
  const { data: locationHierarchy } = useLocationHierarchy();

  async function execute() {
    dispatch({ type: "SET_CREATING", payload: true });

    try {
      const isEditMode = !!state.editingIncentiveId;
      const incentiveName = state.basics.name || "Untitled Incentive";

      if (isEditMode) {
        const request = buildUpdateRequest(state, locationHierarchy);
        await updateMutation.mutateAsync({
          id: state.editingIncentiveId!,
          data: request,
        });
        toast.success("Incentive Updated!", {
          description: `"${incentiveName}" has been saved with your changes.`,
        });
      } else {
        const request = buildCreateRequest(state, locationHierarchy);
        await createMutation.mutateAsync(request);
        toast.success("Incentive Created!", {
          description: `"${incentiveName}" has been created successfully.`,
        });
      }

      dispatch({ type: "DISMISS_CREATE_CONFIRMATION" });

      if (navigateTo) {
        navigate(navigateTo, { replace: true });
      }
      onSuccess?.();
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Please try again.";
      toast.error("Failed to save incentive", { description: message });
    } finally {
      dispatch({ type: "SET_CREATING", payload: false });
    }
  }

  return { execute, isCreating: state.isCreating };
}
