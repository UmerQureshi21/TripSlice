import { useCallback, useSyncExternalStore } from "react";

export type Theme = "light" | "dark";

// Also read by the inline script in index.html, so the first paint already has the right colours
export const THEME_KEY = "tripslice-theme";
const THEME_EVENT = "tripslice:theme";

export function storedTheme(): Theme {
  try {
    return localStorage.getItem(THEME_KEY) === "light" ? "light" : "dark";
  } catch {
    return "dark";
  }
}

/** The landing page is always dark; every other page follows the chosen theme. */
export function themeFor(pathname: string, chosen: Theme): Theme {
  return pathname === "/" ? "dark" : chosen;
}

export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme;
}

export function saveTheme(theme: Theme) {
  try {
    localStorage.setItem(THEME_KEY, theme);
  } catch {
    // Private mode or blocked storage: the switch still works until the page is reloaded
  }
  window.dispatchEvent(new Event(THEME_EVENT));
}

function subscribe(onChange: () => void) {
  window.addEventListener(THEME_EVENT, onChange);
  window.addEventListener("storage", onChange); // switched in another tab
  return () => {
    window.removeEventListener(THEME_EVENT, onChange);
    window.removeEventListener("storage", onChange);
  };
}

/** The chosen theme and a way to change it. */
export function useTheme(): [Theme, (t: Theme) => void] {
  const theme = useSyncExternalStore(subscribe, storedTheme);
  const set = useCallback((t: Theme) => saveTheme(t), []);
  return [theme, set];
}
