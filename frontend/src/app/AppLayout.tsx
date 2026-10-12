import { LogOut, Palette, Target } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { Suspense, useEffect, useRef, useState } from "react";
import { Link, matchPath, NavLink, Outlet, useLocation } from "react-router";
import { useAuth } from "../features/auth/AuthContext";
import { ProgressProvider } from "../features/progress/ProgressProvider";
import { useProgress } from "../features/progress/ProgressContext";
import { useActivityHeartbeat } from "../features/progress/useActivityHeartbeat";
import { ThemeSwitch, ThemeToggle } from "../features/theme/ThemeToggle";
import { Avatar } from "../shared/illustrations/Avatar";
import { Dia } from "../shared/illustrations/Dia";
import { Emoji } from "../shared/illustrations/Emoji";
import type { EmojiName } from "../shared/illustrations/emojiNames";
import { Chip } from "../shared/ui/Chip";
import { cx } from "../shared/ui/cx";
import { Wordmark } from "./Logo";
import { ProgressPanel } from "./ProgressPanel";
import { useReviewSummary } from "./useReviewSummary";
import "./layout.css";

/** Task screens hide all navigation (one thing at a time). They render their own FocusHeader. */
const FOCUS_ROUTES = ["/documents/:id/learn/:topicId", "/assessments/:id", "/review"];
/** Screens wide enough to own the right side (no progress rail). */
const FULL_WIDTH_ROUTES = ["/styleguide"];

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
  const focus = FOCUS_ROUTES.some((p) => matchPath(p, location.pathname));
  const fullWidth = FULL_WIDTH_ROUTES.some((p) => matchPath(p, location.pathname));
  const reviews = useReviewSummary();

  const page = (
    <Suspense
      fallback={
        <div className="page-loading">
          <Dia mood="thinking" size={72} />
          Loading…
        </div>
      }
    >
      <Outlet key={location.pathname} />
    </Suspense>
  );

  if (focus) {
    return (
      <div className="app app-focus">
        <main className="focus-main" id="main">
          {page}
        </main>
      </div>
    );
  }

  return (
    <div className={cx("app", fullWidth && "app-full")}>
      <Sidebar reviewsDue={reviews?.dueNow ?? 0} />
      <MobileTopBar />
      <main className="main" id="main">
        {page}
      </main>
      {!fullWidth && (
        <aside className="rail" aria-label="Your progress">
          <ProgressPanel />
        </aside>
      )}
      <TabBar reviewsDue={reviews?.dueNow ?? 0} />
    </div>
  );
}

const NAV: { to: string; label: string; emoji: EmojiName; end: boolean }[] = [
  { to: "/", label: "Home", emoji: "house", end: true },
  { to: "/library", label: "Library", emoji: "card-file-box", end: false },
  { to: "/explore", label: "Explore", emoji: "globe-showing-asia-australia", end: false },
  { to: "/review", label: "Review", emoji: "card-index", end: false },
];

function Sidebar({ reviewsDue }: { reviewsDue: number }) {
  return (
    <nav className="sidebar" aria-label="Main">
      <Link to="/" className="sidebar-brand" aria-label="Ved home">
        <Wordmark />
      </Link>
      <div className="sidebar-nav">
        {NAV.map(({ to, label, emoji, end }) => (
          <NavLink key={to} to={to} end={end} className={({ isActive }) => cx("nav-item", isActive && "active")}>
            <Emoji name={emoji} size={30} />
            <span>{label}</span>
            {to === "/review" && reviewsDue > 0 && <span className="nav-count">{reviewsDue}</span>}
          </NavLink>
        ))}
      </div>
      <div className="sidebar-foot">
        <ThemeSwitch />
        <UserMenu placement="up" />
      </div>
    </nav>
  );
}

function MobileTopBar() {
  const { summary } = useProgress();
  return (
    <header className="mobile-top">
      <Link to="/" aria-label="Ved home">
        <Wordmark />
      </Link>
      <div className="row" style={{ gap: "var(--space-1)" }}>
        {summary && (
          <>
            <Chip emoji="fire" tone="streak" label={`${summary.streak.current} day streak`}>
              {summary.streak.current}
            </Chip>
            <Chip emoji="high-voltage" tone="xp" label={`${summary.xp.total} XP`}>
              {summary.xp.total}
            </Chip>
          </>
        )}
        <ThemeToggle />
        <UserMenu placement="down" compact />
      </div>
    </header>
  );
}

function TabBar({ reviewsDue }: { reviewsDue: number }) {
  return (
    <nav className="tabbar" aria-label="Main">
      {NAV.map(({ to, label, emoji, end }) => (
        <NavLink key={to} to={to} end={end} className={({ isActive }) => cx("tab-item", isActive && "active")}>
          <span className="tab-icon">
            <Emoji name={emoji} size={28} />
            {to === "/review" && reviewsDue > 0 && <span className="nav-count nav-count-dot">{reviewsDue}</span>}
          </span>
          <span>{label}</span>
        </NavLink>
      ))}
    </nav>
  );
}

function UserMenu({ placement, compact }: { placement: "up" | "down"; compact?: boolean }) {
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

  if (!user) return null;
  return (
    <div className="user-menu" ref={ref}>
      <button className={cx("user-button", compact && "user-button-compact")} aria-haspopup="menu" aria-expanded={open} aria-label="Account menu" onClick={() => setOpen((o) => !o)}>
        <Avatar seed={user.id} size={compact ? 32 : 40} />
        {!compact && (
          <span className="user-button-text">
            <strong>{user.fullName}</strong>
            <span className="xs muted">{user.role === "TEACHER" ? "Teacher" : "Student"}</span>
          </span>
        )}
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            className={cx("menu", placement === "up" && "menu-up")}
            role="menu"
            initial={{ opacity: 0, y: placement === "up" ? 6 : -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: placement === "up" ? 6 : -6 }}
            transition={{ duration: 0.15 }}
          >
            <div className="menu-profile">
              <Avatar seed={user.id} size={44} />
              <div style={{ minWidth: 0 }}>
                <strong className="break">{user.fullName}</strong>
                <div className="xs muted break">{user.email}</div>
              </div>
            </div>
            <div className="menu-separator" />
            <Link to="/welcome" className="menu-item" role="menuitem" onClick={() => setOpen(false)}>
              <Target size={18} aria-hidden /> Daily goal
            </Link>
            <Link to="/styleguide" className="menu-item" role="menuitem" onClick={() => setOpen(false)}>
              <Palette size={18} aria-hidden /> Design system
            </Link>
            <button className="menu-item" role="menuitem" onClick={() => void logout()}>
              <LogOut size={18} aria-hidden /> Sign out
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
