# 0013. Lectures from audio and video: open speech-to-text, then AI study notes

- Status: Accepted
- Date: 2026-10-13

## Context

Learners want to study from recordings: their own lecture sessions, recorded courses. A transcript alone is a
poor study source (fillers, repetitions, tangents, no structure), so the expectation is study notes. Speech-to-text
must be open source, small, fast and accurate enough that the notes are right; it must run on the developer's Linux
laptop and on Macs (no GPU-only dependency), and use a GPU when one is present. Many learners speak Nepali or Hindi
alongside English.

Candidates (October 2026):

| Model | Notes |
|---|---|
| Vosk small | Very fast on CPU, but clearly less accurate and without punctuation: weak input for notes |
| Moonshine tiny / base | Fast, Whisper-level English accuracy at its size, but the open models are English-only (or one language each) |
| Cactus Whistle | 16.9 MB, released 2 Oct 2026; 7 European languages, 30 s windows, company-reported benchmarks only, new runtime |
| Whisper via faster-whisper (CTranslate2) | Multilingual (incl. Nepali, Hindi), punctuation, timestamps, built-in VAD; CPU wheels for Linux / macOS / Windows, CUDA when available; decodes any container via PyAV (no system FFmpeg) |

Measured here on a 50 s lecture clip (int8 on CPU): Whisper base 18x real time but 20% word errors ("database
indexing" heard as "Metamax Indexing"); Whisper small 7x real time on CPU, 38x on a laptop RTX 3050, 5.5% word
errors. Batched decoding gained little on CPU.

## Decision

- **faster-whisper with Whisper `small`** (int8 on CPU, int8_float16 on GPU; `device: auto`). Configurable model,
  device, beam size and language in `settings.yaml` (`speech.*`); the engine sits behind a `SpeechEngine` port, so
  Moonshine or Whistle can be added as engines when they fit.
- **Two steps, two endpoints.** `/v1/transcribe` (audio track only; video streams ignored; decoded in 10-minute
  windows cut at quiet moments, so memory stays flat) and `/v1/extract/transcript` (an LLM task, `lecture_notes`,
  writes faithful study notes window by window: topic sections with time ranges, key terms, steps, tables, formulas;
  fillers dropped; obvious recognition errors fixed only when context makes them certain). The backend stores the
  transcript after step one, so a busy AI only delays the notes and never repeats transcription.
- **Notes are the document.** They become the tree (sections, chunks, embeddings), so lessons, tests, recall, search
  and sharing work unchanged. The transcript is the raw text; the recording plays from a signed, seekable URL.
  "Page" means minute of the recording; the UI shows time ranges instead of pages.
- **Uploads only.** No fetching from YouTube or other sites (their terms forbid downloading); learners upload files
  they have. Media may be up to 1 GB (`ved.documents.max-media-bytes`), documents 25 MB.

## Consequences

- A one-hour lecture takes about 8-9 minutes to transcribe on an 8-core CPU, about 1.5 minutes on a small GPU, plus
  the notes. Long recordings use the separate `ved.inference.media-read-timeout` (3 h).
- The ~480 MB model downloads on the first recording; one transcription per inference process at a time
  (`speech.max_concurrency`) bounds memory (~1.5 GB).
- Notes are AI-written: they are only as faithful as the model and the prompt; the transcript stays available for
  checking. Slides referenced in speech ("as you see here") are noted but not seen.
- Videos are stored whole (for playback), which costs disk; extracting the audio track only would save space if needed.
