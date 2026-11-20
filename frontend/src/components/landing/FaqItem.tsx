import { useId, useState } from "react";

// A question whose card grows smoothly to show the answer, instead of jumping open
export default function FaqItem({ question, answer }: { question: string; answer: string }) {
  const [open, setOpen] = useState(false);
  const id = useId();

  return (
    <div
      className={`rounded-[20px] border bg-[rgb(18,18,18)] transition-colors duration-500 ${
        open ? "border-[#925CFE]/60" : "border-white/10"
      }`}
    >
      <button
        type="button"
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center justify-between gap-[16px] px-[28px] py-[22px] text-left text-[18px] md:text-[20px] font-medium text-white cursor-pointer"
      >
        {question}
        <span
          aria-hidden="true"
          className={`text-[26px] leading-none text-[#925CFE] transition-transform duration-500 ease-out ${open ? "rotate-45" : ""}`}
        >
          +
        </span>
      </button>
      {/* Animating grid rows from 0fr to 1fr grows the card to the answer's real height */}
      <div
        id={id}
        role="region"
        className={`grid transition-[grid-template-rows,opacity] duration-500 ease-in-out ${
          open ? "grid-rows-[1fr] opacity-100" : "grid-rows-[0fr] opacity-0"
        }`}
      >
        <div className="overflow-hidden">
          <p className="px-[28px] pb-[24px] text-gray-300 leading-relaxed">{answer}</p>
        </div>
      </div>
    </div>
  );
}
