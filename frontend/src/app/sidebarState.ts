/** Below this width (and above the 1024px desktop breakpoint) the sidebar starts collapsed unless the user chose. */
export const AUTO_COLLAPSE_BELOW = 1440;
export const STORAGE_KEY = "sapio.sidebar";

export type SidebarPreference = "collapsed" | "expanded" | null;

/** The user's saved choice wins; without one, medium screens get the icon strip and wide screens the full sidebar. */
export function isCollapsed(preference: SidebarPreference, viewportWidth: number): boolean {
  if (preference) return preference === "collapsed";
  return viewportWidth < AUTO_COLLAPSE_BELOW;
}

/** The interface zoom (--ui-zoom, 0.8 on desktop): layout widths are the viewport divided by it. */
export function uiZoom(): number {
  const value = parseFloat(getComputedStyle(document.documentElement).getPropertyValue("--ui-zoom"));
  return value > 0 ? value : 1;
}

/** Viewport width in the interface's own (zoomed) CSS pixels. */
export const layoutWidth = () => window.innerWidth / uiZoom();

export function readPreference(): SidebarPreference {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY);
    return value === "collapsed" || value === "expanded" ? value : null;
  } catch {
    return null; // storage blocked (private mode, policies): fall back to the width rule
  }
}

export function savePreference(value: Exclude<SidebarPreference, null>): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, value);
  } catch {
    // not persisted; the choice still applies for this visit
  }
}
