import { AArrowDown, AArrowUp } from "lucide-react";
import { stepBy, TEXT_STEPS, type TextStep } from "./textScale";

/** A− / level / A+ for the preview's reading text (+0% to +100%). */
export function TextSizeControl({ step, onChange }: { step: TextStep; onChange: (step: TextStep) => void }) {
  const min = TEXT_STEPS[0];
  const max = TEXT_STEPS[TEXT_STEPS.length - 1];
  return (
    <div className="text-size" role="group" aria-label="Text size">
      <button type="button" className="text-size-btn" onClick={() => onChange(stepBy(step, -1))} disabled={step === min} aria-label="Smaller text" title="Smaller text">
        <AArrowDown size={18} strokeWidth={2.5} aria-hidden />
      </button>
      <output className="text-size-value" aria-live="polite" aria-label={`Text size +${step}%`}>
        +{step}%
      </output>
      <button type="button" className="text-size-btn" onClick={() => onChange(stepBy(step, 1))} disabled={step === max} aria-label="Larger text" title="Larger text">
        <AArrowUp size={18} strokeWidth={2.5} aria-hidden />
      </button>
    </div>
  );
}
