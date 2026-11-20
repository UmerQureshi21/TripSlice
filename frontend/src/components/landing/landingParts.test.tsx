import { afterEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import HowItWorks from "./HowItWorks";
import FeatureScroller from "./FeatureScroller";
import { cardState, particleAt } from "./motion";
import { FEATURES } from "./features";
import FaqItem from "./FaqItem";
import IdeaRotator from "./IdeaRotator";
import { setWidth } from "../../test/utils";

const lit = () => [...document.querySelectorAll("[data-step]")].map((s) => s.hasAttribute("data-lit"));

describe("particleAt", () => {
  it("rests at each step, then glides to the next", () => {
    expect(particleAt(0)).toBe(0);
    expect(particleAt(800)).toBe(0); // still paused on step 1
    const halfway = particleAt(900 + 700);
    expect(halfway).toBeGreaterThan(0.2);
    expect(halfway).toBeLessThan(0.3);
    expect(particleAt(900 + 1400 + 100)).toBe(0.5); // resting on step 2
    expect(particleAt(900 * 3 + 1400 * 2 + 200)).toBe(1); // resting on the last step
    expect(particleAt(900 * 3 + 1400 * 2 + 1600)).toBe(0); // and the loop starts over
  });
});

describe("HowItWorks", () => {
  afterEach(() => vi.restoreAllMocks());

  function withFrames() {
    const frames: FrameRequestCallback[] = [];
    vi.spyOn(window, "requestAnimationFrame").mockImplementation((cb) => {
      frames.push(cb);
      return frames.length;
    });
    vi.spyOn(window, "cancelAnimationFrame").mockImplementation(() => {});
    return (ms: number) => act(() => frames.shift()!(ms));
  }

  it("moves the particle across and lights each step as it arrives", () => {
    const frame = withFrames();
    const { unmount } = render(<HowItWorks />);
    frame(1000); // the loop starts at the first frame's time
    expect(lit()).toEqual([true, false, false]);
    frame(1000 + 900 + 1400 + 10);
    expect(lit()).toEqual([true, true, false]);
    frame(1000 + 900 * 3 + 1400 * 2 + 10);
    expect(lit()).toEqual([true, true, true]);
    const particle = document.querySelector<HTMLElement>("[data-particle]")!;
    expect(particle.style.opacity).toBe("1");
    expect(particle.style.left).toBe(`${100 - 100 / 6}%`);
    unmount();
    expect(window.cancelAnimationFrame).toHaveBeenCalled();
  });

  it("pauses and resets while off screen", () => {
    const frame = withFrames();
    let callback: IntersectionObserverCallback = () => {};
    vi.stubGlobal(
      "IntersectionObserver",
      class {
        constructor(cb: IntersectionObserverCallback) {
          callback = cb;
        }
        observe() {}
        disconnect() {}
      }
    );
    render(<HowItWorks />);
    expect(window.requestAnimationFrame).not.toHaveBeenCalled(); // waits until on screen
    const show = (isIntersecting: boolean) =>
      act(() => callback([{ isIntersecting } as IntersectionObserverEntry], {} as IntersectionObserver));
    show(true);
    frame(0);
    frame(900 + 1400 + 10);
    expect(lit()).toEqual([true, true, false]);
    show(false);
    expect(lit()).toEqual([true, false, false]);
    expect(document.querySelector<HTMLElement>("[data-particle]")!.style.opacity).toBe("0");
    vi.unstubAllGlobals();
  });

  it("shows the finished state when the user prefers less motion", () => {
    vi.spyOn(window, "requestAnimationFrame");
    window.matchMedia = vi.fn().mockReturnValue({ matches: true }) as unknown as typeof window.matchMedia;
    render(<HowItWorks />);
    expect(lit()).toEqual([true, true, true]);
    expect(window.requestAnimationFrame).not.toHaveBeenCalled();
    // @ts-expect-error restore jsdom's default
    delete window.matchMedia;
  });

  it("runs straight away without IntersectionObserver", () => {
    const frame = withFrames();
    const original = globalThis.IntersectionObserver;
    // @ts-expect-error simulating an old browser
    delete globalThis.IntersectionObserver;
    render(<HowItWorks />);
    frame(0);
    expect(lit()[0]).toBe(true);
    globalThis.IntersectionObserver = original;
  });
});

describe("cardState", () => {
  it("holds each card, then blends colour while the content swaps without overlapping", () => {
    expect(cardState(0).content).toEqual([1, 0, 0]);
    // Early in the first card's stretch nothing changes yet
    expect(cardState(0.15).content).toEqual([1, 0, 0]);

    const outgoing = cardState(0.34); // blend has started, old content leaving
    expect(outgoing.content[0]).toBeGreaterThan(0);
    expect(outgoing.content[0]).toBeLessThan(1);
    expect(outgoing.content[1]).toBe(0);
    expect(outgoing.colour[0] + outgoing.colour[1]).toBeCloseTo(1);

    const incoming = cardState(0.46);
    expect(incoming.content[0]).toBe(0);
    expect(incoming.content[1]).toBeGreaterThan(0);
    expect(incoming.direction).toEqual([-1, 1, 1]);

    expect(cardState(1).content).toEqual([0, 0, 1]);
    expect(cardState(1, 4).content).toEqual([0, 0, 0, 1]);
    expect(cardState(-5).content).toEqual([1, 0, 0]);
  });
});

describe("FeatureScroller", () => {
  it("pins on wide screens and follows the scroll position", () => {
    setWidth(1440);
    Object.defineProperty(window, "innerHeight", { configurable: true, value: 900 });
    const frames: FrameRequestCallback[] = [];
    vi.spyOn(window, "requestAnimationFrame").mockImplementation((cb) => {
      frames.push(cb);
      return 1;
    });
    render(<FeatureScroller />);
    const section = screen.getByTestId("feature-scroller");
    expect(section.style.height).toBe(`${FEATURES.length * 100}vh`);
    let top = 0;
    vi.spyOn(section, "getBoundingClientRect").mockImplementation(() => ({ top, height: 2700 }) as DOMRect);
    const card = (i: number) => document.querySelector<HTMLElement>(`[data-card="${i}"]`)!;

    top = -1800; // scrolled to the end of the pinned section
    fireEvent.scroll(window);
    fireEvent.scroll(window); // a second scroll in the same frame is coalesced
    expect(frames).toHaveLength(1);
    act(() => frames.shift()!(0));
    expect(card(2).style.opacity).toBe("1");
    expect(card(0).style.visibility).toBe("hidden");
    // The text on the left matches the card on the right
    expect(document.querySelector<HTMLElement>('[data-text="2"]')!).toHaveTextContent("Your footage stays yours");
    expect(document.querySelector<HTMLElement>('[data-text="2"]')!.style.opacity).toBe("1");
    expect(document.querySelector<HTMLElement>('[data-text="0"]')!.style.visibility).toBe("hidden");
    expect(screen.queryByText("Watch it build live")).toBeNull();
    vi.restoreAllMocks();
  });

  it("stacks the cards on small screens", () => {
    setWidth(390);
    render(<FeatureScroller />);
    // The first card shows the TripSlice icon instead of a generic one
    expect(document.querySelector('img[src="/montage-icon-white.png"]')).not.toBeNull();
    expect(screen.queryByTestId("feature-scroller")).toBeNull();
    for (const feature of FEATURES) {
      expect(screen.getByText(feature.title)).toBeVisible();
    }
  });

  it("handles a section no taller than the screen", () => {
    setWidth(1440);
    Object.defineProperty(window, "innerHeight", { configurable: true, value: 5000 });
    render(<FeatureScroller />);
    expect(document.querySelector<HTMLElement>('[data-card="0"]')!.style.opacity).toBe("1");
  });
});

describe("FaqItem", () => {
  it("expands and collapses smoothly with accessible state", () => {
    render(<FaqItem question="Is it free?" answer="Yes, for now." />);
    const button = screen.getByRole("button", { name: /Is it free\?/ });
    const region = document.getElementById(button.getAttribute("aria-controls")!)!;
    expect(button).toHaveAttribute("aria-expanded", "false");
    expect(region.className).toContain("grid-rows-[0fr]");
    expect(region.className).toContain("transition-[grid-template-rows,opacity]");
    fireEvent.click(button);
    expect(button).toHaveAttribute("aria-expanded", "true");
    expect(region.className).toContain("grid-rows-[1fr]");
    fireEvent.click(button);
    expect(region.className).toContain("grid-rows-[0fr]");
  });
});

describe("IdeaRotator", () => {
  it("shows one idea at a time and moves on every second, looping", () => {
    vi.useFakeTimers();
    const { unmount } = render(<IdeaRotator ideas={["beach", "boats", "bridges"]} />);
    const idea = () => document.querySelector("[data-idea]")!.textContent;
    expect(idea()).toBe("\u201cbeach\u201d");
    act(() => vi.advanceTimersByTime(1000));
    expect(idea()).toBe("\u201cboats\u201d");
    act(() => vi.advanceTimersByTime(2000));
    expect(idea()).toBe("\u201cbeach\u201d"); // wrapped back to the first
    expect(document.querySelectorAll("[data-idea]")).toHaveLength(1);
    unmount();
    expect(vi.getTimerCount()).toBe(0);
    vi.useRealTimers();
  });
});
