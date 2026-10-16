# Visualizations: formulas and ideas as short animated videos

A learner types a formula (plain or LaTeX) or describes an idea, and Sapio makes a short animated video of it with
Manim. Design decisions: [ADR-0016](../adr/0016-visualizations.md). Backend: `com.sapio.visualization`;
inference: `sapio_inference.visualize`; renderer: `renderer/`; frontend: `features/visualize` (`/visualize`).

## How one is made

1. **Storyboard** (inference, task `visualize_plan`): a title, a kind (`math`, `physics`, ...) and 2-8 beats, each
   `{shows, says}`: what appears on screen and the caption under it. Written for the learner's Personalize profile.
   A request that cannot be shown as an animation comes back `feasible: false` with a reason, and the
   visualization fails with that reason without rendering anything.
2. **Code** (task `visualize_code`): one `Visualization(Scene)` class in Manim Community 0.19.
3. **Render** (renderer `POST /v1/render`): checked, then run with limits; returns the MP4 (720p30, at most 90 s)
   and a thumbnail (a frame near the end).
4. **Repair**: if the render fails because of the code, the AI receives its code and the renderer's error and
   writes a fix; up to `sapio.visualization.max-attempts` renders (default 3). If the renderer is unreachable the
   visualization fails at once with "unavailable", and can be retried.

Status: `PLANNING` -> `CODING` -> `RENDERING` -> `READY` | `FAILED`. `attempts` counts renders tried. Work
interrupted by a restart is resumed at startup.

## Limits

- The same request (ignoring case and spacing, plus the same context) returns the existing visualization if it is
  ready or still being made; a failed one is not reused.
- `sapio.visualization.daily-limit` new visualizations per learner per 24 hours (default 15; env
  `SAPIO_VISUALIZATIONS_PER_DAY`); over it: `429 RATE_LIMITED`. A retry does not count.
- Request up to 2000 characters; `context` is trimmed to 6000.

## API (`/api/visualizations`, the learner's own only; others answer 404)

| Method | Path | Body / result |
|---|---|---|
| `POST` | `/api/visualizations` | `{request, documentId?, context?}` -> visualization (`documentId` must be the learner's own) |
| `GET` | `/api/visualizations` | the latest 100, newest first |
| `GET` | `/api/visualizations/{id}` | visualization |
| `POST` | `/api/visualizations/{id}/retry` | only when `FAILED`: starts again from the storyboard |
| `DELETE` | `/api/visualizations/{id}` | `204`; the video and thumbnail are deleted too |
| `GET` | `/api/visualizations/{id}/video?exp&sig` | MP4, no login (signed URL); Range requests for seeking |
| `GET` | `/api/visualizations/{id}/thumbnail?exp&sig` | PNG, no login (signed URL) |

A visualization: `{id, request, documentId, status, title, kind, beats:[{shows, says}], code, attempts, error
(when FAILED), durationSeconds, videoUrl, thumbnailUrl (signed, when READY), createdAt, completedAt}`.

## Renderer (`renderer/`, port 8100)

`POST /v1/render {code, quality: preview|final}` with header `X-Internal-Api-Key` (env `SAPIO_RENDERER_API_KEY`)
-> `{videoBase64, thumbnailBase64, durationSeconds}`, or `422 {kind, message}` with kind `code` (rejected by the
static check), `error` (Manim raised; the end of the traceback), `timeout` or `output` (no video, empty, or too
long). `GET /health` -> `{status, engine}`.

The static check allows imports of `manim`, `math`, `numpy`, `random`, `typing`, `itertools`, `functools` and
`colorsys` only, and refuses dunder attributes, strings containing `__`, `global`/`nonlocal`, and names such as
`eval`, `exec`, `open`, `getattr`, `os`, `sys`, `subprocess`. The render runs in a child process with an empty
environment, limits on CPU time (180 s), memory (3 GiB), file size (300 MiB) and wall-clock time (150 s), one at
a time per instance, as a non-root user.

Locally `./dev.sh` builds the image `sapio-renderer:dev` (rebuilt when `renderer/` changes) and runs it with
Docker; nothing is installed on the host. In Google Cloud it is the `sapio-renderer` Cloud Run service
(`deploy/gcp/deploy.sh`): 2 vCPU, 2 GiB, one request per instance, scales to zero, egress only into the VPC.

## Configuration (`sapio.visualization` in `application.yml`)

`renderer-url` (`SAPIO_RENDERER_URL`), `renderer-api-key` (`SAPIO_RENDERER_API_KEY`), `render-timeout` (4m),
`max-attempts` (3), `daily-limit` (15), `url-secret` (`SAPIO_VISUALIZATION_URL_SECRET`, must be set outside
local development), `url-ttl` (6h).
