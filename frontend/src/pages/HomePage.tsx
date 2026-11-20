import { NavLink } from "react-router";
import Header from "../components/Header";
import Footer from "../components/Footer";
import Reveal from "../components/Reveal";
import HowItWorks from "../components/landing/HowItWorks";
import FeatureScroller from "../components/landing/FeatureScroller";
import FaqItem from "../components/landing/FaqItem";
import IdeaRotator from "../components/landing/IdeaRotator";
import useWindowWidth from "../components/useWindowWidth";

const PURPLE = "#925CFE";
const SECTION = "w-full py-[120px] md:py-[160px] px-[16px]";

const PROMPTS = [
  "the Eiffel Tower",
  "sunset over the water",
  "dad laughing",
  "street food",
  "hot air balloons",
  "Times Square at night",
  "waterfalls",
  "London Bridge",
];

const FAQS = [
  {
    q: "What videos can I upload?",
    a: "Most phone formats (mp4, mov, webm and more). Each video can be 3 seconds to 2 minutes long, up to 5 at a time and 15 in your library.",
  },
  {
    q: "How long does a montage take?",
    a: "Usually a minute or two, depending on how many videos you pick (up to 5 per montage). You can watch every step as it happens.",
  },
  {
    q: "What if nothing matches my topic?",
    a: "You'll get a clear message and nothing is saved, so try describing it differently or picking other videos.",
  },
  {
    q: "What powers TripSlice?",
    a: "TwelveLabs video AI for understanding your footage, and FFmpeg for cutting and joining the clips.",
  },
];

function StartButton() {
  return (
    <NavLink
      to="/account"
      className="relative inline-flex items-center justify-center overflow-hidden group rounded-[100px] h-[72px] md:h-[84px] px-[48px] md:px-[64px] border border-[#925CFE] bg-black text-[18px] md:text-[20px] transition-transform duration-500 hover:scale-105"
    >
      <span className="absolute inset-0 bg-[#925CFE] translate-x-[-101%] group-hover:translate-x-0 transition-transform duration-500" />
      <span className="relative z-10 text-[#925CFE] group-hover:text-black transition-colors duration-300">
        Start Creating!
      </span>
    </NavLink>
  );
}

function SectionHeading({ eyebrow, title }: { eyebrow: string; title: string }) {
  return (
    <div className="text-center mb-[56px] md:mb-[80px]">
      <p className="uppercase tracking-[0.25em] text-sm" style={{ color: PURPLE }}>
        {eyebrow}
      </p>
      <h2 className="mt-[12px] text-[36px] md:text-[64px] font-bold text-white leading-tight">{title}</h2>
    </div>
  );
}

export default function HomePage() {
  const width = useWindowWidth();
  const isMobile = width < 640;

  return (
    <div className="bg-black poppins-font text-white">
      <Header height={760} isHome={true}>
        <StartButton />
      </Header>

      {/* Tagline and demo */}
      <section className={SECTION}>
        <Reveal className="mx-auto max-w-[1200px] flex flex-col lg:flex-row items-center gap-[56px]">
          <h1 className="lg:w-[40%] font-thin text-[44px] md:text-[64px] lg:text-[72px] leading-tight text-center lg:text-left">
            From <span className="font-bold" style={{ color: PURPLE }}>cluttered</span> hours of footage to
            minutes of <span className="font-bold" style={{ color: PURPLE }}>magic</span>
          </h1>
          <div className="w-full lg:w-[60%] flex justify-center">
            <video
              className={`${isMobile ? "w-[75%]" : "w-full"} border border-white/80 rounded-[20px]`}
              // Served with the frontend, not from S3, so the bucket stays private
              src={isMobile ? "/trip-slice-mobile.mp4" : "/trip-slice-desktop.mp4"}
              autoPlay
              muted
              playsInline
              loop
            />
          </div>
        </Reveal>
      </section>

      {/* How it works */}
      <section id="how-it-works" className={`${SECTION} scroll-mt-[40px]`}>
        <Reveal>
          <SectionHeading eyebrow="How it works" title="Three steps to your montage" />
        </Reveal>
        <Reveal>
          <HowItWorks />
        </Reveal>
      </section>

      <FeatureScroller />

      {/* Ideas: one big example at a time */}
      <section className={SECTION}>
        <Reveal className="mx-auto max-w-[1100px] flex flex-col items-center gap-[32px]">
          <h2 className="text-[48px] md:text-[80px] lg:text-[96px] font-bold leading-none text-white">Ideas</h2>
          <IdeaRotator ideas={PROMPTS} />
        </Reveal>
      </section>

      {/* FAQ */}
      <section className={`${SECTION} pb-[160px] md:pb-[200px]`}>
        <Reveal>
          <SectionHeading eyebrow="FAQ" title="Good to know" />
        </Reveal>
        <div className="mx-auto max-w-[800px] flex flex-col gap-[16px]">
          {FAQS.map((faq) => (
            <Reveal key={faq.q}>
              <FaqItem question={faq.q} answer={faq.a} />
            </Reveal>
          ))}
        </div>
      </section>

      <Footer />
    </div>
  );
}
