import type { IconName } from "../../shared/illustrations/Emoji";

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
  emoji: IconName;
}

export const OCCUPATIONS: Option<Occupation>[] = [
  { value: "STUDENT", label: "Student", hint: "School, college or university", emoji: "graduation-cap" },
  { value: "PROFESSIONAL", label: "Working professional", hint: "Learning for your job", emoji: "chart-increasing" },
  { value: "EDUCATOR", label: "Teacher", hint: "Also how to explain it", emoji: "writing-hand" },
  { value: "SELF_LEARNER", label: "Self-learner", hint: "Learning on your own", emoji: "seedling" },
];

export const LEVELS: Option<AcademicLevel>[] = [
  { value: "SCHOOL", label: "School", hint: "Plain words, small steps", emoji: "open-book" },
  { value: "HIGHER_SECONDARY", label: "+2 / A-level", hint: "Standard terms, explained", emoji: "memo" },
  { value: "UNDERGRADUATE", label: "Undergraduate", hint: "Technical, mechanisms and why", emoji: "graduation-cap" },
  { value: "POSTGRADUATE", label: "Postgraduate", hint: "Assumes basics, goes deeper", emoji: "brain" },
  { value: "DOCTORAL", label: "Doctorate / research", hint: "Rigorous, nuances, limits", emoji: "test-tube" },
  { value: "NONE", label: "No formal background", hint: "Everyday words and examples", emoji: "light-bulb" },
];

export const DEPTHS: { value: Depth; label: string; hint: string }[] = [
  { value: "CONCISE", label: "Concise", hint: "The essentials, short and to the point." },
  { value: "BALANCED", label: "Balanced", hint: "The essentials, the reasoning and one good example." },
  { value: "IN_DEPTH", label: "In depth", hint: "Mechanisms, derivations, edge cases and trade-offs." },
];

export const PREFERENCES: Option<Preference>[] = [
  { value: "EXAMPLES", label: "Worked examples", hint: "Concrete numbers and cases", emoji: "bullseye" },
  { value: "ANALOGIES", label: "Analogies", hint: "Links to familiar ideas", emoji: "puzzle-piece" },
  { value: "APPLICATIONS", label: "Real-world uses", hint: "Where it's used and why", emoji: "globe-showing-asia-australia" },
  { value: "FORMAL", label: "Formal maths", hint: "Definitions, notation, proofs", emoji: "scroll" },
  { value: "CODE", label: "Code", hint: "Short snippets when it fits", emoji: "high-voltage" },
  { value: "STEP_BY_STEP", label: "Step by step", hint: "Procedures and checklists", emoji: "check-mark" },
];

export const GOALS: Option<Goal>[] = [
  { value: "EXAM", label: "Exam prep", hint: "What's examined, common mistakes", emoji: "1st-place-medal" },
  { value: "UNDERSTANDING", label: "Deep understanding", hint: "The why and the connections", emoji: "brain" },
  { value: "WORK", label: "Use it at work", hint: "Practical use and pitfalls", emoji: "chart-increasing" },
  { value: "CURIOSITY", label: "Curiosity", hint: "The bigger picture", emoji: "sparkles" },
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
