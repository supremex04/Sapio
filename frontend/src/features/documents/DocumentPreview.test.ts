import { describe, expect, it } from "vitest";
import { sectionMarkdown } from "./DocumentPreview";
import type { ContentSection } from "./types";

const figure = { id: "f1", url: "/api/figures/f1/image", caption: "Figure 1: [Cluster]", description: "d", kind: "DIAGRAM", width: 10, height: 10 };

describe("sectionMarkdown", () => {
  it("embeds known figures in place and drops unknown ones", () => {
    const section = {
      blocks: [
        { type: "text", markdown: "Intro", figureId: null, page: 1 },
        { type: "figure", markdown: null, figureId: "f1", page: 1 },
        { type: "figure", markdown: null, figureId: "missing", page: 1 },
        { type: "text", markdown: "After", figureId: null, page: 2 },
      ],
    } as unknown as ContentSection;
    expect(sectionMarkdown(section, { f1: figure })).toBe("Intro\n\n![Figure 1:  Cluster ](figure:f1)\n\nAfter");
  });
});
