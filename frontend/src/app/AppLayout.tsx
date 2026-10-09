import { Link, Outlet } from "react-router";
import { useAuth } from "../features/auth/AuthContext";

export function AppLayout() {
  const { user, logout } = useAuth();
  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand">
            Ved
          </Link>
          <nav className="row">
            <span className="muted small user-chip">
              {user?.fullName} · {user?.role.toLowerCase()}
            </span>
            <button className="btn btn-ghost btn-sm" onClick={() => void logout()}>
              Sign out
            </button>
          </nav>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
