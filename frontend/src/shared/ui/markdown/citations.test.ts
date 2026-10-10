import { describe, expect, it } from "vitest";
import { linkCitations } from "./citations";

describe("linkCitations", () => {
  it("turns citations into cite links", () => {
    expect(linkCitations("Water moves [§1.1].")).toBe("Water moves [§1.1](cite:1.1).");
  });

  it("splits grouped citations", () => {
    expect(linkCitations("Both [§1.2, §2.3]")).toBe("Both [§1.2](cite:1.2) [§2.3](cite:2.3)");
  });

  it("keeps only the last mention of a section per paragraph", () => {
    const text = "A [§1.1.1]. B [§1.1.1].\n\nC [§1.1.1].";
    expect(linkCitations(text)).toBe("A. B [§1.1.1](cite:1.1.1).\n\nC [§1.1.1](cite:1.1.1).");
  });

  it("leaves text without citations alone", () => {
    expect(linkCitations("Plain [link](https://x.y) and [1]")).toBe("Plain [link](https://x.y) and [1]");
  });
});
