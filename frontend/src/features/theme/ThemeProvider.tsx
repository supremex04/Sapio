import { useEffect, useMemo, useState, type ReactNode } from "react";
import { applyTheme, readPreference, resolveTheme, writePreference, type ThemePreference } from "./theme";
import { ThemeContext } from "./ThemeContext";

const darkQuery = () => window.matchMedia("(prefers-color-scheme: dark)");

/** Light / dark / system theming. index.html applies the stored theme before first paint. */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(readPreference);
  const [systemDark, setSystemDark] = useState(() => darkQuery().matches);

  useEffect(() => {
    const mq = darkQuery();
    const onChange = (e: MediaQueryListEvent) => setSystemDark(e.matches);
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);

  const resolved = resolveTheme(preference, systemDark);
  useEffect(() => applyTheme(resolved), [resolved]);

  const value = useMemo(
    () => ({
      preference,
      resolved,
      setPreference: (pref: ThemePreference) => {
        writePreference(pref);
        setPreferenceState(pref);
      },
    }),
    [preference, resolved],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
