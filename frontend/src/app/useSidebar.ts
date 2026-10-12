import { useCallback, useEffect, useState } from "react";
import { isCollapsed, layoutWidth, readPreference, savePreference, type SidebarPreference } from "./sidebarState";

/** Collapsed / expanded sidebar: remembered in this browser, otherwise by screen width; toggled with "[". */
export function useSidebar() {
  const [preference, setPreference] = useState<SidebarPreference>(readPreference);
  const [width, setWidth] = useState(layoutWidth);
  const collapsed = isCollapsed(preference, width);

  useEffect(() => {
    const onResize = () => setWidth(layoutWidth());
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  const toggle = useCallback(() => {
    const next = collapsed ? "expanded" : "collapsed";
    savePreference(next);
    setPreference(next);
  }, [collapsed]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      const typing = target?.closest("input, textarea, select, [contenteditable='true']");
      if (e.key === "[" && !typing && !e.metaKey && !e.ctrlKey && !e.altKey) {
        e.preventDefault();
        toggle();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [toggle]);

  return { collapsed, toggle };
}
