from types import SimpleNamespace

import numpy as np
import pytest

from ved_inference.config import ProviderConfig
from ved_inference.errors import EngineError
from ved_inference.llm.base import EmbedItem
from ved_inference.llm.providers.onnx_embedder import OnnxEmbeddingOptions, OnnxEmbeddingProvider


class FakeTokenizer:
    """Each word is a token; right padding with 0."""

    def encode_batch(self, texts):
        lengths = [len(t.split()) for t in texts]
        width = max(lengths)
        return [
            SimpleNamespace(
                ids=list(range(1, n + 1)) + [0] * (width - n), attention_mask=[1] * n + [0] * (width - n)
            )
            for n in lengths
        ]


class FakeSession:
    """Hidden state of token t is [t, 1, 0, 0]: pooling and truncation are easy to check."""

    def __init__(self):
        self.feeds = []

    def get_inputs(self):
        node = lambda name, shape=None, type="tensor(float)": SimpleNamespace(
            name=name, shape=shape, type=type
        )
        return [
            node("input_ids"),
            node("attention_mask"),
            node("position_ids"),
            node("past_key_values.0.key", [None, 2, None, 3]),
        ]

    def get_outputs(self):
        return [SimpleNamespace(name="last_hidden_state")]

    def run(self, names, feed):
        self.feeds.append(feed)
        ids = feed["input_ids"].astype(np.float32)
        return [np.stack([ids, np.ones_like(ids), np.zeros_like(ids), np.zeros_like(ids)], axis=-1)]


def provider(session=None):
    p = OnnxEmbeddingProvider("local", ProviderConfig(type="onnx"))
    s = session or FakeSession()
    p._load = lambda model, opts: (s, FakeTokenizer(), s.get_inputs())
    return p, s


def test_last_token_pooling_prefixes_truncation_and_normalisation():
    p, session = provider()
    opts = {"pooling": "last", "query_prefix": "q: "}
    [doc] = p.embed("m", 2, [EmbedItem(text="a b c")], "RETRIEVAL_DOCUMENT", opts)
    [query] = p.embed("m", 2, [EmbedItem(text="a")], "RETRIEVAL_QUERY", opts)
    assert np.allclose(
        doc, np.array([3, 1]) / np.linalg.norm([3, 1])
    )  # last real token = 3, truncated to 2 dims
    assert np.allclose(query, np.array([2, 1]) / np.linalg.norm([2, 1]))  # "q: a" has 2 tokens
    feed = session.feeds[0]
    assert feed["past_key_values.0.key"].shape == (1, 2, 0, 3)  # empty cache for decoder exports
    assert feed["position_ids"].tolist() == [[0, 1, 2]]


def test_mean_pooling_ignores_padding_and_keeps_input_order():
    p, _ = provider()
    vectors = p.embed(
        "m", 1, [EmbedItem(text="a b c d"), EmbedItem(text="a")], "RETRIEVAL_DOCUMENT", {"pooling": "mean"}
    )
    assert vectors == [[1.0], [1.0]]  # normalised 1-d vectors, in the order given despite length sorting


def test_rejects_more_dimensions_than_the_model_has_and_text_generation():
    p, _ = provider()
    with pytest.raises(EngineError, match="4 dimensions"):
        p.embed("m", 8, [EmbedItem(text="a")], "RETRIEVAL_DOCUMENT", {})
    with pytest.raises(EngineError, match="only computes embeddings"):
        p.generate(None, system="", prompt="", schema=None, temperature=0, images=[])
    assert OnnxEmbeddingOptions().pooling == "mean"
