# Design system

Status: **implemented (M2)** · Code: `frontend/src/styles`, `frontend/src/shared/ui`

Ved should feel **calm while you read, vivid when you achieve**. This page sets the rules. The
reasons behind them are in [learning-science.md](../architecture/learning-science.md).

## Principles

1. **Calm surfaces, meaningful colour.** Reading and answering happen on neutral surfaces. Saturated
   colour, gradients and motion are reserved for progress, achievement and the single primary action.
2. **One thing at a time.** Progressive disclosure: one lesson section, one question, one recommended
   next step.
3. **Mistakes are amber, never alarming.** Wrong answers use the warning palette with encouraging copy.
   Rose (danger) is only for destructive actions and system errors.
4. **Every action states its cost.** Show time estimates ("~2 min") on next steps, tests and reviews.
5. **Consistency over novelty.** Use the kit components and tokens. No raw colours or one-off sizes in
   feature CSS.

## Tokens (`styles/tokens.css`)

All colours, sizes and timings are CSS custom properties. The light palette lives on `:root`, the dark
palette on `:root[data-theme="dark"]`, both complete.

| Group | Tokens | Notes |
|---|---|---|
| Typography | `--font-sans` (Inter Variable), `--font-display` (Fraunces Variable), `--font-mono`; `--text-xs … --text-3xl` | Display face only for page titles, lesson headings and celebratory moments. Numbers and UI text use sans. |
| Spacing | `--space-1 … --space-16` (4 px grid) | |
| Radius | `--radius-sm` 8, `-md` 12, `-lg` 16, `-xl` 24, `-full` | Cards `lg`; hero blocks `xl`; inputs/buttons `sm` |
| Surfaces | `--color-bg`, `--color-surface`, `-surface-2`, `-surface-3`, `--color-border`, `-border-strong` | |
| Text | `--color-text`, `-text-2`, `-text-muted`, `-text-inverse` | Text never wears a data/series colour. |
| Brand | `--color-primary` (indigo-violet) `+ -hover, -soft, -soft-2, -text` | Primary actions, focus, progress |
| Accent | `--color-accent` (warm amber) `+ -soft, -text` | XP, streaks, goals, celebration, warm-up |
| Status | `--color-success`, `-warning`, `-danger` `+ -soft, -text` | Always paired with an icon and a label |
| Lesson kinds | `--kind-concept`, `-example`, `-analogy`, `-summary` | Left rule + label colour per section kind |
| Charts | `--chart-bar`, `--chart-grid` | Validated with the dataviz palette checker in both themes |
| Elevation | `--shadow-sm/md/lg`, `--shadow-glow` | Glow only on the primary call to action |
| Motion | `--ease-out`, `--ease-spring`, `--duration-fast/base/slow` | |
| Focus | `--focus-ring` | Visible focus on every interactive element |

## Theming

- `ThemeProvider` stores `light | dark | system` in `localStorage["ved-theme"]` (wrapped in try/catch)
  and sets `data-theme` on `<html>`. An inline script in `index.html` applies it before first paint.
- The toggle in the top bar cycles light → dark → system.
- New colours must be added to **both** palettes.

## Components (`shared/ui`)

| Component | Use |
|---|---|
| `Button` / `ButtonLink` / `IconButton` | Variants `primary` (one per view), `secondary`, `soft`, `ghost`, `accent`, `danger`; sizes `sm/md/lg`; `loading` state |
| `Card`, `CardHeader` | Default container; `interactive` for clickable cards |
| `Badge` | Tones `neutral/primary/accent/success/warning/danger`, optional icon |
| `ProgressBar`, `ProgressRing` | Animated; always with an accessible label |
| `Skeleton`, `SkeletonText` | Loading placeholders that match the final layout |
| `Spinner` | Inline busy state |
| `Tabs`, `Segmented` | Tabs for page sections (arrow-key navigation); segmented control for view toggles |
| `Toast` (`useToast`) | `success / error / info / xp / level`; bottom-right, auto-dismiss |
| `ConfirmButton` | Two-step inline confirmation. Never `window.confirm` |
| `EmptyState` | Icon + title + one helpful sentence + optional action |
| `Alert` | Inline error / info / success with optional action |
| `Markdown` | All AI/OCR content: GFM tables, KaTeX math, no raw HTML |
| `PageTransition` | Standard page enter animation |

Feature components follow the same rules: `QuestionCard` (all question answering), `MasteryMeter`,
`PipelineStepper`, `GeneratingState`, `ActivityChart`, `GoalPicker`.

## Motion

- Use `motion/react`. The app is wrapped in `<MotionConfig reducedMotion="user">`, and CSS honours
  `prefers-reduced-motion`. Confetti is skipped when reduced motion is on.
- Durations ≤ 400 ms, eased out. Springs only for small "pop" moments (verdict icon, toast).
- What animates: page enter, step reveal, answer verdict (gentle shake on a miss, pop on correct),
  progress fills, toasts, the recommended-node pulse.
- Confetti is reserved for **real achievements**: topic completed, score ≥ 80 %, level-up, daily goal,
  finished review session.

## Copy

- Second person, warm and brief. Praise strategy and effort, never ability.
- Never shame: no "you lost your streak", no "wrong!". Use "Not quite yet", "Good guess, now let's
  learn it", "Focus here".
- Explain *why* in feedback; link weak areas to a concrete next action ("Review in Learn").

## Charts

Follow the dataviz method: thin columns (≤ 24 px) with a 4 px rounded top, hairline solid gridlines,
clean ticks, a single series (no legend; the title names it), hover/focus tooltips per column and a
**table view** toggle. The daily goal is one hairline reference line labelled in text colour.

## Why Ved works: UI pattern → principle

| UI pattern | Principle ([learning-science.md](../architecture/learning-science.md)) |
|---|---|
| Lesson opens with a **warm-up** guess ("guessing first helps you remember") | Pretesting / generation effect |
| **Quick checks** between sections; "Test my knowledge"; reviews are questions | Retrieval practice |
| **Explain it back** short answer at the end of each lesson; "Ask the tutor" | Elaboration & self-explanation |
| Concept / Example / Analogy sections, each visually distinct | Concrete examples & dual coding |
| Immediate verdict + "why" + personal feedback on every answer | Immediate, elaborative feedback |
| **Confidence picker** before submitting; gentle "confident mistakes" note; calibration grid in report | Hypercorrection & metacognition |
| One section / one question at a time; reading width ~70 ch; calm surfaces | Cognitive load theory |
| **Review cards** with "You'll see this again in 3 days"; Home shows "N due · ~2 min" | Spaced repetition |
| Assessment questions mixed across topics ("All topics, interleaved") | Interleaving |
| Mastery meter per topic; recommended node on the path; "Focus on topics that need practice" | Mastery learning |
| Home leads with **one Next best step**; quieter secondary cards below | Choice overload / defaults |
| Daily **goal ring**, progress bars on path, lessons and tests | Goal-gradient effect |
| Pipeline shows "Uploaded ✓" first; path shows completed topics | Endowed progress |
| "Continue where you left off"; tests resume at the next unanswered question | Zeigarnik effect |
| "I'll study **10 minutes** a day" goal picker at onboarding | Implementation intentions |
| Modest XP toasts; celebration only on real achievements | Habit loop without overjustification |
| Streak copy never mentions loss; inactive today still shows the streak | Loss aversion, used carefully |
| Report: celebrate → strengths → weak areas → next steps; growth-mindset copy | Peak-end rule, growth mindset |
| Choose mode, difficulty, topics, goal | Autonomy (self-determination theory) |
| Time estimates on actions | Lower perceived effort |
