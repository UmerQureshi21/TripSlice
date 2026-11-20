// Pure animation math for the landing page, kept apart from the components so it is easy to test

// Where each step sits along the track (0 = first node, 1 = last)
export const stopsFor = (count: number) => Array.from({ length: count }, (_, i) => i / (count - 1));
const STOPS = stopsFor(3);
const PAUSE_MS = 900;
const TRAVEL_MS = 1400;
const END_PAUSE_MS = 1600;

const easeInOut = (t: number) => (t < 0.5 ? 2 * t * t : 1 - (-2 * t + 2) ** 2 / 2);

/** Position of the particle (0..1) at a given time in the loop: it rests at each step, then glides on. */
export function particleAt(ms: number, stops: number[] = STOPS): number {
  const cycle = stops.length * PAUSE_MS + (stops.length - 1) * TRAVEL_MS + END_PAUSE_MS;
  let t = ms % cycle;
  for (let i = 0; i < stops.length; i++) {
    if (t < PAUSE_MS) return stops[i];
    t -= PAUSE_MS;
    if (i === stops.length - 1) return stops[i];
    if (t < TRAVEL_MS) return stops[i] + (stops[i + 1] - stops[i]) * easeInOut(t / TRAVEL_MS);
    t -= TRAVEL_MS;
  }
  return 1;
}

const HOLD = 0.6; // share of each card's scroll spent holding still before blending into the next
const smooth = (t: number) => t * t * (3 - 2 * t);
const clamp01 = (t: number) => Math.min(Math.max(t, 0), 1);

export type CardState = {
  // Each feature's card colour, blended smoothly into the next
  colour: number[];
  // Each feature's text and art: the old one fades out fully before the new one fades in, so they never overlap
  content: number[];
  // Which feature is on its way in (+1) or out (-1), for the slide direction
  direction: number[];
};

/** What to show for a scroll progress through the pinned section (0..1). */
export function cardState(progress: number, count = 3): CardState {
  const position = clamp01(progress) * (count - 1);
  const index = Math.min(Math.floor(position), count - 1);
  const frac = position - index;
  const blend = frac <= HOLD ? 0 : smooth((frac - HOLD) / (1 - HOLD));
  const out = 1 - smooth(clamp01(blend * 2));
  const incoming = smooth(clamp01(blend * 2 - 1));
  return {
    colour: Array.from({ length: count }, (_, i) => (i === index ? 1 - blend : i === index + 1 ? blend : 0)),
    content: Array.from({ length: count }, (_, i) => (i === index ? out : i === index + 1 ? incoming : 0)),
    direction: Array.from({ length: count }, (_, i) => (i <= index ? -1 : 1)),
  };
}
