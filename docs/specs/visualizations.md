# Visualizations: formulas and ideas as short animated videos

A learner types a formula (plain or LaTeX) or describes an idea, and Sapio makes a short animated video of it with
Manim. Design decisions: [ADR-0016](../adr/0016-visualizations.md). Backend: `com.sapio.visualization`;
inference: `sapio_inference.visualize`; renderer: `renderer/`; frontend: `features/visualize` (`/visualize`).

## How one is made

1. **Storyboard** (inference, task `visualize_plan`): a title, a kind (`math`, `physics`, ...) and 2-8 beats, each
   `{shows, says}`: what appears on screen and the caption, which is both spoken and shown as a subtitle (so it is
   written in plain spoken words: "a squared plus b squared"). Written for the learner's Personalize profile.
   A request that cannot be shown as an animation comes back `feasible: false` with a reason, and the
   visualization fails with that reason without rendering anything.
2. **Code** (task `visualize_code`): one `Visualization(Scene)` class in Manim Community 0.19, each beat wrapped in
   `with narrate(self, n) as seconds:` and paced to the length of its spoken line.
3. **Render** (renderer `POST /v1/render`): the renderer speaks each caption (Kokoro-82M), then runs the checked
   scene with limits; `narrate` starts a beat's line and waits until it is finished, so voice and picture stay in
   step. Returns the MP4 (720p30, AAC narration, at most 90 s) and a thumbnail (a frame near the end).
4. **Repair**: if the render fails because of the code, the AI receives its code and the renderer's error and
   writes a fix; up to `sapio.visualization.max-attempts` renders (default 3). If the renderer is unreachable the
   visualization fails at once with "unavailable", and can be retried.

Status: `PLANNING` -> `CODING` -> `RENDERING` -> `READY` | `FAILED`. `attempts` counts renders tried. Work
interrupted by a restart is resumed at startup.

## Engines ([ADR-0017](../adr/0017-visualization-engines.md))

| Engine | Shown as | Built by | The beat's `focus` |
|---|---|---|---|
| `ANIMATION` | narrated video | the AI's Manim scene, rendered | (none) |
| `MOLECULE` | 3D model (3Dmol.js), 2D drawing, formula and mass | RDKit from SMILES | a named group (`group:methyl`, see `renderer/src/sapio_renderer/groups.py`) or a SMARTS of the atoms to highlight |
| `PROTEIN` | 3D cartoon (3Dmol.js) | RCSB Protein Data Bank entry (title, mmCIF, picture), stored | `A:58-63`, `A`, `ligand:HEM` |
| `CODE` | listing, running line, call stack with variables, output | the renderer's tracer (Python) | line range, `3-5` |

**Routing**, cheapest first and never a separate AI call: the learner's choice (`engine` on create: `AUTO`
default); rules for obvious requests (pasted code; a single SMILES token, once RDKit reads it; a PDB ID on its own
or next to words like "protein"); otherwise the storyboard picks. The learner's own program, SMILES or PDB ID is
tried before the AI's. When the chosen data does not work: under `AUTO`, the AI's own data is tried next; when the
learner chose the engine, it fails with the reason.

The non-video engines write no code: one storyboard call, then the build (milliseconds for a molecule, a second for
a trace, a download for a protein), then one narration track (`POST /v1/narrate`) with each beat's start, which the
viewer follows. A program in another language is translated to Python by the storyboard and said so in the first
beat. Browsers without WebGL see, per beat, RDKit's 2D drawing with its atoms highlighted (molecules) or the PDB's
picture with the focus named (proteins). Narration waits for a click when the browser blocks sound.

## Limits

- The same request (words ignoring case and spacing; code, SMILES and IDs exactly; plus the same context and
  engine choice) returns the existing visualization if it is ready or still being made; a failed one is not reused.
- `sapio.visualization.daily-limit` new visualizations per learner per 24 hours (default 15; env
  `SAPIO_VISUALIZATIONS_PER_DAY`); over it: `429 RATE_LIMITED`. A retry does not count.
- Request up to 4000 characters; `context` is trimmed to 6000.

## API (`/api/visualizations`, the learner's own only; others answer 404)

