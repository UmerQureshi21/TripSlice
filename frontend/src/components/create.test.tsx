import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from "axios";

vi.mock("../services/videoService", () => ({ getAllVideos: vi.fn() }));
vi.mock("../services/montageService", () => ({ createMontage: vi.fn() }));
let progressCallback: ((body: { content: string; montagePath: string | null }) => void) | null = null;
const socket = { activate: vi.fn(), deactivate: vi.fn() };
vi.mock("../services/progressSocket", () => ({
  createProgressClient: (_q: string, cb: typeof progressCallback) => {
    progressCallback = cb;
    return socket;
  },
}));

import CreateMontagePage from "../pages/CreateMontagePage";
import MontageProgressWebSocket from "./MontageProcessWebSocket";
import FinishedMontage from "./FinishedMontage";
import { getAllVideos } from "../services/videoService";
import { createMontage } from "../services/montageService";
import { sampleMontage, sampleVideo } from "../test/utils";

const videos = [1, 2, 3, 4, 5, 6].map((n) => sampleVideo(n));

async function setup() {
  render(<CreateMontagePage />);
  await screen.findByText("clip-1.mp4");
}

function fill(topic: string, title: string) {
  fireEvent.change(screen.getAllByLabelText("Topic")[0], { target: { value: topic } });
  fireEvent.change(screen.getAllByLabelText("Title")[0], { target: { value: title } });
}

const select = (n: number) => fireEvent.click(screen.getByText(`clip-${n}.mp4`));

beforeEach(() => {
  vi.mocked(getAllVideos).mockResolvedValue(videos);
});

describe("Create montage", () => {
  it("counts selections and refuses a sixth video", async () => {
    await setup();
    expect(screen.getByText("0 of 5")).toBeInTheDocument();
    [1, 2, 3, 4, 5].forEach(select);
    expect(screen.getByText("5 of 5")).toBeInTheDocument();
    select(6);
    expect(screen.getByRole("alert")).toHaveTextContent("A montage can use at most 5 videos.");
    expect(screen.getByText("5 of 5")).toBeInTheDocument();
    select(5); // unselecting clears the warning
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.getByText("4 of 5")).toBeInTheDocument();
  });

  it("keeps Generate disabled until topic, title and a video are set, and counts characters", async () => {
    await setup();
    const [button] = screen.getAllByRole("button", { name: "Generate Montage!" });
    expect(button).toBeDisabled();
    fill("beach", "Summer");
    expect(screen.getByText(/topic 5\/100 · title 6\/60/)).toBeInTheDocument();
    expect(button).toBeDisabled();
    select(1);
    expect(button).toBeEnabled();
    expect(screen.getAllByLabelText("Topic")[0]).toHaveAttribute("maxLength", "100");
  });

  it("shows the finished montage and lets you start another", async () => {
    vi.mocked(createMontage).mockResolvedValue(sampleMontage("Summer"));
    await setup();
    fill("beach", "Summer");
    select(1);
    fireEvent.click(screen.getAllByRole("button", { name: "Generate Montage!" })[0]);
    expect(await screen.findByText("Summer has been created!")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Create Another Montage!"));
    expect(screen.getAllByRole("button", { name: "Generate Montage!" })[0]).toBeInTheDocument();
  });

  it("shows the server's reason, then returns to the form with it still visible", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig;
    vi.mocked(createMontage).mockRejectedValue(
      new AxiosError("x", "ERR", config, null, {
        status: 422, statusText: "", headers: {}, config,
        data: { error: 'No moments matching "beach" were found in the selected videos.' },
      })
    );
    vi.spyOn(console, "error").mockImplementation(() => {});
    await setup();
    fill("beach", "Summer");
    select(1);
    fireEvent.click(screen.getAllByRole("button", { name: "Generate Montage!" })[1]);
    expect(await screen.findByText("Taking you back to the create page...")).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(3000));
    expect(screen.getByRole("alert")).toHaveTextContent('No moments matching "beach"');
    vi.useRealTimers();
  });

  it("explains that a montage may still finish when the connection drops", async () => {
    const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig;
    vi.mocked(createMontage).mockRejectedValueOnce(new AxiosError("Network Error", "ERR_NETWORK", config));
    await setup();
    fill("beach", "Summer");
    select(1);
    fireEvent.click(screen.getAllByRole("button", { name: "Generate Montage!" })[0]);
    expect(await screen.findByText(/It may still finish, so check your Montage Library/)).toBeInTheDocument();
  });

  it("falls back to a generic message", async () => {
    vi.mocked(createMontage).mockRejectedValueOnce(new Error("network")).mockResolvedValueOnce(undefined as never);
    vi.spyOn(console, "error").mockImplementation(() => {});
    await setup();
    fill("beach", "Summer");
    select(1);
    fireEvent.click(screen.getAllByRole("button", { name: "Generate Montage!" })[0]);
    expect(await screen.findByText(/Something went wrong while creating the montage. Please try again./)).toBeInTheDocument();
  });

  it("handles an empty response and an empty library", async () => {
    vi.mocked(createMontage).mockResolvedValueOnce(undefined as never);
    const { unmount } = render(<CreateMontagePage />);
    await screen.findByText("clip-1.mp4");
    fill("beach", "Summer");
    select(1);
    fireEvent.click(screen.getAllByRole("button", { name: "Generate Montage!" })[0]);
    expect(await screen.findByText("Something went wrong while creating the montage.")).toBeInTheDocument();
    unmount();

    vi.mocked(getAllVideos).mockRejectedValueOnce(new Error("down")).mockResolvedValueOnce([]);
    render(<CreateMontagePage />);
    // A failed load is an error with a retry, not an empty library
    expect(await screen.findByText("Your videos couldn't be loaded.")).toBeInTheDocument();
    expect(screen.queryByText(/No videos have been uploaded yet/)).toBeNull();
    fireEvent.click(screen.getByText("Try again"));
    await waitFor(() => expect(screen.getByText(/No videos have been uploaded yet/)).toBeInTheDocument());
  });
});

