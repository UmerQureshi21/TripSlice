import axiosInstance from "./refreshTokenAxios";
import { errorMessage } from "./errors";
import type {
  MontageRequestDTO,
  MontageResponseDTO,
  VideoRequestDTO,
} from "../Types";

// Shorter than the 10 minute life of the S3 links inside, so cached previews never expire
const CACHE_DURATION = 4 * 60 * 1000;

let cachedMontages: MontageResponseDTO[] | null = null;
let cacheTime: number = 0;

export async function createMontage(
  files: VideoRequestDTO[],
  clicks: boolean[],
  title: string,
  sentence: string
): Promise<MontageResponseDTO> {
  const videosInMontage: VideoRequestDTO[] = [];
  for (let i = 0; i < clicks.length; i++) {
    if (clicks[i]) {
      videosInMontage.push(files[i]);
    }
  }

  const request: MontageRequestDTO = {
    name: title,
    videoRequestDTOs: videosInMontage,
    prompt: sentence,
  };

  const res = await axiosInstance.post(`/montages`, request);
  clearMontageCache();
  const data: MontageResponseDTO = res.data;
  return data;
}

export async function getAllMontages(): Promise<MontageResponseDTO[]> {
  const now = Date.now();
  if (cachedMontages && now - cacheTime < CACHE_DURATION) {
    return cachedMontages;
  }
  const res = await axiosInstance.get(`/montages`);
  cachedMontages = res.data;
  cacheTime = now;
  return res.data;
}

// Returns null on success, otherwise the message to show the user
export async function deleteMontage(id: number): Promise<string | null> {
  try {
    await axiosInstance.delete(`/montages/${id}`);
    clearMontageCache();
    return null;
  } catch (err) {
    return errorMessage(err, "The montage couldn't be deleted. Please try again.");
  }
}

export function clearMontageCache(): void {
  cachedMontages = null;
  cacheTime = 0;
}
