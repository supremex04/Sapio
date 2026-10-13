import { describe as suite, expect, it } from "vitest";
import { describe, EMPTY } from "./types";

suite("describe", () => {
  it("says nothing for an empty profile", () => {
    expect(describe(EMPTY)).toEqual([]);
  });

  it("uses the field on its own when no level is set", () => {
    expect(describe({ ...EMPTY, fieldOfStudy: "Nursing" })).toEqual(["Use examples from Nursing when they fit."]);
  });

  it("summarises the profile in plain language", () => {
    const lines = describe({
      ...EMPTY,
      academicLevel: "UNDERGRADUATE",
      fieldOfStudy: "Computer engineering",
      depth: "IN_DEPTH",
      preferences: ["CODE", "EXAMPLES"],
      goal: "EXAM",
    });
    expect(lines).toEqual([
      "Explain things for an undergraduate in Computer engineering.",
      "In depth: mechanisms, derivations, edge cases and trade-offs.",
      "Use more code, worked examples.",
      "Focus on exam prep.",
    ]);
  });
});
