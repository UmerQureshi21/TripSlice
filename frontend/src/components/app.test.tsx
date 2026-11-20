import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from "axios";

vi.mock("../services/refreshTokenAxios", () => ({ default: { get: vi.fn() } }));
vi.mock("../services/searchService", () => ({ search: vi.fn() }));
vi.mock("../services/montageService", () => ({ getAllMontages: vi.fn(), deleteMontage: vi.fn(), clearMontageCache: vi.fn() }));
vi.mock("../services/videoService", () => ({ getAllVideos: vi.fn(), clearVideoCache: vi.fn() }));
vi.mock("../services/userService", () => ({ logout: vi.fn(), restoreSession: vi.fn(), logInOrSignUp: vi.fn() }));
vi.mock("../services/progressSocket", () => ({ createProgressClient: () => ({ activate: vi.fn(), deactivate: vi.fn() }) }));

import axiosInstance from "../services/refreshTokenAxios";
import { search } from "../services/searchService";
import { deleteMontage, getAllMontages } from "../services/montageService";
import { getAllVideos } from "../services/videoService";
import { logout, restoreSession } from "../services/userService";
import { tokenManager } from "../services/tokenManager";
import App from "../App";
import SearchPage from "../pages/SearchPage";
import MontagePage from "../pages/MontageLibraryPage";
import DashBoardPage from "../pages/DashBoardPage";
import NavBar from "./NavBar";
import PromptSuggestion from "./PromptSuggestions";
import FileDetails from "./VideoDetails";
import { renderWithRouter, sampleMontage, sampleVideo, setWidth } from "../test/utils";

const result = (id: number, kind: "VIDEO" | "MONTAGE", prompt: string | null = null) => ({
  kind, id, name: `result-${id}`, url: `https://s3/${id}.mp4`, summary: "A sunny beach.", prompt, score: 0.82,
});

