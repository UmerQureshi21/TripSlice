import { beforeEach, describe, expect, it, vi } from "vitest";
import axios, { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from "axios";
import axiosInstance from "./refreshTokenAxios";
import { tokenManager } from "./tokenManager";
import { logInOrSignUp, logout, restoreSession } from "./userService";
import { clearVideoCache, deleteVideo, getAllVideos, uploadVideos } from "./videoService";
import { clearMontageCache, createMontage, deleteMontage, getAllMontages } from "./montageService";
import { search } from "./searchService";
import { sampleMontage, sampleVideo, videoFile } from "../test/utils";

function axiosError(status: number | null, data?: unknown): AxiosError {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig;
  const response = status === null ? undefined : { status, data, statusText: "", headers: {}, config };
  return new AxiosError("failed", "ERR", config, null, response);
}

function fileList(files: File[]): FileList {
  return files as unknown as FileList;
}

beforeEach(() => {
  tokenManager.clearToken();
  clearVideoCache();
  clearMontageCache();
});

describe("tokenManager", () => {
  it("keeps the access token in memory only", () => {
    expect(tokenManager.hasToken()).toBe(false);
    tokenManager.setToken("Bearer a");
    expect(tokenManager.getToken()).toBe("Bearer a");
    expect(tokenManager.hasToken()).toBe(true);
    tokenManager.clearToken();
    expect(tokenManager.getToken()).toBeNull();
  });
});

describe("userService", () => {
  it("logs in and stores the token", async () => {
    const post = vi.spyOn(axios, "post").mockResolvedValue({ headers: { authorization: "Bearer t" } });
    expect(await logInOrSignUp(true, "a@b.co", "password1", "")).toBe("");
    expect(tokenManager.getToken()).toBe("Bearer t");
    expect(post.mock.calls[0][2]).toEqual({ withCredentials: true });
  });

  it("explains login failures", async () => {
    const post = vi.spyOn(axios, "post");
    post.mockRejectedValueOnce(axiosError(401));
    expect(await logInOrSignUp(true, "a@b.co", "x", "")).toBe("Invalid username or password.");
    post.mockRejectedValueOnce(axiosError(429));
    expect(await logInOrSignUp(true, "a@b.co", "x", "")).toBe("Too many requests. Please wait a minute and try again.");
    post.mockRejectedValueOnce(axiosError(null));
    expect(await logInOrSignUp(true, "a@b.co", "x", "")).toBe("Can't reach TripSlice right now. Check your connection and try again.");
    post.mockRejectedValueOnce(new Error("boom"));
    expect(await logInOrSignUp(true, "a@b.co", "x", "")).toBe("Login failed. Please try again.");
    post.mockRejectedValueOnce(axiosError(500, {}));
    expect(await logInOrSignUp(true, "a@b.co", "x", "")).toBe("Something went wrong on our side. Please try again in a moment.");
    // Spring's own error body ("Bad Request") never reaches the user
    post.mockRejectedValueOnce(axiosError(400, { error: "Bad Request", timestamp: "now", status: 400 }));
    expect(await logInOrSignUp(true, "a@b.co", "x", "")).toBe("Login failed. Please try again.");
  });

  it("signs up, sends the bot check token, then logs in", async () => {
    const post = vi.spyOn(axios, "post")
      .mockResolvedValueOnce({ data: {} })
      .mockResolvedValueOnce({ headers: { authorization: "Bearer new" } });
    expect(await logInOrSignUp(false, "a@b.co", "password1", "Al", "tok")).toBe("");
    expect(post.mock.calls[0][1]).toEqual({ username: "a@b.co", password: "password1", displayedName: "Al", turnstileToken: "tok" });
    expect(tokenManager.getToken()).toBe("Bearer new");
  });

  it("shows the server's sign up error", async () => {
    const post = vi.spyOn(axios, "post");
    post.mockRejectedValueOnce(axiosError(409, { error: "That email or display name is already in use." }));
    expect(await logInOrSignUp(false, "a@b.co", "password1", "Al")).toBe("That email or display name is already in use.");
    post.mockResolvedValueOnce({ data: {} }).mockRejectedValueOnce(new Error("down"));
    expect(await logInOrSignUp(false, "a@b.co", "password1", "Al")).toBe(
      "Your account was created, but logging in failed. Please log in."
    );
  });

  it("restores a session from the refresh cookie", async () => {
    const post = vi.spyOn(axios, "post").mockResolvedValueOnce({ headers: { authorization: "Bearer r" } });
    expect(await restoreSession()).toBe(true);
    post.mockResolvedValueOnce({ headers: {} });
    expect(await restoreSession()).toBe(false);
    post.mockRejectedValueOnce(axiosError(401));
    expect(await restoreSession()).toBe(false);
  });

  it("logs out even if the server can't be reached", async () => {
    tokenManager.setToken("Bearer t");
    vi.spyOn(axios, "post").mockRejectedValueOnce(new Error("offline"));
    vi.spyOn(console, "error").mockImplementation(() => {});
    await logout();
    expect(tokenManager.hasToken()).toBe(false);
  });
});

describe("videoService", () => {
  it("caches the video list until cleared", async () => {
    const get = vi.spyOn(axiosInstance, "get").mockResolvedValue({ data: [sampleVideo(1)] });
    expect(await getAllVideos()).toHaveLength(1);
    await getAllVideos();
    expect(get).toHaveBeenCalledTimes(1);
    clearVideoCache();
    await getAllVideos();
    expect(get).toHaveBeenCalledTimes(2);
  });

  it("rethrows when the list can't be loaded", async () => {
    vi.spyOn(axiosInstance, "get").mockRejectedValue(new Error("down"));
    await expect(getAllVideos()).rejects.toThrow("down");
  });

  it("deletes a video and reports why when it can't", async () => {
    const del = vi.spyOn(axiosInstance, "delete").mockResolvedValueOnce({ status: 204 });
    expect(await deleteVideo(4)).toBeNull();
    expect(del).toHaveBeenCalledWith("/videos/4");
    del.mockRejectedValueOnce(axiosError(404, { error: "That video couldn't be found. It may already be deleted." }));
    expect(await deleteVideo(4)).toBe("That video couldn't be found. It may already be deleted.");
    del.mockRejectedValueOnce(new Error("x"));
    expect(await deleteVideo(4)).toBe("The video couldn't be deleted. Please try again.");
  });

  it("returns a message for each kind of upload failure", async () => {
    const post = vi.spyOn(axiosInstance, "post");
    post.mockResolvedValueOnce({ data: [sampleVideo(1)] });
    expect(await uploadVideos(fileList([videoFile("clip-1.mp4")]))).toBeNull();
    expect(post.mock.calls[0][1]).toBeInstanceOf(FormData);

    // Only some of the videos were saved: say which ones weren't
    post.mockResolvedValueOnce({ data: [sampleVideo(1)] });
    expect(await uploadVideos(fileList([videoFile("clip-1.mp4"), videoFile("beach.mp4")]))).toBe(
      '1 of 2 videos were uploaded. "beach.mp4" couldn\'t be processed, please try it again.'
    );
    post.mockResolvedValueOnce({ data: {} });
    expect(await uploadVideos(fileList([videoFile("a.mp4"), videoFile("b.mp4")]))).toBe(
      '0 of 2 videos were uploaded. "a.mp4", "b.mp4" couldn\'t be processed, please try them again.'
    );
    post.mockResolvedValueOnce({ data: [{ ...sampleVideo(9), name: "renamed.mp4" }] });
    expect(await uploadVideos(fileList([videoFile("x\u0007.mp4"), videoFile("y.mp4")]))).toContain("1 of 2 videos were uploaded.");

    post.mockRejectedValueOnce(axiosError(400, { error: "Bad video" }));
    expect(await uploadVideos(fileList([]))).toBe("Bad video");
    post.mockRejectedValueOnce(axiosError(413, {}));
    expect(await uploadVideos(fileList([]))).toBe("Uploads can be at most 200MB in total.");
    post.mockRejectedValueOnce(axiosError(500, {}));
    expect(await uploadVideos(fileList([]))).toBe("Something went wrong on our side. Please try again in a moment.");
    post.mockRejectedValueOnce(axiosError(400, {}));
    expect(await uploadVideos(fileList([]))).toBe("Upload failed. Please try again.");
    post.mockRejectedValueOnce(new Error("x"));
    expect(await uploadVideos(fileList([]))).toBe("Upload failed. Please try again.");
  });
});

describe("montageService", () => {
  it("sends only the selected videos", async () => {
    const post = vi.spyOn(axiosInstance, "post").mockResolvedValue({ data: sampleMontage("Trip") });
    const result = await createMontage([sampleVideo(1), sampleVideo(2)], [false, true], "Trip", "beach");
    expect(result.name).toBe("Trip");
    expect(post).toHaveBeenCalledWith("/montages", { name: "Trip", prompt: "beach", videoRequestDTOs: [sampleVideo(2)] });
  });

  it("caches montages and rethrows failures", async () => {
    const get = vi.spyOn(axiosInstance, "get").mockResolvedValue({ data: [sampleMontage("A")] });
    await getAllMontages();
    await getAllMontages();
    expect(get).toHaveBeenCalledTimes(1);
    clearMontageCache();
    get.mockRejectedValueOnce(new Error("down"));
    await expect(getAllMontages()).rejects.toThrow("down");
  });

  it("deletes a montage and reports why when it can't", async () => {
    const del = vi.spyOn(axiosInstance, "delete").mockResolvedValueOnce({ status: 204 });
    expect(await deleteMontage(2)).toBeNull();
    expect(del).toHaveBeenCalledWith("/montages/2");
    del.mockRejectedValueOnce(axiosError(null));
    expect(await deleteMontage(2)).toBe("Can't reach TripSlice right now. Check your connection and try again.");
    del.mockRejectedValueOnce(new Error("x"));
    expect(await deleteMontage(2)).toBe("The montage couldn't be deleted. Please try again.");
  });
});

describe("searchService", () => {
  it("passes the query and type", async () => {
    const get = vi.spyOn(axiosInstance, "get").mockResolvedValue({ data: [] });
    await search("beach", "montages");
    expect(get).toHaveBeenCalledWith("/search", { params: { q: "beach", type: "montages" } });
  });
});

describe("refreshTokenAxios", () => {
  type Handler = { fulfilled: (v: unknown) => unknown; rejected: (e: unknown) => unknown };
  const request = (axiosInstance.interceptors.request as unknown as { handlers: Handler[] }).handlers[0];
  const response = (axiosInstance.interceptors.response as unknown as { handlers: Handler[] }).handlers[0];

  it("adds the access token to requests", () => {
    const config = { headers: {} as Record<string, string> };
    expect(request.fulfilled(config)).toEqual({ headers: {} });
    tokenManager.setToken("Bearer t");
    expect(request.fulfilled(config)).toEqual({ headers: { Authorization: "Bearer t" } });
  });

  it("refreshes once on 401 and retries", async () => {
    vi.spyOn(axios, "post").mockResolvedValueOnce({ headers: { authorization: "Bearer fresh" } });
    const adapter = vi.fn().mockResolvedValue({ data: "ok", status: 200, statusText: "OK", headers: {}, config: {} });
    const error = axiosError(401);
    error.config!.adapter = adapter;
    const retried = (await response.rejected(error)) as { data: string };
    expect(retried.data).toBe("ok");
    expect(tokenManager.getToken()).toBe("Bearer fresh");
    expect(response.fulfilled("passthrough")).toBe("passthrough");
  });

  it("sends the user to log in when refresh fails, and passes other errors through", async () => {
    vi.spyOn(axios, "post").mockRejectedValueOnce(new Error("expired"));
    const original = window.location;
    Object.defineProperty(window, "location", { configurable: true, value: { href: "/" } });
    await expect(response.rejected(axiosError(403))).rejects.toThrow("expired");
    expect(window.location.href).toBe("/account");
    Object.defineProperty(window, "location", { configurable: true, value: original });

    const notAuth = axiosError(500);
    await expect(response.rejected(notAuth)).rejects.toBe(notAuth);
  });
});
