import {
  createContext,
  useContext,
  useState,
  useCallback,
  type ReactNode,
} from "react";
import type { GuidedTour } from "@/data/guidedTours";
import { useFeatures } from "@/hooks/useFeatures";

interface GuidedTourContextType {
  isActive: boolean;
  /** True when either the bubble animation is playing OR the tour overlay is active.
   *  Use this to disable UI elements (like the sidebar) that shouldn't be interactive. */
  isBusy: boolean;
  currentTour: GuidedTour | null;
  currentStepIndex: number;
  startTour: (tour: GuidedTour) => void;
  advance: () => void;
  endTour: () => void;
  /** Called by AIAssistantInput to signal the bubble animation started/ended */
  setAnimating: (value: boolean) => void;
}

const GuidedTourContext = createContext<GuidedTourContextType | null>(null);

export function GuidedTourProvider({ children }: { children: ReactNode }) {
  const [currentTour, setCurrentTour] = useState<GuidedTour | null>(null);
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [isAnimating, setAnimating] = useState(false);
  const { has } = useFeatures();

  const isActive = currentTour !== null;
  const isBusy = isActive || isAnimating;

  // Service-level gate: when the tenant's tier doesn't include guided_tours,
  // every startTour call is a no-op. The provider stays mounted so the tree
  // shape is stable; tour state simply never activates.
  const startTour = useCallback(
    (tour: GuidedTour) => {
      if (!has("guided_tours")) return;
      setCurrentTour(tour);
      setCurrentStepIndex(0);
    },
    [has],
  );

  const advance = useCallback(() => {
    if (!currentTour) return;
    if (currentStepIndex < currentTour.steps.length - 1) {
      setCurrentStepIndex((prev) => prev + 1);
    } else {
      setCurrentTour(null);
      setCurrentStepIndex(0);
    }
  }, [currentTour, currentStepIndex]);

  const endTour = useCallback(() => {
    setCurrentTour(null);
    setCurrentStepIndex(0);
  }, []);

  return (
    <GuidedTourContext.Provider
      value={{
        isActive,
        isBusy,
        currentTour,
        currentStepIndex,
        startTour,
        advance,
        endTour,
        setAnimating,
      }}
    >
      {children}
    </GuidedTourContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useGuidedTour() {
  const ctx = useContext(GuidedTourContext);
  if (!ctx)
    throw new Error("useGuidedTour must be used within GuidedTourProvider");
  return ctx;
}