describe("Search", () => {
  it("searches, shows match scores and re-runs when the type changes", async () => {
    vi.mocked(search).mockResolvedValueOnce([result(1, "VIDEO")]).mockResolvedValueOnce([result(2, "MONTAGE", "sunset")]);
    render(<SearchPage />);
    const button = screen.getByRole("button", { name: "Search" });
    expect(button).toBeDisabled();
    fireEvent.change(screen.getByPlaceholderText("warm beach at sunset"), { target: { value: " beach " } });
    fireEvent.click(button);
    expect(await screen.findByText("82% match")).toBeInTheDocument();
    expect(search).toHaveBeenCalledWith("beach", "videos");

    fireEvent.click(screen.getByRole("button", { name: "montages" }));
    expect(await screen.findByText('82% match · "sunset"')).toBeInTheDocument();
    expect(search).toHaveBeenLastCalledWith("beach", "montages");
  });

  it("explains empty results and failures, using the server's reason when there is one", async () => {
    const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig;
    vi.mocked(search)
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(new Error("down"))
      .mockRejectedValueOnce(new AxiosError("x", "ERR", config, null, {
        status: 429, statusText: "", headers: {}, config,
        data: { error: "You've reached today's search limit. Please try again tomorrow." },
      }));
    render(<SearchPage />);
    // Changing type before searching doesn't search
    fireEvent.click(screen.getByRole("button", { name: "montages" }));
    expect(search).not.toHaveBeenCalled();
    fireEvent.submit(screen.getByPlaceholderText("warm beach at sunset").closest("form")!);
    expect(search).not.toHaveBeenCalled();
    fireEvent.change(screen.getByPlaceholderText("warm beach at sunset"), { target: { value: "boats" } });
    fireEvent.click(screen.getByRole("button", { name: "Search" }));
    expect(await screen.findByText(/No montages matched/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Search" }));
    expect(await screen.findByText("Search failed. Please try again.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Search" }));
    expect(await screen.findByText("You've reached today's search limit. Please try again tomorrow.")).toBeInTheDocument();
  });
});

describe("Montage library", () => {
  it("lists montages and expands their source videos", async () => {
    vi.mocked(getAllMontages).mockResolvedValue([sampleMontage("Paris", 7)]);
    render(<MontagePage />);
    expect(await screen.findByText("Paris")).toBeInTheDocument();
    expect(screen.getByText("Created On: 2026-05-01")).toBeInTheDocument();
    expect(screen.getByText("Time: 7")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Videos that this montage parsed from"));
    fireEvent.mouseEnter(document.querySelector("video")!.parentElement!);
    fireEvent.mouseLeave(document.querySelector("video")!.parentElement!);
    expect(document.querySelectorAll("video").length).toBe(2);
  });

  it("shows an error, not an empty library, when loading fails, and can retry", async () => {
    vi.mocked(getAllMontages).mockRejectedValueOnce(new Error("down")).mockResolvedValueOnce([]);
    render(<MontagePage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Your montages couldn't be loaded.");
    expect(screen.queryByText(/No montages have been made yet/)).toBeNull();
    fireEvent.click(screen.getByText("Try again"));
    expect(await screen.findByText(/No montages have been made yet/)).toBeInTheDocument();
  });

  it("deletes a montage after confirming, and shows why if it fails", async () => {
    vi.mocked(getAllMontages).mockResolvedValue([{ ...sampleMontage("Paris"), id: 7 }]);
    vi.mocked(deleteMontage).mockResolvedValueOnce("That montage couldn't be found. It may already be deleted.").mockResolvedValueOnce(null);
    render(<MontagePage />);
    fireEvent.click(await screen.findByLabelText("Delete montage"));
    fireEvent.click(screen.getByText("Cancel"));
    expect(deleteMontage).not.toHaveBeenCalled();
    fireEvent.click(screen.getByLabelText("Delete montage"));
    fireEvent.click(screen.getByText("Yes, delete"));
    expect(await screen.findByRole("alert")).toHaveTextContent("It may already be deleted.");
    fireEvent.click(screen.getByLabelText("Delete montage"));
    fireEvent.click(screen.getByText("Yes, delete"));
    await waitFor(() => expect(getAllMontages).toHaveBeenCalledTimes(2));
    expect(deleteMontage).toHaveBeenCalledWith(7);
  });
});

describe("Dashboard", () => {
  beforeEach(() => {
    vi.mocked(axiosInstance.get).mockImplementation(async (url: string) => {
      const data: Record<string, unknown> = {
        "/user": { username: "a@b.co", displayedName: "Umer" },
        "/videos": [sampleVideo(1), sampleVideo(2)],
        "/montages": [sampleMontage("A")],
        "/montages/longest": { ...sampleMontage("Longest one"), videos: undefined },
        "montages/most-vids": sampleMontage("Most videos"),
      };
      return { data: data[url] };
    });
  });

  it("greets the user and shows stats and highlights", async () => {
    renderWithRouter(<DashBoardPage />);
    expect(await screen.findByText("Umer")).toBeInTheDocument();
    expect(await screen.findByText("Longest one")).toBeInTheDocument();
    expect(screen.getByText("Most videos")).toBeInTheDocument();
    expect(screen.getByText("Total Videos").nextSibling).toHaveTextContent("2");
    expect(screen.getByText("Total Montages").nextSibling).toHaveTextContent("1");
  });

  it("shows errors, not made-up numbers, when the dashboard can't load", async () => {
    vi.mocked(axiosInstance.get).mockRejectedValue(new Error("down"));
    renderWithRouter(<DashBoardPage />);
    expect(await screen.findByText("Your stats couldn't be loaded.")).toBeInTheDocument();
    expect(screen.queryByText("Total Videos")).toBeNull();
    expect(screen.getByText("Your montage highlights couldn't be loaded.")).toBeInTheDocument();
    expect(screen.getByText("there")).toBeInTheDocument(); // "Hi, there" when the name can't load

    vi.mocked(axiosInstance.get).mockImplementation(async (url: string) => ({ data: url === "/videos" ? [sampleVideo(1)] : [] }));
    fireEvent.click(screen.getAllByText("Try again")[0]);
    expect(await screen.findByText("Total Videos")).toBeInTheDocument();
  });

  it("Create Montage! goes to the create page", async () => {
    render(
      <MemoryRouter initialEntries={["/app/dashboard"]}>
        <Routes>
          <Route path="/app/dashboard" element={<DashBoardPage />} />
          <Route path="/app/create" element={<p>create page</p>} />
        </Routes>
      </MemoryRouter>
    );
    fireEvent.click(await screen.findByText("Create Montage!"));
    expect(screen.getByText("create page")).toBeInTheDocument();
  });
});

describe("PromptSuggestion", () => {
  it("suggests a prompt and copies it", async () => {
    vi.useFakeTimers();
    const writeText = vi.fn();
    Object.defineProperty(navigator, "clipboard", { configurable: true, value: { writeText } });
    render(<PromptSuggestion />);
    fireEvent.click(screen.getByText("Generate Suggestion (BETA)"));
    expect(screen.getByText("Generating...")).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(300));
    fireEvent.click(screen.getByText("Copy Prompt"));
    expect(writeText).toHaveBeenCalledWith(expect.any(String));
    vi.useRealTimers();
  });
});

describe("VideoDetails", () => {
  it("plays on hover and shows the length", () => {
    const { container } = render(<FileDetails name="a.mp4" size={0} type="" videoSrc="x" searchable />);
    const video = container.querySelector("video")!;
    Object.defineProperty(video, "duration", { value: 75 });
    fireEvent.loadedMetadata(video);
    expect(screen.getByText("1:15")).toBeInTheDocument();
    fireEvent.mouseEnter(container.firstChild as Element);
    expect(HTMLMediaElement.prototype.play).toHaveBeenCalled();
    fireEvent.mouseLeave(container.firstChild as Element);
    expect(screen.getByText("Searchable")).toBeInTheDocument();
  });
});

describe("Navigation", () => {
  it("shows links on wide screens and logs out", async () => {
    setWidth(1200);
    vi.mocked(logout).mockResolvedValue();
    render(
      <MemoryRouter initialEntries={["/app/videos"]}>
        <Routes>
          <Route path="/app/*" element={<NavBar />} />
          <Route path="/" element={<p>home</p>} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByText("Video Library").closest("a")).toHaveClass("active");
    fireEvent.click(screen.getByText("Log Out"));
    expect(await screen.findByText("home")).toBeInTheDocument();
  });

  it("uses a slide-out menu on small screens that locks scrolling", async () => {
    setWidth(400);
    vi.mocked(logout).mockResolvedValue();
    renderWithRouter(<NavBar />, "/app/dashboard");
    const close = screen.getByLabelText("Close menu");
    const open = screen.getByLabelText("Open menu");
    fireEvent.click(open);
    expect(document.body.style.position).toBe("fixed");
    fireEvent.click(close);
    expect(document.body.style.position).toBe("");
    fireEvent.click(open);
    fireEvent.click(screen.getAllByText("Search")[0]);
    expect(document.body.style.position).toBe("");
    fireEvent.click(open);
    fireEvent.click(screen.getByText("Log Out"));
    await waitFor(() => expect(document.body.style.position).toBe(""));
    act(() => setWidth(390));
  });
});

describe("App", () => {
  it("restores the session, then sends logged out users to the login page", async () => {
    vi.mocked(restoreSession).mockResolvedValue(false);
    tokenManager.clearToken();
    window.history.pushState({}, "", "/app/dashboard");
    render(<App />);
    expect(screen.getByText("Loading...")).toBeInTheDocument();
    expect(await screen.findByText("Welcome back")).toBeInTheDocument();
  });

  it("lets logged in users into the app and shows 404s", async () => {
    vi.mocked(restoreSession).mockResolvedValue(true);
    vi.mocked(getAllVideos).mockResolvedValue([]);
    vi.mocked(axiosInstance.get).mockResolvedValue({ data: [] });
    tokenManager.setToken("Bearer t");
    setWidth(1200);
    window.history.pushState({}, "", "/app/videos");
    render(<App />);
    expect(await screen.findByText(/You have 0 of 15/)).toBeInTheDocument();
    tokenManager.clearToken();

    window.history.pushState({}, "", "/nowhere");
    render(<App />);
    expect(await screen.findByText("Oops! Page Not Found")).toBeInTheDocument();
  });
});
