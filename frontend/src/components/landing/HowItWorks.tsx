import { useEffect, useRef } from "react";
import { particleAt, stopsFor } from "./motion";

const STEPS = [
  {
    title: "Upload your trip",
    text: "Drop in the vacation videos you want to search through.",
    example: "paris-day-2.mov",
    icon: (
      <path d="M12 16V4m0 0-4.5 4.5M12 4l4.5 4.5M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3" />
    ),
  },
  {
    title: "Describe a moment",
    text: "Type what you want to see, and AI finds every clip where it happens.",
    example: "“the Eiffel Tower”",
    icon: (
      <>
        <path d="M4 6h10M4 11h7M4 16h5" />
        <path d="m15.5 13.5 1.2 2.8 2.8 1.2-2.8 1.2-1.2 2.8-1.2-2.8-2.8-1.2 2.8-1.2z" />
      </>
    ),
  },
  {
    title: "Get your montage",
    text: "The best moments are cut and stitched into one video, ready to share.",
    example: "Paris highlights · 0:42",
    icon: (
      <>
        <rect x="3" y="5" width="18" height="14" rx="3" />
        <path d="m10 9.5 4.5 2.5-4.5 2.5z" />
      </>
    ),
  },
];

const STOPS = stopsFor(STEPS.length);

// The track runs from the centre of the first column to the centre of the last
const TRACK_LEFT = 100 / (STEPS.length * 2);
const TRACK_WIDTH = 100 - 2 * TRACK_LEFT;

/**
 * The three steps as a pipeline: a glowing particle travels left to right along a track, lighting
 * the path behind it and each step as it arrives, then starts over. Runs on a timer once the
 * section is on screen, not on scroll.
 */
export default function HowItWorks() {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const root = ref.current;
    if (!root) return;
    const particle = root.querySelector<HTMLElement>("[data-particle]");
    const lit = root.querySelector<HTMLElement>("[data-lit-track]");
    const steps = [...root.querySelectorAll<HTMLElement>("[data-step]")];

    const draw = (pos: number, showParticle: boolean) => {
      if (particle) {
        particle.style.left = `${TRACK_LEFT + pos * TRACK_WIDTH}%`;
        particle.style.opacity = showParticle ? "1" : "0";
      }
      if (lit) lit.style.width = `${pos * TRACK_WIDTH}%`;
      steps.forEach((step, i) => step.toggleAttribute("data-lit", pos >= STOPS[i] - 0.001));
    };

    // People who prefer less motion see the finished state
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) {
      draw(1, false);
      return;
    }

    let frame = 0;
    let start: number | null = null;
    const tick = (now: number) => {
      if (start === null) start = now;
      draw(particleAt(now - start), true);
      frame = requestAnimationFrame(tick);
    };
    const begin = () => {
      if (!frame) frame = requestAnimationFrame(tick);
    };

    draw(0, false);
    if (typeof IntersectionObserver === "undefined") {
      begin();
      return () => cancelAnimationFrame(frame);
    }
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          begin();
        } else if (frame) {
          // Paused while off screen, and restarts from the first step when back
          cancelAnimationFrame(frame);
          frame = 0;
          start = null;
          draw(0, false);
        }
      },
      { threshold: 0.35 }
    );
    observer.observe(root);
    return () => {
      observer.disconnect();
      cancelAnimationFrame(frame);
    };
  }, []);

  return (
    <div ref={ref} className="relative mx-auto max-w-[1100px]">
      {/* Track: faint line, the lit part behind the particle, and the particle itself */}
      <div aria-hidden="true" className="absolute top-[32px] sm:top-[40px] md:top-[48px] h-[2px] bg-white/10" style={{ left: `${TRACK_LEFT}%`, width: `${TRACK_WIDTH}%` }} />
      <div
        aria-hidden="true"
        data-lit-track
        className="absolute top-[32px] sm:top-[40px] md:top-[48px] h-[2px] rounded-full bg-gradient-to-r from-[#925CFE] via-[#b98cff] to-[#f0a8ff] shadow-[0_0_12px_rgba(146,92,254,0.8)]"
        style={{ left: `${TRACK_LEFT}%`, width: 0 }}
      />
      <div
        aria-hidden="true"
        data-particle
        className="absolute top-[33px] sm:top-[41px] md:top-[49px] -translate-x-1/2 -translate-y-1/2 z-0 transition-opacity duration-300"
        style={{ left: `${TRACK_LEFT}%`, opacity: 0 }}
      >
        <span className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-[28px] h-[28px] rounded-full bg-[#925CFE]/60 blur-[8px]" />
        <span className="relative block w-[9px] h-[9px] rounded-full bg-white shadow-[0_0_10px_3px_rgba(185,140,255,0.9)]" />
      </div>

      <ol className="relative grid grid-cols-3 gap-[8px] md:gap-[24px]">
        {STEPS.map((step, i) => (
          <li key={step.title} data-step className="group flex flex-col items-center text-center">
            <span className="relative z-10 grid place-items-center w-[64px] h-[64px] sm:w-[80px] sm:h-[80px] md:w-[96px] md:h-[96px] rounded-full border border-white/15 bg-black text-white/35 transition-all duration-500 group-data-lit:border-[#925CFE]/70 group-data-lit:text-white group-data-lit:shadow-[0_0_40px_-6px_rgba(146,92,254,0.75)]">
              <svg viewBox="0 0 24 24" className="w-[26px] h-[26px] sm:w-[30px] sm:h-[30px] md:w-[36px] md:h-[36px]" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                {step.icon}
              </svg>
              <span className="absolute -right-[2px] -bottom-[2px] grid place-items-center w-[28px] h-[28px] rounded-full bg-[#1a1a1a] text-[13px] font-bold text-white/50 ring-[3px] ring-black transition-colors duration-500 group-data-lit:bg-[#925CFE] group-data-lit:text-white">
                {i + 1}
              </span>
            </span>
            <div className="mt-[28px] translate-y-[8px] opacity-40 transition-all duration-700 group-data-lit:translate-y-0 group-data-lit:opacity-100">
              <h3 className="text-[16px] sm:text-[20px] md:text-[26px] font-semibold text-white">{step.title}</h3>
              <p className="mt-[8px] hidden sm:block text-[15px] md:text-[17px] leading-relaxed text-gray-400 max-w-[300px] mx-auto">{step.text}</p>
              <span className="mt-[14px] inline-block rounded-[12px] bg-white/10 px-[12px] py-[6px] text-[11px] sm:text-[13px] text-white/80 sm:whitespace-nowrap">
                {step.example}
              </span>
            </div>
          </li>
        ))}
      </ol>
    </div>
  );
}
