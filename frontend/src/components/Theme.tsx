import { useEffect } from "react";
import { useLocation } from "react-router";
import { applyTheme, themeFor, useTheme } from "../theme";

/** Keeps the page's colours in step with the chosen theme as you move between pages. */
export function ThemeSync() {
  const { pathname } = useLocation();
  const [theme] = useTheme();
  useEffect(() => applyTheme(themeFor(pathname, theme)), [pathname, theme]);
  return null;
}

/** One click between the light and dark palettes. */
export function ThemeToggle({ className = "" }: { className?: string }) {
  const [theme, setTheme] = useTheme();
  const next = theme === "dark" ? "light" : "dark";
  return (
    <button
      type="button"
      onClick={() => setTheme(next)}
      aria-label={`Switch to ${next} mode`}
      title={`Switch to ${next} mode`}
      className={`grid place-items-center w-[38px] h-[38px] rounded-full text-ink/70 hover:text-ink hover:bg-ink/10 transition-colors cursor-pointer ${className}`}
    >
      <svg viewBox="0 0 24 24" className="w-[18px] h-[18px]" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        {theme === "dark" ? (
          // Sun: switch to light
          <>
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
          </>
        ) : (
          // Moon: switch to dark
          <path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" />
        )}
      </svg>
    </button>
  );
}