| Method | Path | Body / result |
|---|---|---|
| `POST` | `/api/visualizations` | `{request (at most 4000), engine? (AUTO\|ANIMATION\|MOLECULE\|PROTEIN\|CODE), documentId?, context?}` -> visualization (`documentId` must be the learner's own) |
| `GET` | `/api/visualizations` | the latest 100, newest first |
| `GET` | `/api/visualizations/{id}` | visualization |
| `POST` | `/api/visualizations/{id}/retry` | only when `FAILED`: starts again from the storyboard |
| `DELETE` | `/api/visualizations/{id}` | `204`; the video and thumbnail are deleted too |
| `GET` | `/api/visualizations/{id}/video?exp&sig` | MP4, no login (signed URL); Range requests for seeking |
| `GET` | `/api/visualizations/{id}/thumbnail?exp&sig` | PNG (a protein's: JPEG), no login (signed URL) |
| `GET` | `/api/visualizations/{id}/audio?exp&sig` | narration track (AAC in MP4) of a molecule, protein or program |
| `GET` | `/api/visualizations/{id}/structure?exp&sig` | a protein's structure (mmCIF text) |

A visualization: `{id, request, documentId, status, engineChoice, engine, title, kind, beats:[{shows, says,
focus}], code, attempts, error (when FAILED), durationSeconds, narrated, videoUrl, thumbnailUrl, audioUrl,
structureUrl (signed, when READY), scene (single visualization only), createdAt, completedAt}`. `scene` by engine:
molecule `{name, smiles, formula, weight, heavyAtoms, threeD, molBlock, highlights[[atom]], drawings[png], timing}`;
protein `{pdbId, name, entryTitle, timing}`; code `{program, trace:{steps:[{line, event, stack:[{name, line,
vars}], output, value?}], output, error, truncated}, timing}`; `timing: [{start, seconds}]` per beat.

The player has its own controls (play, time, seek, sound, full screen); narrated videos start with sound when the
browser allows autoplay with sound (after a click in the app), otherwise muted with the sound button showing it.

## Renderer (`renderer/`, port 8100)

`POST /v1/render {code, quality: preview|final, narration: [line per beat] (at most 8, 400 characters each)}` with
header `X-Internal-Api-Key` (env `SAPIO_RENDERER_API_KEY`) -> `{videoBase64, thumbnailBase64, durationSeconds,
narrated}`, or `422 {kind, message}` with kind `code` (rejected by the static check), `error` (Manim raised; the end
of the traceback), `timeout` or `output` (no video, empty, or too long). `GET /health` -> `{status, engine, speech}`.

**Narration.** Kokoro-82M (int8 ONNX, about 120 MB with its voices, baked into the image by
`scripts/fetch_models.py` and checked by SHA-256) runs in the renderer's own process, before the scene, at roughly
twice real time on a CPU; clips are peak-normalised, end with a short pause and are cached by text. Voice
`af_heart` (env `SAPIO_RENDERER_TTS_VOICE`), US English. Scene code may import exactly
`from sapio_narration import narrate`; each line plays once and in order (a wrong or repeated beat number takes
the next unspoken line). Without the model, or if speech fails, the video renders silently (`narrated: false`).
`durationSeconds` is the picture's length, and any sound past the last frame is cut.

The static check allows imports of `manim`, `math`, `numpy`, `random`, `typing`, `itertools`, `functools`,
`colorsys` and `narrate` from `sapio_narration` only, and refuses dunder attributes, strings containing `__`, `global`/`nonlocal`, and names such as
`eval`, `exec`, `open`, `getattr`, `os`, `sys`, `subprocess`. The render runs in a child process with an empty
environment, limits on CPU time (180 s), memory (3 GiB), file size (300 MiB) and wall-clock time (150 s), one at
a time per instance, as a non-root user.

Locally `./dev.sh` builds the image `sapio-renderer:dev` (rebuilt when `renderer/` changes) and runs it with
Docker; nothing is installed on the host. In Google Cloud it is the `sapio-renderer` Cloud Run service
(`deploy/gcp/deploy.sh`): 2 vCPU, 2 GiB, one request per instance, scales to zero, egress only into the VPC.

**Molecules and traces.** `POST /v1/molecule {smiles (at most 500), highlights:[SMARTS or group:name], captions:[narration]}` -> `{canonicalSmiles,
formula, weight, heavyAtoms, molBlock, threeD, pngBase64, highlights:[[atom]], drawingsBase64:[png]}` (at most 120
heavy atoms; 422 kind `input` when RDKit cannot read it). A focus that lights up nothing or more than 60% of the
heavy atoms gives way to a group the beat's caption names ("three methyl groups"); a SMARTS matching nothing is then
retried by element and connectivity. Highlights include the group's hydrogens (indices in `molBlock`). `POST /v1/trace {code (at most 8000), stdin:[line]}` -> `{steps, output, error, truncated}`: checked
like scenes but for teaching Python (`collections`, `heapq`, `dataclasses` and the like; dunders such as `__init__`;
`input()` reads `stdin`), run in a child process with 8 s of CPU, 512 MiB and a 400-step cap. `POST /v1/narrate
{lines}` -> `{audioBase64 (AAC in MP4, or null without speech), beats:[{start, seconds}]}`.

## Configuration (`sapio.visualization` in `application.yml`)

`renderer-url` (`SAPIO_RENDERER_URL`), `renderer-api-key` (`SAPIO_RENDERER_API_KEY`), `render-timeout` (4m),
`max-attempts` (3), `daily-limit` (15), `url-secret` (`SAPIO_VISUALIZATION_URL_SECRET`, must be set outside
local development), `url-ttl` (6h), `protein-bank.*` (RCSB URLs, `max-structure-bytes` 15 MiB).
