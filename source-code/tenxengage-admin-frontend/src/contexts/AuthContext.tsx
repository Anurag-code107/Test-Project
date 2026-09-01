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

export interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (data: LoginRequest) => Promise<AuthUser>;
  logout: () => Promise<void>;
}

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext<AuthContextValue | null>(null);

interface AuthProviderProps {
  children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const queryClient = useQueryClient();
  const isAuthenticated = user !== null;

  const login = useCallback(async (data: LoginRequest): Promise<AuthUser> => {
    const response = await authService.login(data);
    setUser(response.user);
    return response.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } catch {
      // Ignore logout errors -- always clear local state
    } finally {
      queryClient.clear();
      setUser(null);
    }
  }, [queryClient]);

  useEffect(() => {
    async function tryRestoreSession() {
      try {
        // Attempt to refresh via the HTTPOnly cookie
        const response = await authService.refresh();
        setUser(response.user);
      } catch {
        // No valid refresh cookie -- user is not authenticated
        setUser(null);
      } finally {
        setIsLoading(false);
      }
    }

    tryRestoreSession();
  }, []);

  // Periodic token refresh (every 14 minutes)
  useEffect(() => {
    if (!isAuthenticated) return;

    const interval = setInterval(async () => {
      try {
        const response = await authService.refresh();
        setUser(response.user);
      } catch {
        // Refresh failed -- session expired
        queryClient.clear();
        setUser(null);
      }
    }, 14 * 60 * 1000);

    return () => clearInterval(interval);
  }, [isAuthenticated, queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated,
      isLoading,
      login,
      logout,
    }),
    [user, isAuthenticated, isLoading, login, logout]
  );

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}
