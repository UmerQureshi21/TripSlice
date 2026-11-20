import { useEffect, useState, type ReactNode } from "react";
import Carousel from "./Carousel";

interface props {
  height: number;
  isHome: boolean;
  // Shown under the tagline on the home page, e.g. the main call to action
  children?: ReactNode;
}

const TAGLINE = (
  <>
    Your AI-powered
    <span className="font-normal ml-[5px] ">Vacation Montage </span>Maker
  </>
);

export default function Header({ height, isHome, children }: props) {
  const MEDIUM_WIDTH_SIZE = 768;
  const [windowWidth, setWindowWidth] = useState<number>(window.innerWidth);

  useEffect(() => {
    const handleResize = () => {
      setWindowWidth(window.innerWidth);
    };

    window.addEventListener("resize", handleResize);

    return () => {
      window.removeEventListener("resize", handleResize);
    };
  }, []);

  return (
    <div
      className="relative w-full flex flex-col justify-center"
      style={{
        height: windowWidth >= MEDIUM_WIDTH_SIZE ? height : 320,
      }}
    >
      {/* Carousel background */}
      {windowWidth >= MEDIUM_WIDTH_SIZE ? (
        <div
          className="w-full"
          style={{
            height: height,
          }}
        >
          <Carousel
            images={[
              "/times-square.jpg",
              "/paris-evening.png",
              "/london-bridge.jpeg",
              "/time-square-2.jpeg",
              "/paris-night.jpeg",
              "/river.jpeg",
              "/building.jpeg",
              "/london-bridge.jpeg",
              "/time-square-2.jpeg",
            ]}
            flowSpeed={50}
            carouselHeight={height}
            height={height}
            width={`${height}px`}
          />
        </div>
      ) : (
        <div className="absolute top-0 right-0 w-[200%] h-full bg-cover bg-center flex">
          <img
            src="/times-square.jpg"
            alt=""
            className="w-[50%] h-full object-cover"
          />
          <img
            src="/paris-evening.png"
            alt=""
            className="w-[50%] h-full object-cover"
          />
        </div>
      )}

      {/* Logo */}
      {windowWidth >= MEDIUM_WIDTH_SIZE ? (
        !isHome ? (
          <img
            src="/trip-slice-logo-gradient.png"
            className="absolute z-[50] top-[25px] w-[20%] left-[40%] lg:w-[15%] lg:left-[42.5%]"
            alt=""
          />
        ) : (
          // Logo, tagline and button stacked in one column, so they never overlap at any width
          <div className="absolute z-[50] top-[155px] inset-x-0 flex flex-col items-center gap-[28px] px-[16px]">
            <img src="/trip-slice-logo-gradient.png" className="w-[50%] lg:w-[30%] max-w-[560px]" alt="TripSlice" />
            <h1 className="font-thin text-white text-[25px] poppins-font text-center">{TAGLINE}</h1>
            {children}
          </div>
        )
      ) : (
        <h1
          className="absolute z-[50] poppins-font top-[40px] left-[20px] md:top-[80px] 
                     md:left-1/2 md:-translate-x-1/2 text-[40px] md:text-[120px] md:font-bold 
                     text-white md:bg-gradient-to-r md:from-purple-400 md:via-white 
                     md:via-purple-600 md:to-blue-500 md:bg-clip-text md:text-transparent 
                     md:animate-[gradient_5s_ease-in-out_infinite] md:bg-[length:300%_auto]"
        >
          TripSlice
        </h1>
      )}

      {/* Tag line (and the home button) on small screens */}
      {windowWidth < MEDIUM_WIDTH_SIZE ? (
        <div className="absolute z-[50] top-[100px] left-[20px] w-[85%] flex flex-col items-start gap-[24px]">
          <h1 className="font-thin text-white text-[25px] poppins-font">{TAGLINE}</h1>
          {isHome ? children : null}
        </div>
      ) : null}

      {/* Fades: from black at the top, and on the landing page back to black at the bottom. Light mode has none */}
      <div className="absolute top-0 left-0 right-0 bottom-0 bg-gradient-to-b from-[rgb(5,5,5)] to-transparent light:hidden"></div>
      {isHome && (
        <div data-testid="hero-bottom-fade" className="absolute left-0 right-0 bottom-0 h-[55%] bg-gradient-to-t from-black via-black/60 to-transparent pointer-events-none"></div>
      )}
    </div>
  );
}
