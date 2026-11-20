import { afterEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useNavigate } from "react-router";
import { applyTheme, saveTheme, storedTheme, themeFor, THEME_KEY } from "./theme";
import { ThemeSync, ThemeToggle } from "./components/Theme";
import Header from "./components/Header";
import { setWidth } from "./test/utils";

afterEach(() => {
  localStorage.clear();
  delete document.documentElement.dataset.theme;
});

describe("theme storage", () => {
  it("defaults to dark and remembers light", () => {
    expect(storedTheme()).toBe("dark");
    saveTheme("light");
    expect(localStorage.getItem(THEME_KEY)).toBe("light");
    expect(storedTheme()).toBe("light");
  });

  it("keeps working when storage is blocked", () => {
    const get = vi.spyOn(localStorage, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    const set = vi.spyOn(localStorage, "setItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(storedTheme()).toBe("dark");
    expect(() => saveTheme("light")).not.toThrow();
    get.mockRestore();
    set.mockRestore();
  });

  it("keeps the landing page dark whatever was chosen", () => {
    expect(themeFor("/", "light")).toBe("dark");
    expect(themeFor("/app/videos", "light")).toBe("light");
    applyTheme("light");
    expect(document.documentElement.dataset.theme).toBe("light");
  });
});

describe("ThemeToggle and ThemeSync", () => {
  function Go({ to }: { to: string }) {
    const navigate = useNavigate();
    return <button onClick={() => navigate(to)}>go {to}</button>;
  }

  it("switches the app between light and dark, but not the landing page", () => {
    render(
      <MemoryRouter initialEntries={["/app/videos"]}>
        <ThemeSync />
        <ThemeToggle />
        <Routes>
          <Route path="/app/videos" element={<Go to="/" />} />
          <Route path="/" element={<Go to="/app/videos" />} />
        </Routes>
      </MemoryRouter>
    );
    const html = document.documentElement;
    expect(html.dataset.theme).toBe("dark");

    fireEvent.click(screen.getByLabelText("Switch to light mode"));
    expect(html.dataset.theme).toBe("light");
    expect(screen.getByLabelText("Switch to dark mode")).toBeInTheDocument();

    fireEvent.click(screen.getByText("go /"));
    expect(html.dataset.theme).toBe("dark"); // landing page
    fireEvent.click(screen.getByText("go /app/videos"));
    expect(html.dataset.theme).toBe("light"); // back in the app, choice remembered

    fireEvent.click(screen.getByLabelText("Switch to dark mode"));
    expect(html.dataset.theme).toBe("dark");
  });

  it("follows a change made in another tab", () => {
    render(
      <MemoryRouter initialEntries={["/app/search"]}>
        <ThemeSync />
      </MemoryRouter>
    );
    localStorage.setItem(THEME_KEY, "light");
    act(() => {
      window.dispatchEvent(new StorageEvent("storage", { key: THEME_KEY }));
    });
    expect(document.documentElement.dataset.theme).toBe("light");
  });
});

describe("Header fades", () => {
  it("fades the landing hero back to black at the bottom, and only there", () => {
    setWidth(1440);
    const { rerender } = render(<Header height={600} isHome={true} />);
    expect(screen.getByTestId("hero-bottom-fade")).toBeInTheDocument();
    rerender(<Header height={200} isHome={false} />);
    expect(screen.queryByTestId("hero-bottom-fade")).toBeNull();
  });

  it("hides the top fade in light mode", () => {
    setWidth(1440);
    const { container } = render(<Header height={200} isHome={false} />);
    expect(container.querySelector(".bg-gradient-to-b")).toHaveClass("light:hidden");
  });
});
