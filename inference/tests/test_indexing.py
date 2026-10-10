import base64

import pytest

from tests.conftest import png_bytes
from tests.fakes import FakeEmbedder, FakeLlm
from ved_inference.api import index_schemas as s
from ved_inference.errors import EngineError
from ved_inference.indexing.service import IndexingService

IMG = base64.b64encode(png_bytes()).decode()


def test_figures_are_described_in_batches_and_decorations_flagged():
    llm = FakeLlm(
        {
            "figures": [
                {"index": 0, "kind": "DIAGRAM", "educational": True, "description": "A plant cell."},
                {"index": 1, "kind": "DECORATIVE", "educational": True, "description": "A logo."},
            ]
        }
    )
    result = IndexingService(llm, FakeEmbedder()).describe_figures(
        s.DescribeFiguresRequest(
            figures=[
                s.FigureIn(key="a", image_base64=IMG, caption="Fig 1"),
                s.FigureIn(key="b", image_base64=IMG),
            ]
        )
    )
    assert [(f.key, f.educational) for f in result.figures] == [("a", True), ("b", False)]
    assert llm.images == [2]
    assert llm.tasks == ["figures"]


def test_missing_description_is_an_error():
    llm = FakeLlm({"figures": []})
    with pytest.raises(EngineError):
        IndexingService(llm, FakeEmbedder()).describe_figures(
            s.DescribeFiguresRequest(figures=[s.FigureIn(key="a", image_base64=IMG)])
        )


def test_summaries_dedupe_terms_and_keep_keys():
    llm = FakeLlm(
        {"summaries": [{"key": "n1", "summary": " Cells. ", "key_terms": ["ATP", "atp", " Cell "]}]}
    )
    result = IndexingService(llm, FakeEmbedder()).summarize(
        s.SummarizeRequest(
            nodes=[s.SummarizeNode(key="n1", path="Ch 1", text="Cells make ATP.", child_summaries=["x"])]
        )
    )
    assert result.summaries[0].summary == "Cells." and result.summaries[0].key_terms == ["ATP", "Cell"]
    assert "Subsection summaries" in llm.prompts[0]


def test_embed_supports_text_and_images():
    embedder = FakeEmbedder()
    result = IndexingService(FakeLlm(), embedder).embed(
        s.EmbedRequest(
            task="RETRIEVAL_DOCUMENT",
            items=[s.EmbedItemIn(text="abc"), s.EmbedItemIn(image_base64=IMG, mime="image/png")],
        )
    )
    assert result.vectors == [[3.0, 0.0, 0.0, 1.0], [0.0, 1.0, 0.0, 1.0]]
    assert embedder.calls == [(2, "RETRIEVAL_DOCUMENT")]
