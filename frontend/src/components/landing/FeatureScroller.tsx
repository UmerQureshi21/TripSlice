import { useEffect, useRef, useState } from "react";
import { cardState, type CardState } from "./motion";
import { FEATURES } from "./features";
import useWindowWidth from "../useWindowWidth";

const EYEBROW = <p className="uppercase tracking-[0.25em] text-sm text-[#925CFE]">Why TripSlice</p>;
const CARD = "rounded-[36px] text-white shadow-[0_30px_80px_-30px_rgba(146,92,254,0.6)]";
const ICON = "w-[120px] h-[120px] md:w-[180px] md:h-[180px]";

/**
 * "Why TripSlice": on wide screens the section pins in place while you scroll. The coloured card
 * on the right shows each feature's icon and the text on the left changes with it; each holds for
 * a moment, then blends into the next. On small screens the features simply stack.
 */
export default function FeatureScroller() {
  const width = useWindowWidth();
  const pinned = width >= 1024;
  const sectionRef = useRef<HTMLElement>(null);
  const [state, setState] = useState<CardState>(() => cardState(0, FEATURES.length));

  useEffect(() => {
    if (!pinned) return;
    let frame = 0;
    const update = () => {
      frame = 0;
      const el = sectionRef.current;
      if (!el) return;
      const rect = el.getBoundingClientRect();
      const scrollable = rect.height - window.innerHeight;
      setState(cardState(scrollable > 0 ? -rect.top / scrollable : 0, FEATURES.length));
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update);
    };
    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      cancelAnimationFrame(frame);
    };
  }, [pinned]);

  if (!pinned) {
    return (
      // Small screens: a compact list (icon tile beside the text) instead of big cards
      <section className="w-full py-[96px] px-[20px]">
        <div className="mx-auto max-w-[560px]">
          {EYEBROW}
          <h2 className="mt-[12px] text-[34px] sm:text-[40px] font-bold leading-[1.1] text-white">
            Relive the trip, skip the scrubbing
          </h2>
          <ul className="mt-[40px] divide-y divide-white/10 border-y border-white/10">
            {FEATURES.map((feature) => (
              <li key={feature.title} className="flex gap-[18px] py-[26px]">
                <div
                  className="shrink-0 grid place-items-center w-[52px] h-[52px] rounded-[16px] text-white shadow-[0_10px_30px_-12px_rgba(146,92,254,0.8)]"
                  style={{ background: feature.background }}
                >
                  <div className="w-[26px] h-[26px]">{feature.art}</div>
                </div>
                <div className="min-w-0">
                  <h3 className="text-[19px] font-semibold leading-snug text-white">{feature.title}</h3>
                  <p className="mt-[6px] text-[15px] text-gray-400 leading-relaxed">{feature.body}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </section>
    );
  }

  // Shared fade and slide for whatever is changing between features
  const layer = (i: number) => ({
    opacity: state.content[i],
    transform: `translateY(${(1 - state.content[i]) * 36 * state.direction[i]}px)`,
    visibility: state.content[i] > 0 ? ("visible" as const) : ("hidden" as const),
  });

  return (
    <section ref={sectionRef} data-testid="feature-scroller" className="relative w-full" style={{ height: `${FEATURES.length * 100}vh` }}>
      <div className="sticky top-0 h-screen flex items-center">
        <div className="mx-auto w-full max-w-[1200px] px-[32px] grid grid-cols-2 gap-[64px] items-center">
          <div>
            {EYEBROW}
            {/* Each feature's text, swapping in step with the card */}
            <div className="relative mt-[16px] h-[340px]">
              {FEATURES.map((feature, i) => (
                <div key={feature.title} data-text={i} className="absolute inset-x-0 top-0" style={layer(i)} aria-hidden={state.content[i] < 0.5}>
                  <h2 className="text-[56px] xl:text-[64px] font-bold leading-[1.05] text-white">{feature.title}</h2>
                  <p className="mt-[20px] text-[19px] text-gray-400 leading-relaxed max-w-[480px]">{feature.body}</p>
                </div>
              ))}
            </div>
            <div className="flex gap-[10px]" aria-hidden="true">
              {state.colour.map((o, i) => (
                <span key={i} className="h-[6px] rounded-full bg-[#925CFE] transition-all duration-300" style={{ width: o > 0.5 ? 36 : 12, opacity: 0.3 + o * 0.7 }} />
              ))}
            </div>
          </div>
          {/* One card: its colour blends between features while the icon swaps */}
          <div className={`relative h-[520px] overflow-hidden ${CARD}`}>
            {FEATURES.map((feature, i) => (
              <div key={`bg-${feature.title}`} className="absolute inset-0" style={{ background: feature.background, opacity: state.colour[i] }} />
            ))}
            {FEATURES.map((feature, i) => (
              <div key={feature.title} data-card={i} className="absolute inset-0 grid place-items-center" style={layer(i)} aria-hidden="true">
                <div className={ICON}>{feature.art}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
