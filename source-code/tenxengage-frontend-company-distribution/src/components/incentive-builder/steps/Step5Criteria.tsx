import { useBuilder } from "@/contexts/BuilderContext";
import { SalesRulesEditor } from "./step5/SalesRulesEditor";
import { TrainingCourseEditor } from "./step5/TrainingCourseEditor";
import { ActivitySetupEditor } from "./step5/ActivitySetupEditor";
import { JourneyStageEditor } from "./step5/JourneyStageEditor";

export function Step5Criteria() {
  const { state } = useBuilder();
  const type = state.basics.incentiveType;

  if (!type) {
    return (
      <p className="text-sm text-muted-foreground">
        Select an incentive type to configure criteria.
      </p>
    );
  }

  switch (type) {
    case "SALES":
      return <SalesRulesEditor />;
    case "TRAINING":
      return <TrainingCourseEditor />;
    case "ACTIVITY":
      return <ActivitySetupEditor />;
    case "JOURNEY":
      return <JourneyStageEditor />;
  }
}
