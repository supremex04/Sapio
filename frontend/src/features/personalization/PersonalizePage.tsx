import { Check, RotateCcw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { errorMessage } from "../../shared/api/errors";
import { Emoji } from "../../shared/illustrations/Emoji";
import { Alert } from "../../shared/ui/Alert";
import { Button } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { cx } from "../../shared/ui/cx";
import { PageTransition } from "../../shared/ui/PageTransition";
import { SkeletonText } from "../../shared/ui/Skeleton";
import { useToast } from "../../shared/ui/Toast";
import { personalizationApi } from "./api";
import { DEPTHS, describe, EMPTY, GOALS, LEVELS, OCCUPATIONS, PREFERENCES, type Personalization } from "./types";
import "./personalization.css";

const same = (a: Personalization, b: Personalization) => JSON.stringify(a) === JSON.stringify(b);

/**
 * "Personalize": who the learner is and how they like to learn. Lessons, notes, tests and the tutor are written for
 * it (depth, vocabulary, examples, emphasis), never changing facts or grading. Editable any time.
 */
export function PersonalizePage() {
  const toast = useToast();
  const [saved, setSaved] = useState<Personalization | null>(null);
  const [draft, setDraft] = useState<Personalization>(EMPTY);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    personalizationApi.get().then(
      ({ configured: _c, updatedAt: _u, ...profile }) => {
        setSaved(profile);
        setDraft(profile);
      },
      (e) => setError(errorMessage(e)),
    );
  }, []);

  const dirty = saved !== null && !same(saved, draft);
  const summary = useMemo(() => describe(draft), [draft]);
  const set = <K extends keyof Personalization>(key: K, value: Personalization[K]) => setDraft((d) => ({ ...d, [key]: value }));
  const toggle = <K extends "academicLevel" | "occupation" | "depth" | "goal">(key: K, value: Personalization[K]) =>
    set(key, draft[key] === value ? null : value);

  async function save() {
    setSaving(true);
    setError(null);
    try {
      const { configured: _c, updatedAt: _u, ...profile } = await personalizationApi.save(draft);
      setSaved(profile);
      setDraft(profile);
      toast.show("success", "Saved. New lessons, notes and tests will be written for you.");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  return (
    <PageTransition>
      <div className="stack-lg pz">
        <header className="pz-header">
          <Emoji name="personalize" size={56} />
          <div className="lib-header-text">
            <h1>Personalize</h1>
            <p className="muted">Tell Sapio who you are and how you like to learn. Lessons, notes, tests and the tutor adapt to it. Change it any time.</p>
          </div>
        </header>

        {error && <Alert>{error}</Alert>}

        {saved === null ? (
          <Card>
            <SkeletonText lines={8} />
          </Card>
        ) : (
          <>
            <Card className="pz-card">
              <h2 className="pz-title">About you</h2>
              <Field label="What do you do?">
                <Tiles options={OCCUPATIONS} value={draft.occupation} onPick={(v) => toggle("occupation", v)} />
              </Field>
              <Field label="Your academic level" hint="Sets how technical lessons are, and how much they assume you know.">
                <Tiles options={LEVELS} value={draft.academicLevel} onPick={(v) => toggle("academicLevel", v)} />
              </Field>
              <div className="field">
                <label htmlFor="pz-field">Field of study or work</label>
                <input
                  id="pz-field"
                  className="input"
                  maxLength={100}
                  placeholder="e.g. Computer engineering, Nursing, Commerce"
                  value={draft.fieldOfStudy ?? ""}
                  onChange={(e) => set("fieldOfStudy", e.target.value || null)}
                />
                <span className="xs muted">Examples will come from your field when they fit.</span>
              </div>
            </Card>

            <Card className="pz-card">
              <h2 className="pz-title">How you like to learn</h2>
              <Field label="How much detail?">
                <div className="pz-depth" role="radiogroup" aria-label="How much detail">
                  {DEPTHS.map((d) => (
                    <button
                      key={d.value}
                      type="button"
                      role="radio"
                      aria-checked={draft.depth === d.value}
                      className={cx("pz-option", draft.depth === d.value && "is-selected")}
                      onClick={() => toggle("depth", d.value)}
                    >
                      <strong>{d.label}</strong>
                      <span className="xs muted">{d.hint}</span>
                    </button>
                  ))}
                </div>
              </Field>
              <Field label="What helps you most?" hint="Pick any. These are proven ways to make ideas stick.">
                <div className="pz-tiles" role="group" aria-label="What helps you most">
                  {PREFERENCES.map((o) => {
                    const on = draft.preferences.includes(o.value);
                    return (
                      <button
                        key={o.value}
                        type="button"
                        aria-pressed={on}
                        className={cx("pz-option", on && "is-selected")}
                        onClick={() => set("preferences", on ? draft.preferences.filter((p) => p !== o.value) : [...draft.preferences, o.value])}
                      >
                        <Emoji name={o.emoji} size={26} />
                        <span className="pz-option-text">
                          <strong>{o.label}</strong>
                          <span className="xs muted">{o.hint}</span>
                        </span>
                        {on && <Check size={16} strokeWidth={3} className="pz-check" aria-hidden />}
                      </button>
                    );
                  })}
                </div>
              </Field>
              <Field label="Your main goal">
                <Tiles options={GOALS} value={draft.goal} onPick={(v) => toggle("goal", v)} />
              </Field>
            </Card>

            <Card className="pz-card pz-summary" tone="purple">
              <h2 className="pz-title">
                <Emoji name="sparkles" size={24} /> Sapio will
              </h2>
              {summary.length ? (
                <ul>
                  {summary.map((line) => (
                    <li key={line}>{line}</li>
                  ))}
                </ul>
              ) : (
                <p className="small">Write for a general audience. Pick anything above to tailor it.</p>
              )}
              <p className="xs muted">
                Facts and how answers are marked stay the same for everyone. Notes you open are rewritten for your new profile; lessons you
                have already started stay as they are.
              </p>
            </Card>

            <div className="pz-actions">
              <Button variant="ghost" icon={<RotateCcw size={16} />} disabled={!dirty || saving} onClick={() => setDraft(saved)}>
                Undo changes
              </Button>
              <Button variant="primary" size="lg" loading={saving} disabled={!dirty} onClick={save}>
                Save
              </Button>
            </div>
          </>
        )}
      </div>
    </PageTransition>
  );
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <fieldset className="pz-field">
      <legend className="field-label">{label}</legend>
      {hint && <p className="xs muted pz-hint">{hint}</p>}
      {children}
    </fieldset>
  );
}

function Tiles<T extends string>({ options, value, onPick }: { options: { value: T; label: string; hint: string; emoji: Parameters<typeof Emoji>[0]["name"] }[]; value: T | null; onPick: (v: T) => void }) {
  return (
    <div className="pz-tiles" role="radiogroup">
      {options.map((o) => (
        <button key={o.value} type="button" role="radio" aria-checked={value === o.value} className={cx("pz-option", value === o.value && "is-selected")} onClick={() => onPick(o.value)}>
          <Emoji name={o.emoji} size={26} />
          <span className="pz-option-text">
            <strong>{o.label}</strong>
            <span className="xs muted">{o.hint}</span>
          </span>
        </button>
      ))}
    </div>
  );
}
