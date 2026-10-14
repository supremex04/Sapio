import { describe, expect, it } from "vitest";
import { apply, insertBlock, insertLink, pasteUrlAsLink, sourceOffset, toggleLines, toggleWrap } from "./markdownCommands";

const sel = (doc: string, word: string) => ({ from: doc.indexOf(word), to: doc.indexOf(word) + word.length });

describe("toggleWrap", () => {
  it("wraps and unwraps bold, keeping the word selected", () => {
    const doc = "make this bold";
    const bold = toggleWrap(doc, sel(doc, "this"), "**");
    const once = apply(doc, bold);
    expect(once).toBe("make **this** bold");
    expect(once.slice(bold.selection.from, bold.selection.to)).toBe("this");
    expect(apply(once, toggleWrap(once, bold.selection, "**"))).toBe(doc);
  });

  it("tells italic from bold", () => {
    const doc = "a **b** c";
    const inner = sel(doc, "b");
    expect(apply(doc, toggleWrap(doc, inner, "*"))).toBe("a ***b*** c");
    const both = "a ***b*** c";
    expect(apply(both, toggleWrap(both, sel(both, "b"), "*"))).toBe("a **b** c");
    expect(apply(both, toggleWrap(both, sel(both, "b"), "**"))).toBe("a *b* c");
  });

  it("keeps spaces outside the markers and inserts empty markers at a caret", () => {
    const doc = "word next";
    expect(apply(doc, toggleWrap(doc, { from: 0, to: 5 }, "**"))).toBe("**word** next");
    const caret = toggleWrap(doc, { from: 4, to: 4 }, "$");
    expect(apply(doc, caret)).toBe("word$$ next");
    expect(caret.selection).toEqual({ from: 5, to: 5 });
  });

  it("unwraps when the markers are part of the selection", () => {
    const doc = "x **y** z";
    expect(apply(doc, toggleWrap(doc, sel(doc, "**y**"), "**"))).toBe("x y z");
  });
});

describe("links", () => {
  it("turns selected text into a link with the URL selected to type", () => {
    const doc = "see the docs";
    const edit = insertLink(doc, sel(doc, "docs"));
    const out = apply(doc, edit);
    expect(out).toBe("see the [docs](url)");
    expect(out.slice(edit.selection.from, edit.selection.to)).toBe("url");
  });

  it("links selected text when a URL is pasted over it", () => {
    const doc = "see the docs";
    expect(apply(doc, pasteUrlAsLink(doc, sel(doc, "docs"), "https://example.com")!)).toBe("see the [docs](https://example.com)");
    expect(pasteUrlAsLink(doc, sel(doc, "docs"), "not a url")).toBeNull();
  });
});

describe("toggleLines", () => {
  it("numbers a list and removes it again", () => {
    const doc = "one\ntwo\nthree";
    const list = apply(doc, toggleLines(doc, { from: 0, to: doc.length }, "numbered"));
    expect(list).toBe("1. one\n2. two\n3. three");
    expect(apply(list, toggleLines(list, { from: 0, to: list.length }, "numbered"))).toBe(doc);
  });

  it("switches one kind of line marker for another", () => {
    const doc = "## Title";
    expect(apply(doc, toggleLines(doc, { from: 3, to: 3 }, "h1"))).toBe("# Title");
    const bullets = "- a\n- b";
    expect(apply(bullets, toggleLines(bullets, { from: 0, to: bullets.length }, "task"))).toBe("- [ ] a\n- [ ] b");
  });
});

describe("blocks", () => {
  it("puts display math on its own lines with the caret inside", () => {
    const doc = "text";
    const edit = insertBlock(doc, { from: 4, to: 4 }, "$$");
    expect(apply(doc, edit)).toBe("text\n$$\n\n$$");
    expect(edit.selection.from).toBe(8);
  });
});

describe("sourceOffset", () => {
  const md = "Osmosis is the **diffusion** of water.\n\nIt needs a *membrane* that is partially permeable.";

  it("finds the clicked spot in the source despite Markdown syntax", () => {
    const rendered = "Osmosis is the diffusion of water. It needs a membrane that is partially permeable.";
    const before = rendered.slice(0, rendered.indexOf("that is") + 4);
    const at = sourceOffset(md, before, rendered.length);
    expect(md.slice(0, at).endsWith("that")).toBe(true);
  });

  it("falls back to the proportional position when nothing matches", () => {
    expect(sourceOffset("abcdef", "zzz", 6)).toBe(3);
  });
});

describe("mathAt", () => {
  it("finds inline and display formulas around the caret", async () => {
    const { mathAt } = await import("./markdownCommands");
    const doc = "Energy $E = mc^2$ here.\n\n$$\n\\int_0^1 x\\,dx\n$$\n\nCosts $5 and $6.";
    expect(mathAt(doc, doc.indexOf("mc"))).toMatchObject({ tex: "E = mc^2", display: false });
    expect(mathAt(doc, doc.indexOf("int"))).toMatchObject({ tex: "\\int_0^1 x\\,dx", display: true });
    expect(mathAt(doc, doc.indexOf("here"))).toBeNull();
  });
});
