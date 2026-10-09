import { Monitor, Moon, Sun } from "lucide-react";
import { IconButton } from "../../shared/ui/Button";
import type { ThemePreference } from "./theme";
import { useTheme } from "./ThemeContext";

const ORDER: ThemePreference[] = ["light", "dark", "system"];
const META = {
  light: { icon: Sun, label: "Light" },
  dark: { icon: Moon, label: "Dark" },
  system: { icon: Monitor, label: "System" },
} as const;

/** Compact: cycles light → dark → system. */
export function ThemeToggle() {
  const { preference, setPreference } = useTheme();
  const { icon: Icon, label } = META[preference];
  const next = ORDER[(ORDER.indexOf(preference) + 1) % ORDER.length] ?? "system";
  return (
    <IconButton label={`${label} theme (switch to ${META[next].label.toLowerCase()})`} onClick={() => setPreference(next)}>
      <Icon size={20} strokeWidth={2.5} />
    </IconButton>
  );
}

/** Explicit three-way switch (sidebar, settings, styleguide). */
export function ThemeSwitch() {
  const { preference, setPreference } = useTheme();
  return (
    <div className="theme-switch" role="radiogroup" aria-label="Theme">
      {ORDER.map((p) => {
        const { icon: Icon, label } = META[p];
        return (
          <button key={p} type="button" role="radio" aria-checked={preference === p} title={`${label} theme`} onClick={() => setPreference(p)}>
            <Icon size={16} strokeWidth={2.5} aria-hidden />
            <span>{label}</span>
          </button>
        );
      })}
    </div>
  );
}
