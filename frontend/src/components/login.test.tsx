import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";

vi.mock("../services/userService", () => ({ logInOrSignUp: vi.fn() }));

import { logInOrSignUp } from "../services/userService";
import LogInPage from "../pages/LogInPage";
import { setWidth } from "../test/utils";

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={["/account"]}>
      <Routes>
        <Route path="/account" element={<LogInPage />} />
        <Route path="/app/dashboard" element={<p>dashboard</p>} />
      </Routes>
    </MemoryRouter>
  );
}

const type = (placeholder: string, value: string, index = 0) =>
  fireEvent.change(screen.getAllByPlaceholderText(placeholder)[index], { target: { value } });
const submit = (label: "Login" | "Sign Up") => fireEvent.click(screen.getAllByRole("button", { name: label }).at(-1)!);

beforeEach(() => setWidth(500));

describe("Login", () => {
  it("validates before sending anything", async () => {
    renderLogin();
    submit("Login");
    expect(screen.getByText("Email is required")).toBeInTheDocument();
    expect(screen.getByText("Password is required")).toBeInTheDocument();
    type("you@example.com", "not-an-email");
    expect(screen.queryByText("Email is required")).toBeNull(); // typing clears the field's error
    submit("Login");
    expect(screen.getByText("Please enter a valid email")).toBeInTheDocument();
    expect(logInOrSignUp).not.toHaveBeenCalled();
  });

  it("logs in and goes to the dashboard", async () => {
    vi.mocked(logInOrSignUp).mockResolvedValue("");
    renderLogin();
    type("you@example.com", "a@b.co");
    type("••••••••", "short"); // existing accounts may have older, shorter passwords
    submit("Login");
    expect(await screen.findByText("dashboard")).toBeInTheDocument();
    expect(logInOrSignUp).toHaveBeenCalledWith(true, "a@b.co", "short", "", "");
  });

  it("shows the server's message", async () => {
    vi.mocked(logInOrSignUp).mockResolvedValueOnce("Invalid username or password.").mockRejectedValueOnce(new Error("x"));
    vi.spyOn(console, "error").mockImplementation(() => {});
    renderLogin();
    type("you@example.com", "a@b.co");
    type("••••••••", "password1");
    submit("Login");
    expect(await screen.findByText("Invalid username or password.")).toBeInTheDocument();
    submit("Login");
    expect(await screen.findByText("Something went wrong. Please try again.")).toBeInTheDocument();
  });
});

describe("Sign up", () => {
  it("checks name length, password rules and confirmation", () => {
    renderLogin();
    fireEvent.click(screen.getByRole("button", { name: "Sign Up" }));
    submit("Sign Up");
    expect(screen.getByText("Name is required")).toBeInTheDocument();
    expect(screen.getByText("Please confirm your password")).toBeInTheDocument();
    type("Goofy name here", "A");
    type("you@example.com", "a@b.co");
    type("••••••••", "short");
    type("••••••••", "different", 1);
    submit("Sign Up");
    expect(screen.getByText("Name must be 2 to 30 characters")).toBeInTheDocument();
    expect(screen.getByText("Password must be 8 to 72 characters")).toBeInTheDocument();
    expect(screen.getByText("Passwords do not match")).toBeInTheDocument();
  });

  it("creates the account with a trimmed name", async () => {
    vi.mocked(logInOrSignUp).mockResolvedValue("");
    renderLogin();
    fireEvent.click(screen.getByRole("button", { name: "Sign Up" }));
    type("Goofy name here", "  Umer  ");
    type("you@example.com", "a@b.co");
    type("••••••••", "password1");
    type("••••••••", "password1", 1);
    submit("Sign Up");
    expect(await screen.findByText("dashboard")).toBeInTheDocument();
    expect(logInOrSignUp).toHaveBeenCalledWith(false, "a@b.co", "password1", "Umer", "");
  });

  it("switching tabs clears errors", () => {
    renderLogin();
    submit("Login");
    fireEvent.click(screen.getByRole("button", { name: "Sign Up" }));
    expect(screen.queryByText("Email is required")).toBeNull();
    fireEvent.click(screen.getAllByRole("button", { name: "Login" })[0]);
    expect(screen.getByText("Welcome back")).toBeInTheDocument();
  });
});

describe("Login page layout", () => {
  it("adds the photo carousel on wide screens", () => {
    setWidth(1300);
    renderLogin();
    expect(screen.getByText(/Find the moments that/)).toBeInTheDocument();
    act(() => setWidth(600));
    expect(screen.queryByText(/Find the moments that/)).toBeNull();
  });
});

describe("Turnstile bot check", () => {
  it("is required on sign up when a site key is set", async () => {
    vi.resetModules();
    vi.stubEnv("VITE_TURNSTILE_SITE_KEY", "site-key");
    const render_ = vi.fn((_el: HTMLElement, options: Record<string, (t?: string) => void>) => {
      options.callback("tok-123");
      return "widget-1";
    });
    const remove = vi.fn();
    const scripts: HTMLScriptElement[] = [];
    const append = vi.spyOn(document.head, "appendChild").mockImplementation((node) => {
      scripts.push(node as HTMLScriptElement);
      window.turnstile = { render: render_, remove };
      setTimeout(() => (node as HTMLScriptElement).onload?.(new Event("load")));
      return node;
    });

    const { logInOrSignUp: mockedLogin } = await import("../services/userService");
    vi.mocked(mockedLogin).mockResolvedValue("");
    const { default: FreshLoginPage } = await import("../pages/LogInPage");
    const { unmount } = render(
      <MemoryRouter>
        <FreshLoginPage />
      </MemoryRouter>
    );
    fireEvent.click(screen.getByRole("button", { name: "Sign Up" }));
    await waitFor(() => expect(render_).toHaveBeenCalled());
    expect(scripts[0].src).toContain("challenges.cloudflare.com/turnstile");

    type("Goofy name here", "Umer");
    type("you@example.com", "a@b.co");
    type("••••••••", "password1");
    type("••••••••", "password1", 1);
    submit("Sign Up");
    await waitFor(() => expect(mockedLogin).toHaveBeenCalledWith(false, "a@b.co", "password1", "Umer", "tok-123"));

    unmount();
    expect(remove).toHaveBeenCalledWith("widget-1");
    append.mockRestore();
    vi.unstubAllEnvs();
    delete window.turnstile;
  });

  it("blocks sign up until the check is done, and recovers from a script failure", async () => {
    vi.resetModules();
    vi.stubEnv("VITE_TURNSTILE_SITE_KEY", "site-key");
    const append = vi.spyOn(document.head, "appendChild").mockImplementation((node) => {
      setTimeout(() => (node as HTMLScriptElement).onerror?.(new Event("error")));
      return node;
    });
    const { logInOrSignUp: mockedLogin } = await import("../services/userService");
    const { default: FreshLoginPage } = await import("../pages/LogInPage");
    render(
      <MemoryRouter>
        <FreshLoginPage />
      </MemoryRouter>
    );
    fireEvent.click(screen.getByRole("button", { name: "Sign Up" }));
    type("Goofy name here", "Umer");
    type("you@example.com", "a@b.co");
    type("••••••••", "password1");
    type("••••••••", "password1", 1);
    submit("Sign Up");
    expect(await screen.findByText("Please complete the verification check.")).toBeInTheDocument();
    expect(mockedLogin).not.toHaveBeenCalled();
    append.mockRestore();
    vi.unstubAllEnvs();
  });
});