describe("MontageProgressWebSocket", () => {
  it("advances through the steps as messages arrive", () => {
    vi.spyOn(console, "log").mockImplementation(() => {});
    const { unmount } = render(<MontageProgressWebSocket />);
    expect(screen.getByText("0%")).toBeInTheDocument();
    act(() => progressCallback!({ content: "Successfully extracted beach from a.mp4", montagePath: null }));
    act(() => progressCallback!({ content: "unrelated", montagePath: null }));
    act(() => progressCallback!({ content: "Finished trimming videos...", montagePath: null }));
    expect(screen.getByText("25%")).toBeInTheDocument();
    act(() => progressCallback!({ content: "Combining videos...", montagePath: null }));
    expect(screen.getByText("50%")).toBeInTheDocument();
    act(() => progressCallback!({ content: "Summer created!", montagePath: "https://s3/m.mp4" }));
    expect(screen.getByText("100%")).toBeInTheDocument();
    expect(screen.getByText("Combining videos...")).toBeInTheDocument(); // live log
    unmount();
    expect(socket.deactivate).toHaveBeenCalled();
  });
});

describe("FinishedMontage", () => {
  it("retries loading the video until it plays", () => {
    vi.useFakeTimers();
    vi.spyOn(console, "log").mockImplementation(() => {});
    const { container } = render(<FinishedMontage preSignedUrl="https://s3/m.mp4" progressMessage="Done!" />);
    const first = container.querySelector("video")!;
    act(() => vi.advanceTimersByTime(2000));
    expect(container.querySelector("video")).not.toBe(first); // remounted to retry
    fireEvent.error(container.querySelector("video")!);
    fireEvent.play(container.querySelector("video")!);
    const playing = container.querySelector("video");
    act(() => vi.advanceTimersByTime(4000));
    expect(container.querySelector("video")).toBe(playing);
    vi.useRealTimers();
  });

  it("stops retrying after a while and points to the library", () => {
    vi.useFakeTimers();
    render(<FinishedMontage preSignedUrl="https://s3/broken.mp4" progressMessage="Done!" />);
    act(() => vi.advanceTimersByTime(19000));
    expect(screen.queryByRole("alert")).toBeNull();
    act(() => vi.advanceTimersByTime(2000));
    expect(screen.getByRole("alert")).toHaveTextContent("Your montage is saved");
    expect(vi.getTimerCount()).toBe(0); // no more reloading
    vi.useRealTimers();
  });
});
