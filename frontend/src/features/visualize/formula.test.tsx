import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { ChemicalFormula, isFormula } from "./formula";

const html = (formula: string) => renderToStaticMarkup(<ChemicalFormula formula={formula} />);

describe("chemical formulas", () => {
  it("writes element counts as subscripts", () => {
    expect(html("C8H10N4O2")).toContain("C<sub>8</sub>H<sub>10</sub>N<sub>4</sub>O<sub>2</sub>");
    expect(html("CH4O")).toContain("CH<sub>4</sub>O");
  });

  it("writes a charge as a superscript, number first", () => {
    expect(html("C2H3O2-")).toContain("O<sub>2</sub><sup>−</sup>");
    expect(html("C5H6N+2")).toContain("N<sup>2+</sup>");
    expect(html("Na+")).toContain("Na<sup>+</sup>");
  });
});

describe("formula requests", () => {
  it("typesets a formula on its own, not a question that mentions one", () => {
    expect(isFormula("a^2 + b^2 = c^2")).toBe(true);
    expect(isFormula("e^{i\\pi} + 1 = 0")).toBe(true);
    expect(isFormula("\\sin^2 x + \\cos^2 x = 1")).toBe(true);
    expect(isFormula("why is x^2 a parabola")).toBe(false);
    expect(isFormula("F = ma")).toBe(false); // nothing to typeset
    expect(isFormula("costs $5^2")).toBe(false);
  });
});
