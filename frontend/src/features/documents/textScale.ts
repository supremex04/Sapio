/** Preview text size steps, as a percentage added to the base size. */
export const TEXT_STEPS = [0, 25, 50, 75, 100] as const;
export type TextStep = (typeof TEXT_STEPS)[number];
/** The base reading size is small for long documents, so the preview starts one step up. */
export const DEFAULT_TEXT_STEP: TextStep = 25;
export const TEXT_STEP_KEY = "sapio.preview.textStep";

export function stepBy(current: TextStep, direction: 1 | -1): TextStep {
  const i = TEXT_STEPS.indexOf(current) + direction;
  return TEXT_STEPS[Math.min(Math.max(i, 0), TEXT_STEPS.length - 1)] ?? current;
}

export function readTextStep(): TextStep {
  try {
    const saved = window.localStorage.getItem(TEXT_STEP_KEY);
    const step = Number(saved);
    return saved !== null && (TEXT_STEPS as readonly number[]).includes(step) ? (step as TextStep) : DEFAULT_TEXT_STEP;
  } catch {
    return DEFAULT_TEXT_STEP;
  }
}

export function saveTextStep(step: TextStep): void {
  try {
    window.localStorage.setItem(TEXT_STEP_KEY, String(step));
  } catch {
    // not persisted; applies to this visit only
  }
}
