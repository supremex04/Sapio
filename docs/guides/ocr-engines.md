# OCR engines

Extraction is **hybrid**: PDF pages with a text layer are read natively (PyMuPDF/pymupdf4llm,
with headings and tables kept as Markdown) and never OCR'd. The OCR engine below only handles scanned
pages and images.

Pick the engine with `VED_INFERENCE_ENGINE` (in `inference/.env` or the environment). Restart the service after changing it.

| Engine | Quality | Hardware | Install |
|---|---|---|---|
| `rapid` (default) | plain text, no layout | any CPU | `pip install -e '.[rapid]'` |
| `unlimited-remote` | full Markdown: headings, tables, formulas, multi-page | GPU host running SGLang / vLLM | nothing extra here |
| `unlimited-local` | same as remote | local NVIDIA GPU, ~8 GB+ VRAM | `pip install -e '.[local]'` (~3 GB of packages + ~7 GB model download) |

> The dev laptop's RTX 3050 has 4 GB of VRAM. That is not enough for Unlimited-OCR (3B params,
> BF16), so use `rapid` locally and `unlimited-remote` against a GPU server.

## Unlimited-OCR via SGLang (recommended)

The model card's tested setup. On a GPU host (CUDA 12.9+, e.g. a cloud A10/L4/A100):

```bash
python -m sglang.launch_server \
  --model baidu/Unlimited-OCR --served-model-name Unlimited-OCR \
  --attention-backend fa3 --page-size 1 --mem-fraction-static 0.8 \
  --context-length 32768 --enable-custom-logit-processor --disable-overlap-schedule \
  --skip-server-warmup --host 0.0.0.0 --port 10000
```

(The model card says to install a specific SGLang wheel. Follow it: https://huggingface.co/baidu/Unlimited-OCR)

Then in `inference/.env`:

```env
VED_INFERENCE_ENGINE=unlimited-remote
VED_INFERENCE_REMOTE_BASE_URL=http://<gpu-host>:10000/v1
VED_INFERENCE_REMOTE_FLAVOR=sglang
VED_INFERENCE_REMOTE_MODEL=Unlimited-OCR
```

## Unlimited-OCR via vLLM

Use the `vllm/vllm-openai:unlimited-ocr` image and the recipe at https://recipes.vllm.ai/baidu/Unlimited-OCR.
Set `VED_INFERENCE_REMOTE_FLAVOR=vllm` (drops SGLang-only request fields). Pass any
server-specific request fields with `VED_INFERENCE_REMOTE_EXTRA_BODY='{"key": "value"}'`.

## How the remote engine calls the model

- One page → prompt `document parsing.`, SGLang `image_mode: gundam`.
- Several pages → prompt `Multi page parsing.`, `image_mode: base`. PDFs are sent in batches of
  `VED_INFERENCE_REMOTE_PAGES_PER_REQUEST` (default 8) pages to stay within the 32k-token context;
  batch outputs are joined with `---`.
- `temperature: 0`, `skip_special_tokens: false`. Layout markers (`<|det|>…<|/det|>`) are stripped
  by `engines/markdown.py`.
- Not yet used: SGLang's `DeepseekOCRNoRepeatNGramLogitProcessor`. It needs the `sglang`
  package on the client side to serialise. If you see repetition loops on long pages, add it
  through `REMOTE_EXTRA_BODY`.

## Adding a new engine

1. Create `inference/src/ved_inference/engines/<name>.py` with a subclass of `OcrEngine`
   (`name`, `extract(pages) -> EngineOutput`, optional `close()`). Import heavy dependencies inside the module.
2. Register a factory in `engines/registry.py` and add the name to `EngineName` in `config.py`.
3. Add any optional dependencies as a new extra in `pyproject.toml`.
4. Add tests (mock the model/HTTP; see `tests/test_unlimited_remote.py`).
5. Update this guide.
