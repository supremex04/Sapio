import { describe, expect, it } from "vitest";
import { coveringUnits, sectionMarkdown } from "./DocumentPreview";
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

describe("coveringUnits", () => {
  it("maps each section to the unit covering it", () => {
    const unit = { fromLabel: "1", toLabel: "1.2", sections: 3, pageFrom: 1, pageTo: 5 };
    const s = (id: string, parentId: string | null, isUnit = false) =>
      ({ id, parentId, unit: isUnit ? unit : null }) as unknown as ContentSection;
    const map = coveringUnits([s("1", null, true), s("1.1", "1"), s("1.1.1", "1.1"), s("2", null), s("2.1", "2", true)]);
    expect(Object.fromEntries(map)).toEqual({ "1": "1", "1.1": "1", "1.1.1": "1", "2.1": "2.1" });
  });
});
