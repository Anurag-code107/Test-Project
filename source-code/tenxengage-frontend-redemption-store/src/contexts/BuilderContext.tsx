import { createContext, useContext } from "react";
import type { BuilderState, BuilderAction } from "@/types/builder-state.types";
import { initialBuilderState } from "@/types/builder-state.types";

interface BuilderContextValue {
  state: BuilderState;
  dispatch: React.Dispatch<BuilderAction>;
}

const BuilderContext = createContext<BuilderContextValue>({
  state: initialBuilderState,
  dispatch: () => {},
});

interface BuilderProviderProps {
  state: BuilderState;
  dispatch: React.Dispatch<BuilderAction>;
  children: React.ReactNode;
}

export function BuilderProvider({
  state,
  dispatch,
  children,
}: BuilderProviderProps) {
  return (
    <BuilderContext.Provider value={{ state, dispatch }}>
      {children}
    </BuilderContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useBuilder(): BuilderContextValue {
  const context = useContext(BuilderContext);
  if (!context) {
    throw new Error("useBuilder must be used within a BuilderProvider");
  }
  return context;
}
