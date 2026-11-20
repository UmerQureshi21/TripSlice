import axiosInstance from "./refreshTokenAxios";
import type { VideoRequestDTO } from "../Types";
import { errorMessage } from "./errors";

// Shorter than the 10 minute life of the S3 preview links inside, so cached previews never expire
const CACHE_DURATION = 4 * 60 * 1000;

let cachedVideos: VideoRequestDTO[] | null = null;
let cacheTime: number = 0;

export async function getAllVideos(): Promise<VideoRequestDTO[]> {
  const now = Date.now();
  if (cachedVideos && now - cacheTime < CACHE_DURATION) {
    return cachedVideos;
  }
  const res = await axiosInstance.get("/videos");
  cachedVideos = res.data;
  cacheTime = now;
  return res.data;
}

// Returns null when every video was saved, otherwise the message to show the user
export async function uploadVideos(files: File[] | FileList): Promise<string | null> {
  const list = Array.from(files);
  const request = new FormData();
  for (const file of list) {
    request.append("files", file);
  }
  try {
    const res = await axiosInstance.post("/videos", request);
    const saved: VideoRequestDTO[] = Array.isArray(res.data) ? res.data : [];
    if (saved.length >= list.length) return null;
    // Some videos were saved and some weren't, say which
    const savedNames = new Set(saved.map((v) => v.name));
    const missing = list.filter((f) => !savedNames.has(f.name)).map((f) => `"${f.name}"`);
    return `${saved.length} of ${list.length} videos were uploaded. ${
      missing.length > 0 ? missing.join(", ") : "The rest"
    } couldn't be processed, please try ${missing.length === 1 ? "it" : "them"} again.`;
  } catch (err) {
    return errorMessage(err, "Upload failed. Please try again.");
  }
}

// Returns null on success, otherwise the message to show the user
export async function deleteVideo(id: number): Promise<string | null> {
  try {
    await axiosInstance.delete(`/videos/${id}`);
    clearVideoCache();
    return null;
  } catch (err) {
    return errorMessage(err, "The video couldn't be deleted. Please try again.");
  }
}

export function clearVideoCache(): void {
  cachedVideos = null;
  cacheTime = 0;
}
