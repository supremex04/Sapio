# Ved design system ("Diya")

Status: **v2** (replaces the v1 gradient/glass look). Living reference: **`/styleguide`** in the running
app shows every token and component in both themes.

## Personality

**A friendly study buddy, not a dashboard.** Playful but never childish, encouraging but never pushy,
clear above all. People should *want* to come back (see [learning-science.md](../architecture/learning-science.md)).

| We are | We are not |
|---|---|
| Flat, bold, saturated colour with tactile depth | Gradients, glassmorphism, glows, neon |
| Chunky, pressable buttons and cards | Hairline borders, floating shadows |
| One rounded typeface (Nunito), heavy weights for hierarchy | Serif display + mixed fonts |
| A character (Dia) that reacts to what you do | Faceless spinners and generic empty states |
| Colour that *means* something (each hue has one job) | Decorative colour |

## Foundations

### Colour: one hue per job

Each hue has 4 steps: `soft` (tinted surface), `main` (fills), `edge` (the 3D lip under fills), `ink`
(text on soft). Defined for light and dark in `src/styles/tokens.css`.

| Hue | Job | Used for |
|---|---|---|
| **Blue** | primary action, Learn | primary buttons, selection, links, current path node, CONCEPT sections |
| **Green** | correct, done, growth | correct answers, completed states, success, EXAMPLE sections, daily goal |
| **Red** | incorrect (gently) | wrong-answer footer, destructive actions |
| **Amber** | XP, streak, energy | XP chips, streak, highlights, warm-up |
| **Purple** | mastery & levels | mastery rings, level badge, SUMMARY sections, Test mode |
| **Teal** | memory | spaced review |
| **Pink** | imagination | ANALOGY sections |
| Neutrals | structure | text (`ink`), surfaces, borders |

Rules: never put two saturated hues side by side as backgrounds; text on `main` fills is white, text
on `soft` surfaces uses that hue's `ink`. Contrast ≥ 4.5:1 for body text in both themes.

### Depth: the "edge"

Ved's signature. Interactive things sit on a solid lip (`--edge: 4px`) in a darker shade of their own
colour: `box-shadow: 0 var(--edge) 0 <edge colour>`. Pressing moves the element down by the edge
(`translateY(var(--edge))`) and removes the lip: a physical "click". Cards use a 2px border with a 4px
bottom lip. **No blurred drop shadows** except for floating layers (toasts, popovers, footer sheet).

### Typography

Nunito Variable (OFL), one family only. Hierarchy via weight and size:

| Token | Size / weight | Use |
|---|---|---|
| `--text-display` | 38 / 900 | celebration numbers, hero |
| `--text-h1` | 30 / 800 | page titles |
| `--text-h2` | 22 / 800 | section titles |
| `--text-h3` | 18 / 800 | card titles |
| `--text-body` | 16 / 600 (lesson text 17 / 500) | body |
| `--text-small` | 14 / 700 | secondary, buttons-sm |
| `--text-label` | 12 / 800, uppercase, +0.06em | eyebrows ("UNIT 1", "WARM-UP") |

### Shape, space, motion

- Radius: `--radius-sm 10`, `--radius-md 14`, `--radius-lg 18`, `--radius-xl 24`, pills `999`.
- Spacing: 4 px grid (`--space-1 … --space-16`).
- Motion: springy and short. `--ease-bounce` for rewards, `--ease-out` for everything else;
  ≤ 300 ms for UI, ≤ 800 ms for celebrations. Correct answers *pop*, wrong answers *shake gently*.
  Everything respects `prefers-reduced-motion` (no movement, instant states).

### Iconography

- **Lucide** (stroke 2.5) for **actions and controls**: close, back, upload, settings, chevrons.
- **Fluent Emoji 3D-style** (Microsoft, MIT; `shared/illustrations/emoji`) for **places, rewards and
  feelings**: navigation, XP, streak, trophies, section kinds, confidence. Always paired with text.
  Add new ones via `frontend/scripts/extract-emoji.mjs`.

### Characters

- **Dia**, the mascot: a little clay diya (lamp of knowledge) whose flame flickers. Moods: `happy`,
  `cheer`, `thinking`, `encourage`, `oops`, `sleepy`. Dia appears at moments that matter: onboarding,
  empty states, answer feedback, celebrations, loading. Never more than once per screen.
  Code: `shared/illustrations/Dia.tsx` (hand-made SVG, ours).
- **Avatars**: DiceBear *notionists* (CC0), seeded by user id: friendly, consistent, no uploads needed.

## Theming

- Light and dark are complete token sets: `:root` (light) and `:root[data-theme="dark"]`.
  Components only use semantic tokens (`--color-primary`, `--surface`, `--text`…), never raw values.
- Theme preference: `light | dark | system` (`features/theme`), applied before first paint by the
  inline script in `index.html`, persisted in localStorage.
- Dark mode is not inverted light: surfaces are deep blue-grey (`#131c26`), hues are lifted ~10% in
  lightness, edges are darker than fills, soft tints are low-saturation.

## Components (`src/shared/ui`)

| Component | Notes |
|---|---|
| `Button` / `ButtonLink` | variants `primary`(blue) `success`(green) `danger`(red) `accent`(amber) `secondary`(white, bordered) `ghost`; sizes `sm md lg`; 3D edge; `block` |
| `IconButton` | round, bordered, for toolbar actions |
| `Card` | 2px border + 4px lip; `interactive` lifts on hover and presses on click; `tone` tints |
| `StatTile` | emoji + big number + label (streak, XP, goal…) |
| `Chip` | small pill with optional emoji, for counts and filters |
| `Badge` | tiny status label (Ready, Weak, Strong…) |
| `ProgressBar` | thick rounded bar with a highlight stripe; colour by hue |
| `ProgressRing` | for goals and mastery |
| `OptionTile` | answer choice: key badge, selected/correct/incorrect states |
| `FeedbackSheet` | the slide-up footer after "Check": verdict colour, Dia mood, explanation, Continue |
| `SpeechBubble` | Dia's voice |
| `Tabs`, `Segmented`, `Toast`, `Skeleton`, `Spinner`, `EmptyState`, `ConfirmButton`, `Alert`, `FormField`, `Markdown` | as named |
| `Emoji` | `<Emoji name="fire" size={28} />` |
| `Avatar` | DiceBear notionists by seed |

App shell (`app/`): sidebar navigation on desktop (≥ 1024 px), right rail with streak / goal / reviews
on wide screens (≥ 1200 px), top stat bar + bottom tab bar on mobile.

## Signature patterns

- **Learning path**: a winding column of round 3D nodes (zig-zag), one per topic; colours by state
  (done = gold, recommended = blue with a bouncing "Start" bubble and Dia beside it, not started = grey);
  a mastery ring around each node; the path ends in a trophy node: *Test yourself*.
- **Question flow** (lesson checks, tests, reviews): one question at a time, big option tiles,
  confidence picker, sticky bottom bar with **Check**, which becomes the **FeedbackSheet**.
- **Celebration screens**: Dia cheering + three stat cards (XP, accuracy, time) + one primary button.

## Copy

Short, warm, second person. Praise strategy, not ability. Never shame. Errors say what to do next.

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
