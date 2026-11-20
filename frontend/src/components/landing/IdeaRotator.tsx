import { useEffect, useState } from "react";

/** One big idea at a time, swapping to the next every second, looping forever. */
export default function IdeaRotator({ ideas, intervalMs = 1000 }: { ideas: string[]; intervalMs?: number }) {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => setIndex((i) => (i + 1) % ideas.length), intervalMs);
    return () => clearInterval(timer);
  }, [ideas.length, intervalMs]);

  return (
    // Screen readers aren't told about every change, it would interrupt them once a second
    <div aria-live="off" className="min-h-[2.6em] flex items-center justify-center text-[26px] md:text-[40px] lg:text-[48px] font-semibold leading-[1.3] text-center">
      {/* The key change remounts the text, which replays the fade-up */}
      <p key={index} data-idea className="idea-in bg-gradient-to-r from-white via-[#d9c6ff] to-[#925CFE] bg-clip-text text-transparent">
        &ldquo;{ideas[index]}&rdquo;
      </p>
    </div>
  );
}
