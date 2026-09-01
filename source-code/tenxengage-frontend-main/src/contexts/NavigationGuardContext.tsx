import {
  createContext,
  useContext,
  useRef,
  useState,
  useCallback,
  type ReactNode,
} from "react";

interface NavigationGuardContextValue {
  setGuard: (fn: () => boolean) => void;
  clearGuard: () => void;
  checkGuard: (path: string) => boolean;
  pendingPath: string | null;
  clearPendingPath: () => void;
}

const NavigationGuardContext =
  createContext<NavigationGuardContextValue | null>(null);

export function NavigationGuardProvider({ children }: { children: ReactNode }) {
  const guardRef = useRef<(() => boolean) | null>(null);
  const [pendingPath, setPendingPath] = useState<string | null>(null);

  const setGuard = useCallback((fn: () => boolean) => {
    guardRef.current = fn;
  }, []);

  const clearGuard = useCallback(() => {
    guardRef.current = null;
  }, []);

  const checkGuard = useCallback((path: string): boolean => {
    if (!guardRef.current) return true;
    const allowed = guardRef.current();
    if (allowed) return true;
    setPendingPath(path);
    return false;
  }, []);

  const clearPendingPath = useCallback(() => {
    setPendingPath(null);
  }, []);

  return (
    <NavigationGuardContext.Provider
      value={{
        setGuard,
        clearGuard,
        checkGuard,
        pendingPath,
        clearPendingPath,
      }}
    >
      {children}
    </NavigationGuardContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useNavigationGuard() {
  const ctx = useContext(NavigationGuardContext);
  if (!ctx)
    throw new Error(
      "useNavigationGuard must be used within NavigationGuardProvider",
    );
  return ctx;
}
