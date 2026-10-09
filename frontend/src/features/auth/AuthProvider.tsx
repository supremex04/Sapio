import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { refreshSession, setRefreshHandler, tokenStore } from "../../shared/api/http";
import { authApi } from "./api";
import { AuthContext, type AuthContextValue, type AuthStatus } from "./AuthContext";
import type { AuthResponse, User } from "./types";

/**
 * Owns the client-side session: restores it from the refresh cookie on load, keeps the in-memory
 * access token fresh, and exposes login/register/logout.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<User | null>(null);

  const accept = useCallback((auth: AuthResponse) => {
    tokenStore.set(auth.accessToken);
    setUser(auth.user);
    setStatus("authenticated");
  }, []);

  const reset = useCallback(() => {
    tokenStore.set(null);
    setUser(null);
    setStatus("anonymous");
  }, []);

  useEffect(() => {
    setRefreshHandler(async () => {
      try {
        const auth = await authApi.refresh();
        accept(auth);
        return auth.accessToken;
      } catch {
        reset();
        return null;
      }
    });
    // Restore the session from the refresh cookie. Goes through the shared single-flight refresh, so
    // StrictMode's double-run of effects cannot send the same (rotating) token twice.
    void refreshSession();
  }, [accept, reset]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      login: async (input) => {
        const auth = await authApi.login(input);
        accept(auth);
        return auth.user;
      },
      register: async (input) => {
        const auth = await authApi.register(input);
        accept(auth);
        return auth.user;
      },
      logout: async () => {
        try {
          await authApi.logout();
        } finally {
          reset();
        }
      },
    }),
    [status, user, accept, reset],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
