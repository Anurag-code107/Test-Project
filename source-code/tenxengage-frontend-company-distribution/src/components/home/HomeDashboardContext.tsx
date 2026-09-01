import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";

/**
 * Transient page-level state that widgets on /home can publish to and
 * the page shell (e.g. the home PageBanner) can read from. Scoped per template
 * render — navigating away resets it, which is the intended behavior.
 */
export interface HomeDashboardState {
  selectedPartnerName: string | null;
  setSelectedPartnerName: (name: string | null) => void;
}

const defaultState: HomeDashboardState = {
  selectedPartnerName: null,
  setSelectedPartnerName: () => {},
};

const HomeDashboardContext = createContext<HomeDashboardState>(defaultState);

export function HomeDashboardProvider({ children }: { children: ReactNode }) {
  const [selectedPartnerName, setSelectedPartnerNameState] = useState<
    string | null
  >(null);

  const setSelectedPartnerName = useCallback(
    (name: string | null) => setSelectedPartnerNameState(name),
    [],
  );

  const value = useMemo(
    () => ({ selectedPartnerName, setSelectedPartnerName }),
    [selectedPartnerName, setSelectedPartnerName],
  );

  return (
    <HomeDashboardContext.Provider value={value}>
      {children}
    </HomeDashboardContext.Provider>
  );
}

/**
 * Returns the live home-dashboard state. When called outside a provider
 * (e.g. in a widget test that renders the widget in isolation), returns
 * a no-op setter + null value so widgets don't crash.
 */
// eslint-disable-next-line react-refresh/only-export-components
export function useHomeDashboardState(): HomeDashboardState {
  return useContext(HomeDashboardContext);
}
