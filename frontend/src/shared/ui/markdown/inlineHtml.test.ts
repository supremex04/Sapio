import { describe, expect, it } from "vitest";
import { inlineHtmlToMarkdown } from "./inlineHtml";

describe("inlineHtmlToMarkdown", () => {
  it("turns extraction HTML into Markdown but leaves code alone", () => {
    expect(inlineHtmlToMarkdown("<mark>Learn First</mark> and <u>this</u><br>`<b>keep</b>`")).toBe(
      "**Learn First** and this\n`<b>keep</b>`",
    );
    expect(inlineHtmlToMarkdown("```\n<i>code</i>\n```")).toBe("```\n<i>code</i>\n```");
    expect(inlineHtmlToMarkdown("A\n<!-- Start of picture text -->\nPod1 Pod2\n<!-- End of picture text -->\nB")).toBe("A\n\nB");
    expect(inlineHtmlToMarkdown("- 🆃 Load Balancing 🆃 Resource Allocation")).toBe("- Load Balancing Resource Allocation");
  });
});
