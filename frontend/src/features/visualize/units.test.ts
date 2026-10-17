import { describe, expect, it } from "vitest";
import { si } from "./units";

describe("SI values", () => {
  it("picks the prefix and three significant figures", () => {
    expect(si(0.0025, "A")).toBe("2.50 mA");
    expect(si(1000, "\u2126")).toBe("1.00 k\u2126");
    expect(si(1e-6, "F")).toBe("1.00 \u00b5F");
    expect(si(3.1606, "V")).toBe("3.16 V");
    expect(si(-0.003, "A")).toBe("-3.00 mA");
    expect(si(0, "V")).toBe("0 V");
    expect(si(150000, "\u2126")).toBe("150 k\u2126");
  });
});
