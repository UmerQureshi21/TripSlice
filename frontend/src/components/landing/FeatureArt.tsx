// Icons for the "Why TripSlice" cards, drawn in white on each card's colour
const LINE = { fill: "none", stroke: "currentColor", strokeWidth: 1.6, strokeLinecap: "round", strokeLinejoin: "round" } as const;

// The TripSlice film-and-scissors icon, the same one shown next to the title on the login page
export function TripSliceIconArt() {
  return <img src="/montage-icon-white.png" alt="" className="w-full h-full object-contain" draggable={false} />;
}

export function SearchArt() {
  return (
    <svg viewBox="0 0 24 24" className="w-full h-full" aria-hidden="true" {...LINE}>
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="m15.5 15.5 5 5" />
    </svg>
  );
}

export function LockArt() {
  return (
    <svg viewBox="0 0 24 24" className="w-full h-full" aria-hidden="true" {...LINE}>
      <rect x="4.5" y="10.5" width="15" height="10" rx="2.5" />
      <path d="M8 10.5V7a4 4 0 0 1 8 0v3.5" />
      <path d="M12 14.5v2" />
    </svg>
  );
}
