import { describe, expect, it } from "vitest";
import { filenameFromDisposition } from "./download";

describe("filenameFromDisposition", () => {
  it("prefers the RFC 5987 encoded name", () => {
    const header = `attachment; filename="=?UTF-8?Q?x?="; filename*=UTF-8''r%C3%A9sum%C3%A9.md`;
    expect(filenameFromDisposition(header, "x.md")).toBe("résumé.md");
  });

  it("falls back to the plain name, then the default", () => {
    expect(filenameFromDisposition('attachment; filename="notes.md"', "x.md")).toBe("notes.md");
    expect(filenameFromDisposition(null, "x.md")).toBe("x.md");
  });
});
