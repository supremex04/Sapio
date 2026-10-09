import { describe, expect, it } from "vitest";
import { isConfidentMiss, optionLetter, verdictCopy } from "./feedback";

describe("feedback copy", () => {
  it("never uses alarming tones for mistakes", () => {
    expect(verdictCopy("INCORRECT", "normal").tone).toBe("warning");
    expect(verdictCopy("CORRECT", "normal").tone).toBe("success");
    expect(verdictCopy("INCORRECT", "warmup").title).toMatch(/guess/i);
  });

  it("detects confident misses only", () => {
    expect(isConfidentMiss({ verdict: "INCORRECT", confidence: "HIGH" })).toBe(true);
    expect(isConfidentMiss({ verdict: "INCORRECT", confidence: "LOW" })).toBe(false);
    expect(isConfidentMiss({ verdict: "CORRECT", confidence: "HIGH" })).toBe(false);
    expect(isConfidentMiss({ verdict: "INCORRECT", confidence: null })).toBe(false);
  });

  it("letters options", () => {
    expect([0, 1, 3].map(optionLetter)).toEqual(["A", "B", "D"]);
  });
});
