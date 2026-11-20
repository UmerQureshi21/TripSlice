import { useEffect, useState } from "react";

// Current window width, updated on resize
export default function useWindowWidth(): number {
  const [width, setWidth] = useState<number>(window.innerWidth);
  useEffect(() => {
    const onResize = () => setWidth(window.innerWidth);
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);
  return width;
}
