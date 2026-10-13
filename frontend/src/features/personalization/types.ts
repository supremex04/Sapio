import type { GlyphName } from "../../shared/illustrations/glyphs";

export type AcademicLevel = "SCHOOL" | "HIGHER_SECONDARY" | "UNDERGRADUATE" | "POSTGRADUATE" | "DOCTORAL" | "NONE";
export type Occupation = "STUDENT" | "PROFESSIONAL" | "EDUCATOR" | "SELF_LEARNER";
export type Depth = "CONCISE" | "BALANCED" | "IN_DEPTH";
export type Preference = "EXAMPLES" | "ANALOGIES" | "APPLICATIONS" | "FORMAL" | "CODE" | "STEP_BY_STEP";
export type Goal = "EXAM" | "UNDERSTANDING" | "WORK" | "CURIOSITY";

export interface Personalization {
  academicLevel: AcademicLevel | null;
  occupation: Occupation | null;
  fieldOfStudy: string | null;
  depth: Depth | null;
  preferences: Preference[];
  goal: Goal | null;
}

export interface PersonalizationResponse extends Personalization {
  /** False until the learner has set anything. */
  configured: boolean;
  updatedAt: string | null;
}

export const EMPTY: Personalization = { academicLevel: null, occupation: null, fieldOfStudy: null, depth: null, preferences: [], goal: null };

interface Option<T> {
  value: T;
  label: string;
  hint: string;
  /** One of Sapio's own glyphs, not an emoji. */
  icon: GlyphName;
}

export const OCCUPATIONS: Option<Occupation>[] = [
  { value: "STUDENT", label: "Student", hint: "School, college or university", icon: "graduate" },
  { value: "PROFESSIONAL", label: "Working professional", hint: "Learning for your job", icon: "briefcase" },
  { value: "EDUCATOR", label: "Teacher", hint: "Also how to explain it", icon: "teacher" },
  { value: "SELF_LEARNER", label: "Self-learner", hint: "Learning on your own", icon: "compass" },
];

export const LEVELS: Option<AcademicLevel>[] = [
  { value: "SCHOOL", label: "School", hint: "Plain words, small steps", icon: "pencil" },
  { value: "HIGHER_SECONDARY", label: "+2 / A-level", hint: "Standard terms, explained", icon: "books" },
  { value: "UNDERGRADUATE", label: "Undergraduate", hint: "Technical, mechanisms and why", icon: "university" },
  { value: "POSTGRADUATE", label: "Postgraduate", hint: "Assumes basics, goes deeper", icon: "diploma" },
  { value: "DOCTORAL", label: "Doctorate / research", hint: "Rigorous, nuances, limits", icon: "flask" },
  { value: "NONE", label: "No formal background", hint: "Everyday words and examples", icon: "sprout" },
];

export const DEPTHS: { value: Depth; label: string; hint: string }[] = [
  { value: "CONCISE", label: "Concise", hint: "The essentials, short and to the point." },
  { value: "BALANCED", label: "Balanced", hint: "The essentials, the reasoning and one good example." },
  { value: "IN_DEPTH", label: "In depth", hint: "Mechanisms, derivations, edge cases and trade-offs." },
];

export const PREFERENCES: Option<Preference>[] = [
  { value: "EXAMPLES", label: "Worked examples", hint: "Concrete numbers and cases", icon: "target" },
  { value: "ANALOGIES", label: "Analogies", hint: "Links to familiar ideas", icon: "venn" },
  { value: "APPLICATIONS", label: "Real-world uses", hint: "Where it's used and why", icon: "gear" },
  { value: "FORMAL", label: "Formal maths", hint: "Definitions, notation, proofs", icon: "sigma" },
  { value: "CODE", label: "Code", hint: "Short snippets when it fits", icon: "code" },
  { value: "STEP_BY_STEP", label: "Step by step", hint: "Procedures and checklists", icon: "stairs" },
];

export const GOALS: Option<Goal>[] = [
  { value: "EXAM", label: "Exam prep", hint: "What's examined, common mistakes", icon: "trophy" },
  { value: "UNDERSTANDING", label: "Deep understanding", hint: "The why and the connections", icon: "bulb" },
  { value: "WORK", label: "Use it at work", hint: "Practical use and pitfalls", icon: "wrench" },
  { value: "CURIOSITY", label: "Curiosity", hint: "The bigger picture", icon: "sparkle" },
];

const label = <T,>(options: { value: T; label: string }[], value: T | null) => options.find((o) => o.value === value)?.label;

const LEARNER: Record<AcademicLevel, string> = {
  SCHOOL: "a school student",
  HIGHER_SECONDARY: "a +2 / A-level student",
  UNDERGRADUATE: "an undergraduate",
  POSTGRADUATE: "a postgraduate",
  DOCTORAL: "a researcher",
  NONE: "someone new to the subject",
};

/** A plain-language summary of what Sapio will do with the profile (shown live while editing). */
export function describe(p: Personalization): string[] {
  const lines: string[] = [];
  const field = p.fieldOfStudy?.trim();
  const learner = p.academicLevel ? LEARNER[p.academicLevel] : null;
  if (learner) lines.push(`Explain things for ${learner}${field ? ` in ${field}` : ""}.`);
  else if (field) lines.push(`Use examples from ${field} when they fit.`);
  const depth = DEPTHS.find((d) => d.value === p.depth);
  if (depth) lines.push(`${depth.label}: ${depth.hint.charAt(0).toLowerCase()}${depth.hint.slice(1)}`);
  if (p.preferences.length) lines.push(`Use more ${p.preferences.map((x) => label(PREFERENCES, x)?.toLowerCase()).join(", ")}.`);
  const goal = label(GOALS, p.goal);
  if (goal) lines.push(`Focus on ${goal.toLowerCase()}.`);
  return lines;
}
