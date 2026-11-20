import { describe, expect, it } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import InfoPopover from "./InfoPopover";

describe("InfoPopover", () => {
  function setup() {
    render(
      <div>
        <p>outside</p>
        <InfoPopover label="Upload rules">
          <p>the rules</p>
        </InfoPopover>
      </div>
    );
    return screen.getByLabelText("Upload rules", { selector: "button" });
  }

  it("opens and closes from the i button", () => {
    const button = setup();
    expect(screen.queryByText("the rules")).toBeNull();
    fireEvent.click(button);
    expect(button).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("the rules")).toBeInTheDocument();
    fireEvent.click(button);
    expect(screen.queryByText("the rules")).toBeNull();
  });

  it("closes on a click elsewhere or Escape, but not on a click inside", () => {
    const button = setup();
    fireEvent.click(button);
    fireEvent.mouseDown(screen.getByText("the rules"));
    expect(screen.getByText("the rules")).toBeInTheDocument();
    fireEvent.mouseDown(screen.getByText("outside"));
    expect(screen.queryByText("the rules")).toBeNull();

    fireEvent.click(button);
    fireEvent.keyDown(document, { key: "Enter" });
    expect(screen.getByText("the rules")).toBeInTheDocument();
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByText("the rules")).toBeNull();
  });
});
