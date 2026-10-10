"""Indexing use-cases: figure descriptions, bottom-up summaries, embeddings."""

from collections.abc import Sequence

from ved_inference.api import index_schemas as api
from ved_inference.config import IndexingConfig
from ved_inference.errors import EngineError
from ved_inference.indexing.models import FigureDescriptions, NodeSummaries
from ved_inference.llm.base import Embedder, EmbedItem, ImagePart, LlmClient
from ved_inference.study import prompts

DESCRIBE_SYSTEM = """You index figures from a student's study material so they can be found and explained later.
Describe what each image shows precisely (labels, axes, steps, relationships) using the document's terminology.
Give each a role:
- VISUAL: a student learns from seeing it (diagrams, charts, photos, maps, annotated interfaces).
- TEXTUAL: its text alone carries the meaning (terminal output, code, text-only slides or screenshots of text).
  Transcribe the important text verbatim; the image itself will not be shown.
- DECORATIVE: logos, clip-art, icons, page ornaments, stock backgrounds.
When unsure between VISUAL and TEXTUAL choose VISUAL. Use kind SCREENSHOT for terminal, code or app captures."""

SUMMARY_SYSTEM = """You index a student's study material. For each section write a faithful summary of what it teaches,
using only its text (and its subsections' summaries), and list its key terms exactly as written. Write in {language}."""


class IndexingService:
    def __init__(self, llm: LlmClient, embedder: Embedder, config: IndexingConfig | None = None) -> None:
        self._llm = llm
        self._embedder = embedder
        self._config = config or IndexingConfig()

    def describe_figures(self, request: api.DescribeFiguresRequest) -> api.DescribeFiguresResponse:
        out: list[api.FigureDescriptionOut] = []
        figures = request.figures
        per_call = self._config.figures_per_call
        for start in range(0, len(figures), per_call):
            batch = figures[start : start + per_call]
            listing = "\n".join(
                f"[{i}] caption: {f.caption or '(none)'} | section: {f.context or '(unknown)'}"
                for i, f in enumerate(batch)
            )
            result = self._llm.generate(
                system=DESCRIBE_SYSTEM,
                prompt=f"Describe these {len(batch)} images, in order. Write descriptions in {prompts.language_name(request.language)}.\n{listing}",
                schema=FigureDescriptions,
                temperature=0.2,
                images=[ImagePart(data=f.image_bytes(), mime=f.mime) for f in batch],
                task="figures",
            )
            by_index = {d.index: d for d in result.figures if 0 <= d.index < len(batch)}
            for i, f in enumerate(batch):
                d = by_index.get(i)
                if d is None:
                    raise EngineError("Figure description incomplete. Please retry.")
                role = "DECORATIVE" if d.kind == "DECORATIVE" else d.role
                transcript = d.transcript.strip() if role == "TEXTUAL" else ""
                out.append(
                    api.FigureDescriptionOut(
                        key=f.key,
                        kind=d.kind,
                        role=role,
                        description=d.description.strip(),
                        transcript=transcript[: self._config.transcript_chars],
                    )
                )
        return api.DescribeFiguresResponse(figures=out, model=self._llm.model_name)

    def summarize(self, request: api.SummarizeRequest) -> api.SummarizeResponse:
        out: list[api.NodeSummaryOut] = []
        per_call = self._config.nodes_per_call
        for start in range(0, len(request.nodes), per_call):
            batch = request.nodes[start : start + per_call]
            listing = "\n\n".join(_node_block(n, self._config.node_text_chars) for n in batch)
            result = self._llm.generate(
                system=SUMMARY_SYSTEM.format(language=prompts.language_name(request.language)),
                prompt=f"Summarise each of these {len(batch)} sections. Return one entry per key.\n\n{listing}",
                schema=NodeSummaries,
                temperature=0.2,
                task="summaries",
            )
            got = {s.key: s for s in result.summaries}
            for n in batch:
                s = got.get(n.key)
                if s is None:
                    raise EngineError("Section summaries incomplete. Please retry.")
                terms = _dedupe(t.strip() for t in s.key_terms if t.strip())[:8]
                out.append(api.NodeSummaryOut(key=n.key, summary=s.summary.strip(), key_terms=terms))
        return api.SummarizeResponse(summaries=out, model=self._llm.model_name)

    def embed(self, request: api.EmbedRequest) -> api.EmbedResponse:
        items = [
            EmbedItem(
                text=i.text,
                image=ImagePart(data=i.image_bytes(), mime=i.mime or "image/png") if i.image_base64 else None,
            )
            for i in request.items
        ]
        vectors = self._embedder.embed(items, request.task)
        return api.EmbedResponse(
            vectors=vectors, model=self._embedder.model_name, dimensions=self._embedder.dimensions
        )


def _node_block(n: api.SummarizeNode, budget: int) -> str:
    text = n.text if len(n.text) <= budget else n.text[:budget] + "…"
    children = "\n".join(f"- {c}" for c in n.child_summaries) if n.child_summaries else ""
    return (
        f"### key: {n.key}\nSection: {n.path}\n"
        + (f"Subsection summaries:\n{children}\n" if children else "")
        + (f"Text:\n{text}" if text.strip() else "")
    )


def _dedupe(values: Sequence[str] | object) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for v in values:  # type: ignore[union-attr]
        k = v.lower()
        if k not in seen:
            seen.add(k)
            out.append(v)
    return out
