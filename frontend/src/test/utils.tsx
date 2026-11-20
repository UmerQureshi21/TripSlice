import type { ReactElement } from "react";
import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router";

export function renderWithRouter(ui: ReactElement, route = "/") {
  return render(<MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>);
}

export function setWidth(width: number) {
  Object.defineProperty(window, "innerWidth", { configurable: true, writable: true, value: width });
  window.dispatchEvent(new Event("resize"));
}

export function videoFile(name: string, sizeBytes = 1000, type = "video/mp4"): File {
  const file = new File(["x"], name, { type });
  Object.defineProperty(file, "size", { value: sizeBytes });
  return file;
}

export const sampleVideo = (n: number, searchable = true) => ({
  name: `clip-${n}.mp4`,
  videoId: `vid-${n}`,
  previewUrl: `https://s3/clip-${n}.mp4`,
  searchable,
});

export const sampleMontage = (name: string, duration = 5, videos = [sampleVideo(1)]) => ({
  name,
  preSignedUrl: `https://s3/${name}.mp4`,
  prompt: "beach",
  createdAt: new Date("2026-05-01T10:00:00Z"),
  duration,
  videos,
  searchable: true,
});
