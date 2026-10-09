import { Monitor, Moon, Sun } from "lucide-react";
import { IconButton } from "../../shared/ui/Button";
import type { ThemePreference } from "./theme";
import { useTheme } from "./ThemeContext";

const ORDER: ThemePreference[] = ["light", "dark", "system"];
const META = {
  light: { icon: Sun, label: "Light theme" },
  dark: { icon: Moon, label: "Dark theme" },
  system: { icon: Monitor, label: "System theme" },
} as const;

/** Cycles light → dark → system. */
export function ThemeToggle() {
  const { preference, setPreference } = useTheme();
  const { icon: Icon, label } = META[preference];
  const next = ORDER[(ORDER.indexOf(preference) + 1) % ORDER.length] ?? "system";
  return (
    <IconButton label={`${label} (switch to ${META[next].label.toLowerCase()})`} onClick={() => setPreference(next)}>
      <Icon size={18} />
    </IconButton>
  );
}
