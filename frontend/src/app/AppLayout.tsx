import { BookOpen, Flame, Home, Layers, LogOut, Sparkles } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { Suspense, useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router";
import { useAuth } from "../features/auth/AuthContext";
import { ProgressProvider } from "../features/progress/ProgressProvider";
import { useProgress } from "../features/progress/ProgressContext";
import { levelProgress } from "../features/progress/levels";
import { useActivityHeartbeat } from "../features/progress/useActivityHeartbeat";
import { ThemeToggle } from "../features/theme/ThemeToggle";
import { cx } from "../shared/ui/cx";
import { Logo } from "./Logo";
import "./layout.css";

export function AppLayout() {
  return (
    <ProgressProvider>
      <Shell />
    </ProgressProvider>
  );
}

function Shell() {
  useActivityHeartbeat();
  const location = useLocation();
  return (
    <div className="app">
      <TopBar />
      <main className="content" id="main">
        <Suspense
          fallback={
            <div className="page-loading">
              <span className="spinner" role="status" aria-label="Loading" />
            </div>
          }
        >
          <Outlet key={location.pathname} />
        </Suspense>
      </main>
      <BottomNav />
    </div>
  );
}

const NAV = [
  { to: "/", label: "Home", icon: Home, end: true },
  { to: "/library", label: "Library", icon: BookOpen, end: false },
  { to: "/review", label: "Review", icon: Layers, end: false },
];

function TopBar() {
  return (
    <header className="topbar">
      <div className="topbar-inner">
        <Link to="/" className="brand" aria-label="Ved home">
          <Logo />
          <span>Ved</span>
        </Link>
        <nav className="topnav" aria-label="Main">
          {NAV.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className={({ isActive }) => cx("topnav-link", isActive && "active")}>
              <Icon size={17} aria-hidden />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="topbar-right">
          <ProgressChips />
          <ThemeToggle />
          <UserMenu />
        </div>
      </div>
    </header>
  );
}

function ProgressChips() {
  const { summary } = useProgress();
  if (!summary) return null;
  const { streak, xp } = summary;
  return (
    <div className="chips" aria-label="Your progress">
      <span className={cx("stat-chip", "streak", streak.activeToday && "lit")} title={streak.activeToday ? "Streak active today" : "Learn today to extend your streak"}>
        <Flame size={16} aria-hidden />
        <span className="tabular">{streak.current}</span>
        <span className="visually-hidden">day streak</span>
      </span>
      <span className="stat-chip xp" title={`${xp.total} XP · ${xp.nextLevelXp - xp.total} XP to level ${xp.level + 1}`}>
        <Sparkles size={15} aria-hidden />
        <span>Lv {xp.level}</span>
        <span className="xp-mini" aria-hidden>
          <span style={{ width: `${levelProgress(xp) * 100}%` }} />
        </span>
      </span>
    </div>
  );
}

function UserMenu() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("pointerdown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const initials = (user?.fullName ?? "?")
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join("");

  return (
    <div className="user-menu" ref={ref}>
      <button className="avatar" aria-haspopup="menu" aria-expanded={open} aria-label="Account menu" onClick={() => setOpen((o) => !o)}>
        {initials}
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            className="menu"
            role="menu"
            initial={{ opacity: 0, y: -4, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.98 }}
            transition={{ duration: 0.15 }}
          >
            <div className="menu-profile">
              <strong>{user?.fullName}</strong>
              <span className="muted small">{user?.email}</span>
              <span className="badge badge-primary" style={{ alignSelf: "flex-start", marginTop: 6 }}>
                {user?.role === "TEACHER" ? "Teacher" : "Student"}
              </span>
            </div>
            <div className="menu-separator" />
            <Link to="/welcome" className="menu-item" role="menuitem" onClick={() => setOpen(false)}>
              <Flame size={16} aria-hidden /> Daily goal
            </Link>
            <button className="menu-item" role="menuitem" onClick={() => void logout()}>
              <LogOut size={16} aria-hidden /> Sign out
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function BottomNav() {
  return (
    <nav className="bottomnav" aria-label="Main">
      {NAV.map(({ to, label, icon: Icon, end }) => (
        <NavLink key={to} to={to} end={end} className={({ isActive }) => cx("bottomnav-link", isActive && "active")}>
          <Icon size={20} aria-hidden />
          <span>{label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
