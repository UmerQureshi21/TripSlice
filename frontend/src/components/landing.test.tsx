import { describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import HomePage from "../pages/HomePage";
import NotFoundPage from "../pages/NotFoundPage";
import Footer, { GITHUB_URL, LINKEDIN_URL } from "./Footer";
import Reveal from "./Reveal";
import Header from "./Header";
import Carousel from "./Carousel";
import { renderWithRouter, setWidth } from "../test/utils";

describe("Footer", () => {
  it("links to GitHub, LinkedIn and the project pages", () => {
    renderWithRouter(<Footer />);
    expect(screen.getByLabelText("TripSlice on GitHub")).toHaveAttribute("href", GITHUB_URL);
    expect(screen.getByLabelText("Umer Qureshi on LinkedIn")).toHaveAttribute("href", LINKEDIN_URL);
    expect(screen.getByText("Report an issue")).toHaveAttribute("href", `${GITHUB_URL}/issues`);
    expect(screen.getByText("Start creating")).toHaveAttribute("href", "/account");
    // Scrolls to the section without adding #how-it-works to the URL
    const section = document.createElement("section");
    section.id = "how-it-works";
    section.scrollIntoView = vi.fn();
    document.body.appendChild(section);
    const howItWorks = screen.getByText("How it works");
    expect(fireEvent.click(howItWorks)).toBe(false); // default navigation prevented
    expect(section.scrollIntoView).toHaveBeenCalledWith({ behavior: "smooth" });
    section.remove();
    // External links open safely in a new tab
    for (const link of screen.getAllByRole("link").filter((a) => a.getAttribute("target") === "_blank")) {
      expect(link).toHaveAttribute("rel", "noopener noreferrer");
    }
    expect(screen.getByText(`© ${new Date().getFullYear()} Umer Qureshi.`)).toBeInTheDocument();
    expect(screen.queryByText(/TwelveLabs/)).toBeNull();
    expect(screen.queryByText(/Built with/)).toBeNull();
  });
});

describe("HomePage", () => {
  it("renders every section on desktop", () => {
    setWidth(1440);
    renderWithRouter(<HomePage />);
    for (const heading of ["Three steps to your montage", "Finds the moments for you", "Good to know"]) {
      expect(screen.getByRole("heading", { name: heading })).toBeInTheDocument();
    }
    // The call to action sits in the hero, right under the logo and tagline
    expect(screen.getByAltText("TripSlice").parentElement).toContainElement(screen.getByText("Start Creating!"));
    expect(screen.getByText("Start Creating!").closest("a")).toHaveAttribute("href", "/account");
    expect(screen.queryByText(/Ready to relive your/)).toBeNull();
    expect(document.getElementById("how-it-works")).not.toBeNull();
    expect(document.querySelector("video")?.getAttribute("src")).toBe("/trip-slice-desktop.mp4");
    expect(screen.getByText("What videos can I upload?")).toBeInTheDocument();
  });

  it("uses the phone demo on small screens", () => {
    setWidth(390);
    renderWithRouter(<HomePage />);
    expect(document.querySelector("video")?.getAttribute("src")).toBe("/trip-slice-mobile.mp4");
    expect(screen.getByText("TripSlice")).toBeInTheDocument(); // text logo on mobile
    expect(screen.getByText("Start Creating!")).toBeInTheDocument();
  });
});

describe("Reveal", () => {
  it("shows content when scrolled into view", () => {
    render(<Reveal>hello</Reveal>);
    expect(screen.getByText("hello")).toHaveAttribute("data-visible", "true");
  });

  it("shows content straight away without IntersectionObserver", () => {
    const original = globalThis.IntersectionObserver;
    // @ts-expect-error simulating an old browser
    delete globalThis.IntersectionObserver;
    render(<Reveal>old browser</Reveal>);
    expect(screen.getByText("old browser")).toHaveAttribute("data-visible", "true");
    globalThis.IntersectionObserver = original;
  });
});

describe("Header", () => {
  it("shows the image logo on wide screens and the tagline only on home", () => {
    setWidth(1200);
    const { rerender } = render(<Header height={300} isHome={false} />);
    expect(screen.queryByText(/AI-powered/)).toBeNull();
    rerender(<Header height={300} isHome={true} />);
    expect(screen.getByText(/AI-powered/)).toBeInTheDocument();
    act(() => setWidth(500));
    expect(screen.getByText("TripSlice")).toBeInTheDocument();
  });
});

describe("Carousel", () => {
  it("slides images left and recycles the ones that leave the screen", () => {
    const frames: FrameRequestCallback[] = [];
    vi.spyOn(window, "requestAnimationFrame").mockImplementation((cb) => {
      frames.push(cb);
      return frames.length;
    });
    const cancel = vi.spyOn(window, "cancelAnimationFrame").mockImplementation(() => {});
    const { container, unmount } = render(
      <Carousel images={["/a.jpg", "/b.jpg"]} flowSpeed={1000} carouselHeight={100} height={100} width="100px" />
    );
    const slides = container.querySelectorAll<HTMLDivElement>("div.absolute");
    slides.forEach((s) => Object.defineProperty(s, "offsetWidth", { value: 100 }));

    act(() => frames.shift()!(1000)); // first frame only records the time
    act(() => frames.shift()!(1050)); // 50ms at 1000px/s moves 50px
    expect(slides[0].style.left).toBe("-50px");
    act(() => frames.shift()!(1150)); // slide 0 (at -150px) is off screen and moves after slide 1 (at -50px)
    expect(slides[1].style.left).toBe("-50px");
    expect(slides[0].style.left).toBe("50px");

    unmount();
    expect(cancel).toHaveBeenCalled();
  });
});

describe("NotFoundPage", () => {
  it("links back to the dashboard", () => {
    renderWithRouter(<NotFoundPage />);
    expect(screen.getByText("Back to Home").closest("a")).toHaveAttribute("href", "/app/dashboard");
  });
});

