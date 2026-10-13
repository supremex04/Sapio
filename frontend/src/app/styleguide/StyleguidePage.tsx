import { ArrowRight, Check, Upload, X } from "lucide-react";
import { useState, type ReactNode } from "react";
import { ThemeSwitch } from "../../features/theme/ThemeToggle";
import { Avatar } from "../../shared/illustrations/Avatar";
import { Sage, type SageMood } from "../../shared/illustrations/Sage";
import { Emoji } from "../../shared/illustrations/Emoji";
import { EMOJI_NAMES } from "../../shared/illustrations/emojiNames";
import { Alert } from "../../shared/ui/Alert";
import { Badge } from "../../shared/ui/Badge";
import { Button } from "../../shared/ui/Button";
import { Card, type CardTone } from "../../shared/ui/Card";
import { Chip } from "../../shared/ui/Chip";
import { EmptyState } from "../../shared/ui/EmptyState";
import { FeedbackSheet } from "../../shared/ui/FeedbackSheet";
import { FormField } from "../../shared/ui/FormField";
import { ProgressBar, ProgressRing } from "../../shared/ui/Progress";
import { Segmented } from "../../shared/ui/Segmented";
import { Skeleton } from "../../shared/ui/Skeleton";
import { SpeechBubble } from "../../shared/ui/SpeechBubble";
import { StatTile } from "../../shared/ui/StatTile";
import { Tabs } from "../../shared/ui/Tabs";
import { useToast } from "../../shared/ui/Toast";
import "./styleguide.css";

const HUES = [
  { name: "blue", job: "Primary action · Learn · concepts" },
  { name: "green", job: "Correct · done · examples · goal" },
  { name: "red", job: "Incorrect (gently) · destructive" },
  { name: "amber", job: "XP · streak · warm-up" },
  { name: "purple", job: "Mastery · levels · summary · Test" },
  { name: "teal", job: "Recall: spaced retrieval (memory)" },
  { name: "pink", job: "Analogies (imagination)" },
] as const;
const STEPS = ["soft", "main", "edge", "ink"] as const;
const NEUTRALS = ["bg", "surface", "surface-2", "surface-3", "border", "border-strong", "text", "text-2", "text-muted"];
const MOODS: SageMood[] = ["happy", "cheer", "thinking", "encourage", "oops", "sleepy"];

