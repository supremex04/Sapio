"""In-process embedding models (ONNX Runtime on CPU): no API calls, nothing leaves the machine.

Models are downloaded once from the Hugging Face Hub (``embeddings.model`` is the repo id) and cached. Each model
family needs its own recipe (which file, how token vectors are pooled, what prefix queries get), configured in
``embeddings.options`` and validated by :class:`OnnxEmbeddingOptions`. Only embeddings are supported; this
provider cannot generate text.
"""

import logging
import threading
from pathlib import Path
from typing import Literal

import numpy as np
from pydantic import BaseModel, Field

from sapio_inference.config import ModelRef, ProviderConfig
from sapio_inference.errors import ConfigurationError
from sapio_inference.llm.base import EmbedItem, EmbedTask, ImagePart, T
from sapio_inference.llm.providers.base import Provider

log = logging.getLogger(__name__)


class OnnxEmbeddingOptions(BaseModel):
    #: ONNX file inside the repo (quantised variants are smaller and faster, e.g. "onnx/model_int8.onnx").
    file: str = "onnx/model.onnx"
    #: "last" = last real token (decoder models such as Qwen3-Embedding), "mean" = average (BERT-style), "cls".
    pooling: Literal["last", "mean", "cls"] = "mean"
    #: Prepended to queries / documents (instruction-tuned models need them; see the model card).
    query_prefix: str = ""
    document_prefix: str = ""
    max_tokens: int = Field(default=512, ge=16)
    #: Texts per forward pass (CPU memory grows with batch × length).
    batch_size: int = Field(default=8, ge=1)
    threads: int = Field(default=0, ge=0)  # 0 = ONNX Runtime default (all cores)


class OnnxEmbeddingProvider(Provider):
    supports_image_embeddings = False  # text models: figures are embedded from caption + description

    def __init__(self, name: str, config: ProviderConfig) -> None:
        self.name = name
        self._config = config
        self._sessions: dict[tuple[str, str], tuple[object, object, list[str]]] = {}
        self._lock = threading.Lock()

    def generate(
        self,
        model: ModelRef,
        *,
        system: str,
        prompt: str,
        schema: type[T],
        temperature: float,
        images: list[ImagePart],
    ) -> T:
        raise ConfigurationError(
            f"Provider '{self.name}' (onnx) only computes embeddings; route text generation elsewhere"
        )

    def embed(
        self,
        model: str,
        dimensions: int,
        items: list[EmbedItem],
        task: EmbedTask,
        options: dict | None = None,
    ) -> list[list[float]]:
        opts = OnnxEmbeddingOptions(**(options or {}))
        session, tokenizer, inputs = self._load(model, opts)
        prefix = opts.query_prefix if task == "RETRIEVAL_QUERY" else opts.document_prefix
        texts = [prefix + (i.text or "") for i in items]
        # Similar lengths together = less padding = faster.
        order = sorted(range(len(texts)), key=lambda i: len(texts[i]))
        out: list[list[float] | None] = [None] * len(texts)
        for start in range(0, len(order), opts.batch_size):
            idx = order[start : start + opts.batch_size]
            vectors = self._forward(session, tokenizer, inputs, [texts[i] for i in idx], opts, dimensions)
            for i, v in zip(idx, vectors, strict=True):
                out[i] = v
        return [v for v in out if v is not None]

    # ------------------------------------------------------------------------------------------------ internals

    def _load(self, model: str, opts: OnnxEmbeddingOptions):
        key = (model, opts.file)
        with self._lock:
            if key not in self._sessions:
                self._sessions[key] = self._open(model, opts)
            return self._sessions[key]

    def _open(self, model: str, opts: OnnxEmbeddingOptions):
        try:
            import onnxruntime as ort
            from huggingface_hub import hf_hub_download
            from tokenizers import Tokenizer
        except ImportError as exc:  # pragma: no cover - depends on installed extras
            raise ConfigurationError(
                "Local embeddings need the 'embed' extra: pip install -e '.[embed]'"
            ) from exc
        log.info("Loading embedding model %s (%s)", model, opts.file)
        model_path = Path(hf_hub_download(model, opts.file))
        tokenizer = Tokenizer.from_file(hf_hub_download(model, "tokenizer.json"))
        tokenizer.enable_truncation(max_length=opts.max_tokens)
        pad_id = tokenizer.token_to_id("<|endoftext|>") or tokenizer.token_to_id("[PAD]") or 0
        tokenizer.enable_padding(pad_id=pad_id, direction="right")
        so = ort.SessionOptions()
        if opts.threads:
            so.intra_op_num_threads = opts.threads
        session = ort.InferenceSession(str(model_path), so, providers=["CPUExecutionProvider"])
        inputs = session.get_inputs()
        log.info("Embedding model ready (%d inputs)", len(inputs))
        return session, tokenizer, inputs

    @staticmethod
    def _forward(
        session, tokenizer, inputs: list, texts: list[str], opts, dimensions: int
    ) -> list[list[float]]:
        encodings = tokenizer.encode_batch(texts)
        ids = np.array([e.ids for e in encodings], dtype=np.int64)
        mask = np.array([e.attention_mask for e in encodings], dtype=np.int64)
        feed: dict[str, np.ndarray] = {}
        for node in inputs:
            if node.name == "input_ids":
                feed[node.name] = ids
            elif node.name == "attention_mask":
                feed[node.name] = mask
            elif node.name == "token_type_ids":
                feed[node.name] = np.zeros_like(ids)
            elif node.name == "position_ids":
                feed[node.name] = np.clip(np.cumsum(mask, axis=1) - 1, 0, None)
            elif node.name.startswith("past_key_values"):
                # Decoder exports also serve generation; for a single forward pass the cache is empty.
                heads, head_dim = node.shape[1], node.shape[3]
                dtype = np.float16 if "float16" in node.type else np.float32
                feed[node.name] = np.zeros((len(texts), heads, 0, head_dim), dtype=dtype)
        hidden = session.run([session.get_outputs()[0].name], feed)[0]  # (batch, tokens, dim)

        if opts.pooling == "last":
            last = mask.sum(axis=1) - 1  # right padding: last real token
            pooled = hidden[np.arange(len(texts)), last]
        elif opts.pooling == "cls":
            pooled = hidden[:, 0]
        else:
            weights = mask[..., None].astype(hidden.dtype)
            pooled = (hidden * weights).sum(axis=1) / np.clip(weights.sum(axis=1), 1e-9, None)

        if pooled.shape[1] < dimensions:
            raise ConfigurationError(
                f"Model outputs {pooled.shape[1]} dimensions; {dimensions} were configured"
            )
        # Matryoshka models keep their meaning in the leading dimensions: truncate, then re-normalise.
        pooled = pooled[:, :dimensions]
        pooled = pooled / np.clip(np.linalg.norm(pooled, axis=1, keepdims=True), 1e-12, None)
        return pooled.astype(np.float32).tolist()
