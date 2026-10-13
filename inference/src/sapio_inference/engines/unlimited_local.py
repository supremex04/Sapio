"""Unlimited-OCR in-process via Hugging Face transformers (``trust_remote_code``).

Requires the ``local`` extra and an NVIDIA GPU with enough memory for a 3B BF16 model
(~8 GB+). The model's ``infer``/``infer_multi`` helpers write results to an output directory;
we read the result back from there.
"""

import tempfile
from pathlib import Path

from sapio_inference.config import Settings
from sapio_inference.documents.models import PageImage
from sapio_inference.engines.base import EngineOutput, OcrEngine, OcrSegment
from sapio_inference.engines.markdown import remove_detection_markup
from sapio_inference.errors import ConfigurationError, EngineError


class UnlimitedOcrLocalEngine(OcrEngine):
    name = "unlimited-ocr-local"

    def __init__(self, settings: Settings) -> None:
        try:
            import torch
            from transformers import AutoModel, AutoTokenizer
        except ImportError as exc:  # pragma: no cover - depends on installed extras
            raise ConfigurationError("Local engine needs: pip install -e '.[local]'") from exc
        self._torch = torch
        config = settings.ocr.local
        self._batch_size = config.pages_per_request
        self._tokenizer = AutoTokenizer.from_pretrained(config.model_id, trust_remote_code=True)
        model = AutoModel.from_pretrained(
            config.model_id,
            trust_remote_code=True,
            use_safetensors=True,
            torch_dtype=torch.bfloat16,
        )
        self._model = model.eval().to(config.device)

    def extract(self, pages: list[PageImage]) -> EngineOutput:
        batches = [pages[i : i + self._batch_size] for i in range(0, len(pages), self._batch_size)]
        return EngineOutput(
            segments=[
                OcrSegment(page_from=batch[0].number, page_to=batch[-1].number, markdown=self._run(batch))
                for batch in batches
            ]
        )

    def _run(self, pages: list[PageImage]) -> str:
        with tempfile.TemporaryDirectory(prefix="sapio_ocr_") as tmp:
            tmp_dir = Path(tmp)
            files = []
            for page in pages:
                path = tmp_dir / f"page_{page.number:04d}.png"
                page.image.save(path)
                files.append(str(path))
            out_dir = tmp_dir / "out"
            out_dir.mkdir()
            with self._torch.inference_mode():
                if len(files) == 1:
                    returned = self._model.infer(
                        self._tokenizer,
                        prompt="<image>document parsing.",
                        image_file=files[0],
                        output_path=str(out_dir),
                        base_size=1024,
                        image_size=640,
                        crop_mode=True,
                        max_length=32768,
                        no_repeat_ngram_size=35,
                        ngram_window=128,
                        save_results=True,
                    )
                else:
                    returned = self._model.infer_multi(
                        self._tokenizer,
                        prompt="<image>Multi page parsing.",
                        image_files=files,
                        output_path=str(out_dir),
                        image_size=1024,
                        max_length=32768,
                        no_repeat_ngram_size=35,
                        ngram_window=1024,
                        save_results=True,
                    )
            raw = returned if isinstance(returned, str) and returned.strip() else _read_saved_result(out_dir)
        return remove_detection_markup(raw)


def _read_saved_result(out_dir: Path) -> str:
    for pattern in ("*.mmd", "*.md", "*.txt"):
        found = sorted(out_dir.rglob(pattern))
        if found:
            return found[0].read_text(encoding="utf-8")
    raise EngineError("Local Unlimited-OCR produced no output")
