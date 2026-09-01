import { useEffect } from "react";
import { useBuilder } from "@/contexts/BuilderContext";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

export function Step1Basics() {
  const { state, dispatch } = useBuilder();
  const { basics } = state;

  useEffect(() => {
    const isComplete =
      basics.name.trim().length > 0 &&
      basics.description.trim().length > 0 &&
      basics.rewardMessage.trim().length > 0;
    if (isComplete && !state.completedSteps.includes("basics")) {
      dispatch({ type: "MARK_STEP_COMPLETE", payload: "basics" });
    } else if (!isComplete && state.completedSteps.includes("basics")) {
      dispatch({ type: "MARK_STEP_INCOMPLETE", payload: "basics" });
    }
  }, [
    basics.name,
    basics.description,
    basics.rewardMessage,
    state.completedSteps,
    dispatch,
  ]);

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="step1-name">
          Incentive Name <span className="text-destructive">*</span>
        </Label>
        <Input
          id="step1-name"
          value={basics.name}
          onChange={(e) =>
            dispatch({
              type: "UPDATE_BASICS",
              payload: { name: e.target.value },
            })
          }
          placeholder="e.g., Q1 2025 Growth Accelerator"
          maxLength={255}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="step1-description">
          Description <span className="text-destructive">*</span>
        </Label>
        <Textarea
          id="step1-description"
          value={basics.description}
          onChange={(e) =>
            dispatch({
              type: "UPDATE_BASICS",
              payload: { description: e.target.value },
            })
          }
          placeholder="Brief description of the incentive program..."
          maxLength={2000}
          rows={4}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="step1-reward-message">
          Reward Message <span className="text-destructive">*</span>
        </Label>
        <p className="text-xs text-muted-foreground">
          Displayed to partner users on the incentive card showing potential
          earnings.
        </p>
        <Input
          id="step1-reward-message"
          value={basics.rewardMessage}
          onChange={(e) =>
            dispatch({
              type: "UPDATE_BASICS",
              payload: { rewardMessage: e.target.value },
            })
          }
          placeholder='e.g., "Earn up to $5,000" or "Earn up to 500 points"'
          maxLength={500}
        />
      </div>
    </div>
  );
}
