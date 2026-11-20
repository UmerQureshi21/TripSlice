import { useEffect, useId, useRef, useState, type ReactNode } from "react";

/** An "i" button that opens a small speech bubble. Closes on a click elsewhere or Escape. */
export default function InfoPopover({ label, children }: { label: string; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const id = useId();

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onClick);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div ref={ref} className="relative inline-flex">
      <button
        type="button"
        aria-label={label}
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen((o) => !o)}
        className={`grid place-items-center w-[30px] h-[30px] rounded-full border text-[15px] font-semibold italic font-serif transition-colors cursor-pointer ${
          open ? "border-[#925CFE] bg-[#925CFE] text-white" : "border-line text-muted hover:text-ink hover:border-[#925CFE]"
        }`}
      >
        i
      </button>
      {open && (
        <div
          id={id}
          role="dialog"
          aria-label={label}
          className="absolute z-[60] top-[calc(100%+12px)] right-[-12px] sm:right-auto sm:left-1/2 sm:-translate-x-1/2 w-[min(320px,85vw)] rounded-[16px] border border-line bg-surface px-[18px] py-[14px] text-left text-sm text-ink-soft leading-relaxed shadow-[0_18px_40px_-12px_rgba(0,0,0,0.45)]"
        >
          {/* The bubble's little tail, pointing at the button */}
          <span
            aria-hidden="true"
            className="absolute -top-[7px] right-[22px] sm:right-auto sm:left-1/2 sm:-translate-x-1/2 w-[12px] h-[12px] rotate-45 border-l border-t border-line bg-surface"
          />
          {children}
        </div>
      )}
    </div>
  );
}
