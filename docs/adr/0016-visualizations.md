# 0016. Visualizations: AI-written Manim scenes rendered in a separate, locked-down service

- Status: Accepted
- Date: 2026-10-16

## Context

Learners meet formulas and ideas (Pythagoras, derivatives, harmonic motion, Fourier series) that are easier to
grasp when seen moving. We want a learner to type a formula or describe an idea and get a short animated video of
it, in the style of 3Blue1Brown. Manim (Community Edition) draws such animations from Python code, and an LLM can
write that code. Two problems: the code is written by an AI from user input, so it is untrusted; and rendering
needs heavy native dependencies (Cairo, Pango, FFmpeg, LaTeX) and minutes of CPU.

## Decision

- **Three steps, two services.** Inference plans a **storyboard** (title, kind, up to 8 beats of what is shown
  and what is said, or `feasible: false` with a reason) and then writes the **scene code** from it
  (`/v1/visualize/storyboard`, `/v1/visualize/code`, contract 1.4). A new **renderer** service (`renderer/`)
  runs the code with Manim and returns an MP4 and a thumbnail. It has no AI and no secrets, so a scene that
  escapes the checks finds nothing worth taking.
- **The backend orchestrates** (`com.sapio.visualization`), on its own small worker pool: plan, code, render.
  When a render fails because of the code (rejected, Manim raised, too long, empty), the AI gets its code back
  with the renderer's error and repairs it, up to `max-attempts` renders (3). A renderer that is down is not
  blamed on the code. Videos are stored like figures and served with signed, expiring URLs.
- **Defence in depth for untrusted code** in the renderer: an AST allowlist (only `manim`, `math`, `numpy`,
  `random` and a few pure modules; no dunder access, `eval`, `open`, `getattr`, ...; exactly one
  `Visualization(Scene)` class), then a child process with an empty environment, CPU, memory, file-size and
  process limits, and a wall-clock timeout; one render at a time per instance; a non-root user in a container.
  In the cloud the renderer runs as a service account that can read only its own key, with egress only into the
  VPC (no Cloud NAT, so no internet).
- **Cost control**: the same request (case and spacing aside) reuses the finished or in-progress visualization;
  each learner may start `daily-limit` (15) a day; videos are capped at 90 s and rendered at 720p30. The renderer
  scales to zero.

## Consequences

- A visualization costs two to four LLM calls and one to three renders (about a minute of 2 vCPU), roughly
  $0.003 in Cloud Run compute; idle cost is zero. The first render after a quiet spell waits for a cold start.
- The static check is not a sandbox on its own; the container and its missing network and secrets are what make
  an escape harmless. A stronger sandbox (gVisor is already Cloud Run gen2's) or a per-render job can come later.
- Not yet: a "Visualize this" action in the reading view (the API already takes `documentId` and `context`),
  narration audio, sharing visualizations to Explore, choosing quality.
