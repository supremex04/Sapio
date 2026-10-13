# Learning science: the principles behind Sapio's design

Sapio should feel effortless and pleasant, while quietly steering learners toward the study
habits research shows actually work. Each product decision below is tied to a well-replicated
finding. When a feature or design choice is debated, check it against this page.

> Ethos: **nudge, don't manipulate.** We use motivation science to help people do what they
> already want (learn), never to maximise screen time. No shame, no fake urgency, no punishing
> loss mechanics.

## 1. How memory works → what the learner does

| Principle | Evidence (short) | How Sapio applies it |
|---|---|---|
| **Retrieval practice** (testing effect) | Recalling information strengthens memory more than re-reading it (Roediger & Karpicke, 2006; Dunlosky et al., 2013 rate it "high utility"). | Comprehension checks inside every lesson; "Test my knowledge"; reviews are questions, never re-reading. |
| **Pretesting / generation effect** | Trying to answer *before* being taught improves later learning, even when the guess is wrong (Richland et al., 2009; Kornell et al., 2009). | Each lesson opens with a **warm-up question** (`afterSection: -1`): "Make a guess. Guessing first helps you remember, even if you're wrong." |
| **Spaced repetition** | Spreading reviews over growing intervals beats massed study (Cepeda et al., 2006). | Every answered question becomes a **review card** in a Leitner schedule (1 → 3 → 7 → 16 → 35 days; a miss returns to box 1, due again in 10 minutes). Home shows "N reviews due · ~2 min". |
| **Interleaving** | Mixing topics improves discrimination and transfer compared with practising one topic at a time (Rohrer & Taylor, 2007). | Assessment questions are interleaved round-robin across topics, not grouped. |
| **Elaboration & self-explanation** | Explaining *why*, in your own words, deepens understanding (Chi et al., 1994). | Each lesson ends with an "Explain it back" short-answer check graded by the AI; "Ask the tutor" for why-questions. |
| **Concrete examples & dual coding** | Abstract ideas stick better with concrete examples and complementary representations (Weinstein et al., 2018). | Lessons mix CONCEPT / EXAMPLE / ANALOGY sections, each visually distinct. |
| **Immediate, elaborative feedback** | Feedback that explains *why* beats right/wrong alone; it corrects misconceptions before they consolidate (Hattie & Timperley, 2007). | Lesson checks and recall show verdict, explanation and personal feedback grounded in the source as soon as the learner answers (a tap on an option in lessons). Tests hold all feedback for the report, so they measure without changing later answers, and the report explains every question. |
| **Hypercorrection & metacognition** | High-confidence errors, once corrected, are remembered especially well (Butterfield & Metcalfe, 2001). Rating confidence improves calibration. | Learners tap a **confidence** level (unsure / fairly sure / certain) before submitting. Confident misses are highlighted gently; the report shows calibration. |
| **Cognitive load theory** | Working memory is small; split information and remove distractions (Sweller, 1988). | One section or one question at a time, progressive reveal, compact structured section context (summaries + passages within a per-use budget, never the whole document), calm reading layouts. |
| **Mastery learning** | Advance when a concept is mastered, not after a fixed time (Bloom, 1968). | Per-topic **mastery** (recency-weighted accuracy) drives "recommended next" and weak-area advice. |

## 2. Why people keep going → how Sapio motivates

| Principle | Evidence (short) | How Sapio applies it |
|---|---|---|
| **Self-determination theory**: autonomy, competence, relatedness | Intrinsic motivation needs choice, a sense of growth and connection (Ryan & Deci, 2000). | Learner chooses mode, topics, difficulty (autonomy); mastery, levels and progress make growth visible (competence); classes/teachers come next (relatedness). |
| **Goal-gradient effect** | Effort increases as people near a goal (Kivetz et al., 2006). | Progress rings and bars everywhere: daily goal ring, topic path, question progress. |
| **Endowed progress** | People persist more when they start with some progress (Nunes & Drèze, 2006). | The study path shows "Uploaded · Study guide built" (checked) as completed steps; first lessons are short. |
| **Zeigarnik effect** | Unfinished tasks stay on our mind. | "Continue where you left off" is the first card on Home; in-progress assessments resume at the next question. |
| **Implementation intentions & small commitments** | A concrete plan ("10 minutes a day") strongly predicts follow-through (Gollwitzer, 1999). | A **daily goal** in minutes (5–30) the learner picks; tiny next steps with time estimates ("~2 min"). |
| **Habit formation** (cue → routine → reward) | Consistent small rewards after a routine build habits (Lally et al., 2010). | Streaks, XP and gentle celebrations attached to *learning actions*, not to opening the app. |
| **Loss aversion, used carefully** | Losses weigh more than gains, but fear-based mechanics cause anxiety and dropout. | Streaks are forgiving: today not yet active never shows as "broken"; no guilt messages. |
| **Overjustification risk** | Heavy extrinsic rewards can crowd out intrinsic interest (Deci et al., 1999). | XP is modest and secondary; the main feedback is mastery and understanding ("You can now explain…"). |
| **Adapting to the learner, not to "learning styles"** | Pitching explanations to prior knowledge matters (expertise reversal: what helps novices can hinder experts; Kalyuga, 2007), while matching teaching to visual / auditory / kinesthetic "styles" shows no benefit (Pashler et al., 2008). | Personalize sets level, depth, field and evidence-based preferences (examples, analogies, applications, formal treatment, code, step-by-step); no learning-styles option ([personalization.md](../specs/personalization.md)). |
| **Growth mindset feedback** | Praising effort and strategy beats praising ability (Mueller & Dweck, 1998). | Copy and AI feedback praise strategy ("Guessing first helped"), frame mistakes as part of learning, never "you're bad at…". |
| **Peak-end rule** | Experiences are remembered by their peak and their end (Kahneman et al., 1993). | Every session ends on a high point: topic completion celebration with takeaways; reports lead with strengths. |
| **Choice overload / defaults** | Too many options stall action (Iyengar & Lepper, 2000). | Home offers **one recommended next step** (reviews due → continue topic → next topic → test → upload) plus quiet secondary options. |

## 3. Design language implications

- **Calm by default, vivid on success.** Neutral reading surfaces; colour and motion are saved for
  progress and achievement, so they mean something.
- **Errors are amber/rose with encouraging copy**, never alarming red walls of text.
- **Motion is meaningful and short** (≤ 300 ms, eased) and respects `prefers-reduced-motion`.
- **Readable long-form text**: 65–75 character measure, generous line height, clear hierarchy.
- **Time estimates on actions** reduce perceived effort ("~3 min").

## 4. Measuring it

The progress module records the data needed to check these effects later: per-day active time,
answers with verdict and confidence, review outcomes and intervals, and mastery over time. The
roadmap item "learning effectiveness dashboard" uses it, e.g. retention on spaced reviews vs
first attempts.
