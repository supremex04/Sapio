# 0003. OCR engines behind one interface

- Status: Accepted
- Date: 2026-10-09

## Context

The target model is **baidu/Unlimited-OCR** (3B parameters, BF16, released June 2026; MIT
license). It parses whole multi-page documents into Markdown in one pass. It needs a CUDA GPU with
roughly 8 GB+ VRAM. The development laptop has an RTX 3050 with 4 GB, so the model can't run
locally. We still want the whole product to work end-to-end on any machine.

## Decision

The inference service defines `OcrEngine.extract(pages) -> Markdown` and picks an implementation
from `SAPIO_INFERENCE_ENGINE`:

| Engine | Use |
|---|---|
| `unlimited-remote` | **Production target.** Calls Unlimited-OCR served by SGLang or vLLM (OpenAI-compatible) on a GPU host. |
| `unlimited-local` | Unlimited-OCR in-process via transformers, for a machine with a big enough GPU. |
| `rapid` | RapidOCR (ONNX, CPU). Plain-text quality; the default for development and CI. |

Document decoding (PDF → images at 200 DPI) is shared, so engines only deal with page images.

## Consequences

- Swapping or A/B-testing models is a config change; the backend and UI are unaffected.
- The model server can scale independently on GPU hardware.
- Output quality depends on the engine; the `engine` field is stored per document so results are traceable.
- `unlimited-local` is written to the model card's API but has not been run on suitable hardware yet.

> **Note (2026-10-10):** the engine is now selected with `ocr.engine` in `inference/settings.yaml`
> (env override `SAPIO_INFERENCE_OCR__ENGINE`); the `SAPIO_INFERENCE_ENGINE`, `SAPIO_INFERENCE_REMOTE_*`,
> `SAPIO_INFERENCE_LOCAL_*` and `PDF_RENDER_DPI` settings became `ocr.*` and `parsing.dpi`. See
> [ADR-0010](0010-vendor-neutral-model-configuration.md) and [configuration.md](../guides/configuration.md).
