import { Fragment, type ReactNode } from "react";

/**
 * A chemical formula as RDKit writes it ("C8H10N4O2", "C2H3O2-", "C5H6N+2") with element counts as subscripts and
 * the charge as a superscript: C₈H₁₀N₄O₂, C₂H₃O₂⁻.
 */
export function ChemicalFormula({ formula }: { formula: string }) {
  const charge = /([+-]\d*)$/.exec(formula);
  const body = charge ? formula.slice(0, charge.index) : formula;
  const parts: ReactNode[] = body.split(/(\d+)/).map((part, i) => (/^\d+$/.test(part) && i > 0 ? <sub key={i}>{part}</sub> : <Fragment key={i}>{part}</Fragment>));
  if (charge?.[1]) {
    const [sign, ...count] = charge[1];
    // Chemists write the number first: 2+, not +2.
    parts.push(<sup key="charge">{count.join("") + (sign === "-" ? "−" : "+")}</sup>);
  }
  return <span className="viz-formula" aria-label={formula}>{parts}</span>;
}

const MATH_WORDS = new Set([
  "sin", "cos", "tan", "cot", "sec", "csc", "log", "ln", "exp", "lim", "sum", "int", "sqrt", "frac", "pi", "theta",
  "alpha", "beta", "gamma", "delta", "lambda", "mu", "sigma", "omega", "phi", "psi", "rho", "tau", "epsilon", "infty",
  "cdot", "times", "left", "right", "mathrm", "vec", "hat", "dot", "partial", "nabla", "approx", "neq", "leq", "geq",
]);

/**
 * Whether a request is a formula on its own (so it can be typeset): it uses ^, _ or a LaTeX command, has no $ of its
 * own, and no words beyond math names. "a^2 + b^2 = c^2" is; "why is x^2 a parabola" is not, and stays text.
 */
export function isFormula(text: string): boolean {
  const t = text.trim();
  if (!t || t.length > 200 || t.includes("$") || t.includes("\n") || !/[\\^_]/.test(t)) return false;
  const words = t.replace(/\\[a-zA-Z]+/g, " ").match(/[A-Za-z]{3,}/g) ?? [];
  return words.every((w) => MATH_WORDS.has(w.toLowerCase()));
}
