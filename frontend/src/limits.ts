// The same limits the backend enforces. Checking them here first gives instant, clear messages
// instead of a slow upload that is refused at the end.

export const MAX_FILES_PER_UPLOAD = 5;
export const MAX_VIDEOS_TOTAL = 15;
export const MIN_VIDEO_SECONDS = 3;
export const MAX_VIDEO_SECONDS = 120;
export const MAX_UPLOAD_BYTES = 200 * 1024 * 1024;
export const MAX_VIDEOS_PER_MONTAGE = 5;
export const MAX_TOPIC_LENGTH = 100;
export const MAX_TITLE_LENGTH = 60;
export const MAX_SEARCH_LENGTH = 200;


/**
 * Reads a video's length in the browser. Resolves null when the browser can't tell
 * (e.g. some .mkv/.avi files); the server still checks those.
 */
export function readDuration(file: File, timeoutMs = 5000): Promise<number | null> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file);
    const video = document.createElement("video");
    let settled = false;
    const finish = (value: number | null) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      URL.revokeObjectURL(url);
      resolve(value);
    };
    const timer = setTimeout(() => finish(null), timeoutMs);
    video.preload = "metadata";
    video.onloadedmetadata = () => finish(Number.isFinite(video.duration) ? video.duration : null);
    video.onerror = () => finish(null);
    video.src = url;
  });
}

function formatSeconds(seconds: number): string {
  const total = Math.round(seconds);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return m > 0 ? `${m}m ${s}s` : `${s}s`;
}

/** Every problem with a selection, as messages for the user. Empty means it can be uploaded. */
export async function validateUpload(
  files: File[],
  existingCount: number,
  getDuration: (file: File) => Promise<number | null> = readDuration
): Promise<string[]> {
  const errors: string[] = [];
  if (files.length === 0) return errors;

  if (files.length > MAX_FILES_PER_UPLOAD) {
    errors.push(`You picked ${files.length} videos. You can upload up to ${MAX_FILES_PER_UPLOAD} at a time.`);
  }
  if (existingCount + files.length > MAX_VIDEOS_TOTAL) {
    const room = Math.max(0, MAX_VIDEOS_TOTAL - existingCount);
    errors.push(
      room === 0
        ? `Your library is full (${MAX_VIDEOS_TOTAL} videos). Delete some videos to upload more.`
        : `You have ${existingCount} videos, so you can add ${room} more (the limit is ${MAX_VIDEOS_TOTAL}).`
    );
  }
  const totalBytes = files.reduce((sum, f) => sum + f.size, 0);
  if (totalBytes > MAX_UPLOAD_BYTES) {
    errors.push(
      `These files add up to ${Math.round(totalBytes / 1024 / 1024)}MB. One upload can be at most 200MB.`
    );
  }

  for (const file of files) {
    if (!file.type.startsWith("video/")) {
      errors.push(`"${file.name}" isn't a video file.`);
      continue;
    }
    const seconds = await getDuration(file);
    if (seconds === null) continue;
    if (seconds > MAX_VIDEO_SECONDS) {
      errors.push(`"${file.name}" is ${formatSeconds(seconds)} long. Videos can be at most 2 minutes.`);
    } else if (seconds < MIN_VIDEO_SECONDS) {
      errors.push(`"${file.name}" is only ${formatSeconds(seconds)} long. Videos must be at least 3 seconds.`);
    }
  }
  return errors;
}
