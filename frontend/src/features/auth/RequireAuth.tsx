import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router";
import { useAuth } from "./AuthContext";

export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();
  if (status === "loading") return <div className="page-loading"><span className="spinner" aria-label="Loading" role="status" /></div>;
  if (status === "anonymous") return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return children;
}

/** For login/register: signed-in users go straight to the app. */
export function RedirectIfAuthenticated({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  if (status === "loading") return <div className="page-loading"><span className="spinner" aria-label="Loading" role="status" /></div>;
  if (status === "authenticated") return <Navigate to="/" replace />;
  return children;
}
