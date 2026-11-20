import axios from "axios";

export const OFFLINE_MESSAGE = "Can't reach TripSlice right now. Check your connection and try again.";

/**
 * The message to show for a failed request. The backend sends { "error": "..." } written for the
 * user, so that wins; otherwise the status decides, and `fallback` covers anything else.
 */
export function errorMessage(err: unknown, fallback: string): string {
  if (!axios.isAxiosError(err)) return fallback;
  if (!err.response) return OFFLINE_MESSAGE;
  const data = err.response.data as { error?: unknown; timestamp?: unknown } | undefined;
  // Spring's own error body also has an "error" field, but it only holds "Bad Request" and the like
  if (typeof data?.error === "string" && data.error.trim() && data.timestamp === undefined) {
    return data.error;
  }
  const status = err.response.status;
  if (status === 429) return "Too many requests. Please wait a minute and try again.";
  if (status === 413) return "Uploads can be at most 200MB in total.";
  if (status >= 500) return "Something went wrong on our side. Please try again in a moment.";
  return fallback;
}

/** True when the request never got an answer, e.g. the connection dropped. */
export function isOffline(err: unknown): boolean {
  return axios.isAxiosError(err) && !err.response;
}
