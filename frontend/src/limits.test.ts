import { describe, expect, it, vi } from "vitest";
import { readDuration, validateUpload, MAX_VIDEO_SECONDS } from "./limits";
import { videoFile } from "./test/utils";

const durations = (map: Record<string, number | null>) => async (file: File) => map[file.name] ?? null;

describe("validateUpload", () => {
  it("accepts a valid selection", async () => {
    expect(await validateUpload([videoFile("a.mp4")], 0, durations({ "a.mp4": 30 }))).toEqual([]);
    expect(await validateUpload([], 0)).toEqual([]);
  });

  it("explains every problem at once", async () => {
    const files = ["1", "2", "3", "4", "5", "6"].map((n) => videoFile(`${n}.mp4`, 40 * 1024 * 1024));
    const errors = await validateUpload(files, 12, durations({ "1.mp4": 130, "2.mp4": 2, "3.mp4": 65 }));
    expect(errors).toContain("You picked 6 videos. You can upload up to 5 at a time.");
    expect(errors).toContain("You have 12 videos, so you can add 3 more (the limit is 15).");
    expect(errors.some((e) => e.startsWith("These files add up to 240MB"))).toBe(true);
    expect(errors).toContain('"1.mp4" is 2m 10s long. Videos can be at most 2 minutes.');
    expect(errors).toContain('"2.mp4" is only 2s long. Videos must be at least 3 seconds.');
    expect(errors.some((e) => e.includes("3.mp4"))).toBe(false);
  });

  it("says when the library is full and when a file isn't a video", async () => {
    const errors = await validateUpload([videoFile("notes.txt", 10, "text/plain")], 15);
    expect(errors).toEqual([
      "Your library is full (15 videos). Delete some videos to upload more.",
      '"notes.txt" isn\'t a video file.',
    ]);
  });

  it("leaves unreadable durations to the server", async () => {
    expect(await validateUpload([videoFile("a.mkv", 10, "video/x-matroska")], 0, durations({}))).toEqual([]);
  });

  it("caps videos at two minutes", () => {
    expect(MAX_VIDEO_SECONDS).toBe(120);
  });
});

describe("readDuration", () => {
  function stubVideo(trigger: (video: HTMLVideoElement) => void) {
    const real = document.createElement.bind(document);
    return vi.spyOn(document, "createElement").mockImplementation((tag: string) => {
      const el = real(tag);
      if (tag === "video") {
        Object.defineProperty(el, "src", { set: () => setTimeout(() => trigger(el as HTMLVideoElement)) });
      }
      return el;
    });
  }

  it("reads the length from metadata", async () => {
    const spy = stubVideo((v) => {
      Object.defineProperty(v, "duration", { value: 42 });
      v.onloadedmetadata?.(new Event("loadedmetadata"));
    });
    expect(await readDuration(videoFile("a.mp4"))).toBe(42);
    expect(URL.revokeObjectURL).toHaveBeenCalled();
    spy.mockRestore();
  });

  it("returns null for unreadable or endless videos", async () => {
    let spy = stubVideo((v) => v.onerror?.(new Event("error")));
    expect(await readDuration(videoFile("a.avi"))).toBeNull();
    spy.mockRestore();

    spy = stubVideo((v) => {
      Object.defineProperty(v, "duration", { value: Infinity });
      v.onloadedmetadata?.(new Event("loadedmetadata"));
    });
    expect(await readDuration(videoFile("live.mp4"))).toBeNull();
    spy.mockRestore();
  });

  it("gives up after the timeout", async () => {
    const spy = stubVideo(() => {});
    expect(await readDuration(videoFile("slow.mp4"), 5)).toBeNull();
    spy.mockRestore();
  });
});
