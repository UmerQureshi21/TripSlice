import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";

vi.mock("../services/videoService", () => ({
  getAllVideos: vi.fn(),
  uploadVideos: vi.fn(),
  deleteVideo: vi.fn(),
  clearVideoCache: vi.fn(),
}));
vi.mock("../limits", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../limits")>()),
  validateUpload: vi.fn(),
}));
let progressCallback: ((body: { content: string; montagePath: string | null }) => void) | null = null;
const socket = { activate: vi.fn(), deactivate: vi.fn() };
vi.mock("../services/progressSocket", () => ({
  createProgressClient: (_queue: string, cb: typeof progressCallback) => {
    progressCallback = cb;
    return socket;
  },
}));

import Upload from "./Upload";
import UploadProgressWebSocket from "./UploadProgressWebSocket";
import VideoLibraryPage from "../pages/VideoLibraryPage";
import { deleteVideo, getAllVideos, uploadVideos } from "../services/videoService";
import { validateUpload } from "../limits";
import { sampleVideo, videoFile } from "../test/utils";

function pick(files: File[]) {
  fireEvent.change(screen.getByLabelText("Upload videos"), { target: { files } });
}

beforeEach(() => {
  vi.mocked(getAllVideos).mockResolvedValue([sampleVideo(1), sampleVideo(2, false)]);
});

describe("Upload", () => {
  it("lists the library and the upload rules", async () => {
    render(<VideoLibraryPage />);
    expect(await screen.findByText("clip-1.mp4")).toBeInTheDocument();
    expect(screen.getByText(/Each video must be 3 seconds to 2 minutes long/)).toHaveTextContent("You have 2 of 15.");
    expect(screen.getByText("Not searchable yet")).toBeInTheDocument();
  });

  it("shows every problem and uploads nothing when the selection is invalid", async () => {
    vi.mocked(validateUpload).mockResolvedValue(['"long.mp4" is 3m 0s long. Videos can be at most 2 minutes.']);
    render(<Upload />);
    await screen.findByText("clip-1.mp4");
    pick([videoFile("long.mp4")]);
    expect(await screen.findByRole("alert")).toHaveTextContent("Nothing was uploaded");
    expect(screen.getByRole("alert")).toHaveTextContent("Videos can be at most 2 minutes.");
    expect(uploadVideos).not.toHaveBeenCalled();
  });

  it("uploads a valid selection, shows progress, then refreshes", async () => {
    vi.mocked(validateUpload).mockResolvedValue([]);
    let finish: (v: string | null) => void = () => {};
    vi.mocked(uploadVideos).mockReturnValue(new Promise((r) => (finish = r)));
    render(<Upload />);
    await screen.findByText("clip-1.mp4");
    pick([videoFile("new.mp4")]);
    expect(await screen.findByText("Upload Progress")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Uploading/ })).toBeDisabled();
    await act(async () => finish(null));
    await waitFor(() => expect(getAllVideos).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("shows the server's message when an upload fails", async () => {
    vi.mocked(validateUpload).mockResolvedValue([]);
    vi.mocked(uploadVideos).mockResolvedValue("TripSlice has reached its video processing limit for now.");
    render(<Upload />);
    await screen.findByText("clip-1.mp4");
    pick([videoFile("new.mp4")]);
    expect(await screen.findByRole("alert")).toHaveTextContent("processing limit");
  });

  it("ignores an empty pick, and shows an error with a retry when the library won't load", async () => {
    vi.mocked(getAllVideos).mockRejectedValueOnce(new Error("down"));
    render(<Upload />);
    pick([]);
    expect(validateUpload).not.toHaveBeenCalled();
    expect(await screen.findByRole("alert")).toHaveTextContent("Your videos couldn't be loaded.");
    expect(screen.queryByText(/No videos have been uploaded yet/)).toBeNull();
    fireEvent.click(screen.getByText("Try again"));
    expect(await screen.findByText("clip-1.mp4")).toBeInTheDocument();
  });

  it("deletes a video after confirming, then reloads the library", async () => {
    vi.mocked(getAllVideos).mockResolvedValue([{ ...sampleVideo(1), id: 11 }]);
    vi.mocked(deleteVideo).mockResolvedValue(null);
    render(<Upload />);
    fireEvent.click(await screen.findByLabelText("Delete video"));
    expect(screen.getByText("Delete this video?")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Yes, delete"));
    await waitFor(() => expect(deleteVideo).toHaveBeenCalledWith(11));
    await waitFor(() => expect(getAllVideos).toHaveBeenCalledTimes(2));
  });
});

describe("UploadProgressWebSocket", () => {
  it("moves each video through its stages", () => {
    const { unmount } = render(<UploadProgressWebSocket fileNames={["a.mp4", "b.mp4"]} />);
    expect(socket.activate).toHaveBeenCalled();
    expect(screen.getByText("0%")).toBeInTheDocument();
    act(() => progressCallback!({ content: "a.mp4", montagePath: "indexing" }));
    expect(screen.getByText("17%")).toBeInTheDocument();
    act(() => progressCallback!({ content: "a.mp4", montagePath: "saved" }));
    act(() => progressCallback!({ content: "unknown.mp4", montagePath: "saved" }));
    act(() => progressCallback!({ content: "b.mp4", montagePath: null }));
    expect(screen.getByText("50%")).toBeInTheDocument();
    act(() => progressCallback!({ content: "b.mp4", montagePath: "saved" }));
    expect(screen.getByText("100%")).toBeInTheDocument();
    unmount();
    expect(socket.deactivate).toHaveBeenCalled();
  });
});
