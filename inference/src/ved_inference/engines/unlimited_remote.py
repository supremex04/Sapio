"""Unlimited-OCR served by an OpenAI-compatible server (SGLang or vLLM), typically on a GPU host.

See https://huggingface.co/baidu/Unlimited-OCR for server setup. Single images use the "gundam"
mode with prompt ``document parsing.``; multi-page input uses "base" mode with ``Multi page parsing.``
"""

import base64
import io

import httpx

from ved_inference.config import Settings
from ved_inference.documents.models import PageImage
from ved_inference.engines.base import EngineOutput, OcrEngine, OcrSegment
from ved_inference.engines.markdown import remove_detection_markup
from ved_inference.errors import EngineError

SINGLE_PAGE_PROMPT = "document parsing."
MULTI_PAGE_PROMPT = "Multi page parsing."


class UnlimitedOcrRemoteEngine(OcrEngine):
    name = "unlimited-ocr"

    def __init__(self, settings: Settings, transport: httpx.BaseTransport | None = None) -> None:
        headers = {}
        if key := settings.remote_api_key.get_secret_value():
            headers["Authorization"] = f"Bearer {key}"
        self._client = httpx.Client(
            base_url=settings.remote_base_url.rstrip("/"),
            headers=headers,
            timeout=httpx.Timeout(settings.remote_timeout_seconds, connect=10.0),
            transport=transport,
        )
        self._model = settings.remote_model
        self._flavor = settings.remote_flavor
        self._batch_size = settings.remote_pages_per_request
        self._extra_body = settings.remote_extra_body

    def extract(self, pages: list[PageImage]) -> EngineOutput:
        batches = [pages[i : i + self._batch_size] for i in range(0, len(pages), self._batch_size)]
        return EngineOutput(
            segments=[
                OcrSegment(page_from=batch[0].number, page_to=batch[-1].number, markdown=self._run(batch))
                for batch in batches
            ]
        )

    def close(self) -> None:
        self._client.close()

    def _run(self, pages: list[PageImage]) -> str:
        single = len(pages) == 1
        content = [{"type": "text", "text": SINGLE_PAGE_PROMPT if single else MULTI_PAGE_PROMPT}]
        content += [{"type": "image_url", "image_url": {"url": _data_url(p)}} for p in pages]
        body: dict = {
            "model": self._model,
            "messages": [{"role": "user", "content": content}],
            "temperature": 0,
            "skip_special_tokens": False,
            "stream": False,
        }
        if self._flavor == "sglang":
            body["images_config"] = {"image_mode": "gundam" if single else "base"}
        body.update(self._extra_body)
        try:
            response = self._client.post("/chat/completions", json=body)
            response.raise_for_status()
            raw = response.json()["choices"][0]["message"]["content"] or ""
        except httpx.HTTPStatusError as exc:
            raise EngineError(f"OCR model server returned HTTP {exc.response.status_code}") from exc
        except (httpx.HTTPError, KeyError, IndexError, ValueError) as exc:
            raise EngineError("OCR model server unavailable or returned an invalid response") from exc
        return remove_detection_markup(raw)


def _data_url(page: PageImage) -> str:
    buffer = io.BytesIO()
    page.image.save(buffer, format="PNG")
    return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode("ascii")
