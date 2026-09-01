import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import type { AuthUser, LoginRequest } from "@/types/auth.types";
import { useQueryClient } from "@tanstack/react-query";
import * as authService from "@/services/auth.service";
import { useIdleTimer } from "@/hooks/useIdleTimer";
import { SessionExpiryDialog } from "@/components/SessionExpiryDialog";

const IDLE_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
const WARNING_MS = 2 * 60 * 1000; // 2 minute warning

export interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  enabledFeatures: string[];
  login: (data: LoginRequest) => Promise<AuthUser>;
  logout: () => Promise<void>;
  /**
   * Re-fetches the authenticated user (including home dashboard template +
   * enabled features) from the server. Call after a server-side change that
   * invalidates the cached session — e.g. a Client Admin reassigning the
   * dashboard template for their own role.
   */
  refreshUser: () => Promise<void>;
}

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext<AuthContextValue | null>(null);

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [enabledFeatures, setEnabledFeatures] = useState<string[]>([]);

  const queryClient = useQueryClient();
  const isAuthenticated = user !== null;

  const login = useCallback(async (data: LoginRequest): Promise<AuthUser> => {
    const response = await authService.login(data);
    setUser(response.user);
    setEnabledFeatures(response.enabledFeatures ?? []);
    return response.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } catch {
      // Ignore logout errors — always clear local state
    } finally {
      queryClient.clear();
      setUser(null);
      setEnabledFeatures([]);
    }
  }, [queryClient]);

  const restoreSession = useCallback(async () => {
    try {
      const response = await authService.refresh();
      setUser(response.user);
      setEnabledFeatures(response.enabledFeatures ?? []);
    } catch {
      setUser(null);
      setEnabledFeatures([]);
    }
  }, []);

  const refreshUser = useCallback(async () => {
    await restoreSession();
  }, [restoreSession]);

  useEffect(() => {
    restoreSession().finally(() => setIsLoading(false));
  }, [restoreSession]);

  // Re-sync the session (permissions + enabled features) when the user returns to the tab, so a
  // server-side permission change is reflected without a full reload. Permissions are a snapshot from
  // /auth/refresh (not a live query), so this is how a Client Admin's override reaches an active
  // partner session. Throttled to avoid spamming /auth/refresh on rapid focus changes.
  useEffect(() => {
    if (!isAuthenticated) return;
    let lastRefresh = 0;
    const THROTTLE_MS = 30 * 1000;
    const resync = () => {
      if (document.visibilityState === "hidden") return;
      const now = Date.now();
      if (now - lastRefresh < THROTTLE_MS) return;
      lastRefresh = now;
      void restoreSession();
    };
    window.addEventListener("focus", resync);
    document.addEventListener("visibilitychange", resync);
    return () => {
      window.removeEventListener("focus", resync);
      document.removeEventListener("visibilitychange", resync);
    };
  }, [isAuthenticated, restoreSession]);

  useEffect(() => {
    if (import.meta.env.DEV) {
      (window as unknown as Record<string, unknown>)['__BUG_REPORTER_USER__'] = user;
    }
  }, [user]);

  const { showWarning, remainingSeconds, stayActive } = useIdleTimer({
    timeoutMs: IDLE_TIMEOUT_MS,
    warningMs: WARNING_MS,
    onIdle: logout,
    enabled: isAuthenticated,
  });

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated,
      isLoading,
      enabledFeatures,
      login,
      logout,
      refreshUser,
    }),
    [
      user,
      isAuthenticated,
      isLoading,
      enabledFeatures,
      login,
      logout,
      refreshUser,
    ],
  );

  return (
    <AuthContext.Provider value={value}>
      {children}
      <SessionExpiryDialog
        open={showWarning}
        remainingSeconds={remainingSeconds}
        onStayActive={stayActive}
        onLogout={logout}
      />
    </AuthContext.Provider>
  );
}