/** Living reference of the "Diya" design system: everything here is the real component. */
export function StyleguidePage() {
  const toast = useToast();
  const [tab, setTab] = useState<"a" | "b" | "c">("a");
  const [seg, setSeg] = useState<"easy" | "mixed" | "hard">("mixed");
  return (
    <div className="sg">
      <header className="sg-head">
        <div className="row" style={{ gap: "var(--space-4)" }}>
          <Sage mood="cheer" size={84} />
          <div>
            <p className="eyebrow">Design system v2</p>
            <h1>Diya</h1>
            <p className="muted">Flat colour, tactile depth, one rounded typeface, and a friend called Sapi.</p>
          </div>
        </div>
        <ThemeSwitch />
      </header>

      <Section title="Colour" note="One hue per job. Steps: soft (tint) · main (fill) · edge (3D lip) · ink (text on soft).">
        <div className="sg-hues">
          {HUES.map((h) => (
            <div key={h.name} className="sg-hue">
              <div className="sg-swatches">
                {STEPS.map((s) => (
                  <div key={s} className="sg-swatch" style={{ background: `var(--${h.name}-${s})` }} title={`--${h.name}-${s}`}>
                    <span style={{ color: s === "soft" ? `var(--${h.name}-ink)` : "#fff" }}>{s}</span>
                  </div>
                ))}
              </div>
              <strong className="sg-hue-name">{h.name}</strong>
              <span className="xs muted">{h.job}</span>
            </div>
          ))}
        </div>
        <div className="sg-neutrals">
          {NEUTRALS.map((n) => (
            <div key={n} className="sg-neutral">
              <span className="sg-chip" style={{ background: `var(--${n})` }} />
              <code className="xs">--{n}</code>
            </div>
          ))}
        </div>
      </Section>

      <Section title="Typography" note="Nunito, heavy weights for hierarchy. One family only.">
        <div className="stack-sm">
          <div style={{ fontSize: "var(--text-display)", fontWeight: 900 }}>Display 38 · 900</div>
          <h1>Heading 1 · 30 · 800</h1>
          <h2>Heading 2 · 22 · 800</h2>
          <h3>Heading 3 · 18 · 800</h3>
          <p>Body 16 · 600. Short sentences, warm voice, second person.</p>
          <div className="markdown">
            <p>Lesson text 17 · 500 with comfortable line height for reading longer explanations.</p>
          </div>
          <p className="eyebrow">Label · 12 · 800 · uppercase</p>
        </div>
      </Section>

      <Section title="Buttons" note="The signature 3D edge: press it and it sinks. Labels are short verbs.">
        <div className="sg-row">
          <Button variant="primary">Continue</Button>
          <Button variant="success">Check</Button>
          <Button variant="danger">Got it</Button>
          <Button variant="accent">Claim XP</Button>
          <Button variant="mastery">Test yourself</Button>
          <Button variant="review">Recall</Button>
          <Button variant="soft">Maybe later</Button>
          <Button>Secondary</Button>
          <Button variant="ghost">Ghost</Button>
          <Button variant="primary" disabled>
            Disabled
          </Button>
        </div>
        <div className="sg-row">
          <Button variant="primary" size="sm">
            Small
          </Button>
          <Button variant="primary" icon={<Upload size={18} />}>
            With icon
          </Button>
          <Button variant="primary" size="lg" iconRight={<ArrowRight size={20} />}>
            Large
          </Button>
          <Button variant="primary" loading>
            Loading
          </Button>
        </div>
      </Section>

      <Section title="Cards, tiles & chips">
        <div className="sg-grid">
          <Card>
            <h3>Card</h3>
            <p className="muted small">2px border + 4px lip.</p>
          </Card>
          <Card interactive tabIndex={0}>
            <h3>Interactive</h3>
            <p className="muted small">Hover and press me.</p>
          </Card>
          {(["blue", "green", "amber", "purple", "teal", "red"] as CardTone[]).map((t) => (
            <Card key={t} tone={t} tight>
              <strong>tone="{t}"</strong>
            </Card>
          ))}
        </div>
        <div className="sg-grid">
          <StatTile emoji="fire" value="12" label="Day streak" />
          <StatTile emoji="high-voltage" value="1,240" label="Total XP" />
          <StatTile emoji="bullseye" value="8/10" label="Minutes today" />
        </div>
        <div className="sg-row">
          <Chip emoji="fire" tone="streak">
            12
          </Chip>
          <Chip emoji="high-voltage" tone="xp">
            +10 XP
          </Chip>
          <Chip emoji="brain" tone="mastery">
            72% mastery
          </Chip>
          <Chip emoji="recall" tone="review">
            4 due
          </Chip>
          <Chip onClick={() => undefined} pressed>
            Selected
          </Chip>
          <Badge tone="success">Strong</Badge>
          <Badge tone="warning">Developing</Badge>
          <Badge tone="danger">Weak</Badge>
          <Badge tone="primary">Ready</Badge>
          <Badge tone="mastery">Level 3</Badge>
          <Badge tone="review">Box 2</Badge>
          <Badge>Neutral</Badge>
        </div>
      </Section>

      <Section title="Progress">
        <div className="stack">
          <ProgressBar value={0.35} label="Lesson progress" size="lg" />
          <ProgressBar value={0.6} label="Level" color="var(--purple-main)" />
          <ProgressBar value={0.8} label="Small" size="sm" color="var(--blue-main)" />
          <div className="sg-row">
            <ProgressRing value={0.7} size={88} stroke={11} label="Daily goal">
              <Emoji name="bullseye" size={32} />
            </ProgressRing>
            <ProgressRing value={0.45} size={88} stroke={11} color="var(--purple-main)" label="Mastery">
              45%
            </ProgressRing>
          </div>
        </div>
      </Section>

      <Section title="Answers & feedback" note="Option states, then the sheet that slides up after Check.">
        <div className="sg-options">
          <button className="option">
            <span className="option-key">A</span> Default
          </button>
          <button className="option is-selected">
            <span className="option-key">B</span> Selected
          </button>
          <button className="option is-correct">
            <span className="option-key">
              <Check size={18} strokeWidth={3.5} />
            </span>{" "}
            Correct
          </button>
          <button className="option is-incorrect">
            <span className="option-key">
              <X size={18} strokeWidth={3.5} />
            </span>{" "}
            Incorrect
          </button>
        </div>
        <div className="stack">
          <FeedbackSheet inline tone="correct" mood="cheer" title="Nicely done!" actions={<Button variant="success">Continue</Button>}>
            Osmosis moves water from a dilute solution to a more concentrated one.
          </FeedbackSheet>
          <FeedbackSheet inline tone="partial" mood="encourage" title="Partly there" actions={<Button variant="accent">Continue</Button>}>
            You named diffusion. Add that it needs no energy.
          </FeedbackSheet>
          <FeedbackSheet inline tone="incorrect" mood="encourage" title="Not quite yet" actions={<Button variant="danger">Got it</Button>}>
            Active transport needs ATP because it moves against the gradient.
          </FeedbackSheet>
        </div>
      </Section>

      <Section title="Sapi" note="Our mascot: a kindly sage carrying the diya, the lamp of knowledge. The flame and the face show the mood. Once per screen, at moments that matter.">
        <div className="sg-row" style={{ alignItems: "flex-end" }}>
          {MOODS.map((m) => (
            <div key={m} className="sg-mood">
              <Sage mood={m} size={88} />
              <code className="xs">{m}</code>
            </div>
          ))}
        </div>
        <div className="row" style={{ gap: "var(--space-3)" }}>
          <Sage mood="happy" size={64} />
          <SpeechBubble>Guessing first helps you remember, even if you're wrong!</SpeechBubble>
        </div>
      </Section>

      <Section title="Icons & avatars" note="Fluent Emoji (MIT) for places, rewards and feelings; Lucide for actions. Avatars: DiceBear notionists (CC0).">
        <div className="sg-emoji">
          {EMOJI_NAMES.map((n) => (
            <div key={n} className="sg-emoji-cell" title={n}>
              <Emoji name={n} size={36} />
              <span>{n}</span>
            </div>
          ))}
        </div>
        <div className="sg-row">
          {["asha", "bikash", "chen", "dana", "emeka", "farah"].map((s) => (
            <Avatar key={s} seed={s} size={52} label={s} />
          ))}
        </div>
      </Section>

      <Section title="Navigation & inputs">
        <Tabs
          label="Example tabs"
          value={tab}
          onChange={setTab}
          items={[
            { value: "a", label: "Topics" },
            { value: "b", label: "Text" },
            { value: "c", label: "Tests" },
          ]}
        />
        <Segmented
          label="Difficulty"
          value={seg}
          onChange={setSeg}
          options={[
            { value: "easy", label: "Easy" },
            { value: "mixed", label: "Mixed" },
            { value: "hard", label: "Hard" },
          ]}
        />
        <div className="sg-grid">
          <FormField label="Email" name="sg-email" placeholder="you@school.edu" />
          <FormField label="With error" name="sg-err" error="That doesn't look like an email" defaultValue="asha@" />
        </div>
        <textarea className="textarea" placeholder="Explain it in your own words…" />
      </Section>

      <Section title="Feedback & states">
        <Alert tone="info">We'll email you when it's ready.</Alert>
        <Alert tone="success">Saved!</Alert>
        <Alert>Something went wrong. Try again.</Alert>
        <div className="sg-row">
          <Button size="sm" variant="accent" onClick={() => toast.show("xp", "+10 XP")}>
            XP toast
          </Button>
          <Button size="sm" variant="mastery" onClick={() => toast.show("level", "Level 4!")}>
            Level toast
          </Button>
          <Button size="sm" onClick={() => toast.show("error", "Couldn't save")}>
            Error toast
          </Button>
        </div>
        <Card>
          <EmptyState mood="sleepy" title="Nothing due right now" action={<Button variant="primary">Learn something new</Button>}>
            Your memory is fresh. Cards come back when they're due.
          </EmptyState>
        </Card>
        <Card>
          <div className="stack-sm">
            <Skeleton height={22} width="60%" />
            <Skeleton height={14} />
            <Skeleton height={14} width="80%" />
          </div>
        </Card>
      </Section>
    </div>
  );
}

function Section({ title, note, children }: { title: string; note?: string; children: ReactNode }) {
  return (
    <section className="sg-section">
      <div>
        <h2>{title}</h2>
        {note && <p className="muted small">{note}</p>}
      </div>
      {children}
    </section>
  );
}
