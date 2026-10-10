"""Compare embedding models on real study material: does the right passage come back for a student's question?

Usage (from inference/):
    .venv/bin/python scripts/eval_embeddings.py export.json [--questions questions.json]

``export.json`` holds documents as exported from Postgres (see docs/guides/configuration.md, "Choosing an
embedding model"): {"documents": [{"title", "passages": [{"id", "text"}], "figures": [{"id", "text", "image"}]}]}.
Questions are generated once with the configured LLM (one per passage/figure, paraphrased so word overlap alone
cannot answer them) and cached in --questions, so every model is judged on the same questions.

Reports, per model: hit@1 / hit@3 / hit@5 (the right item ranks in the top k) and MRR, for passages and figures.
Candidates are defined in CANDIDATES below; each is a (provider config, embeddings config) pair.
"""

import argparse
import json
import math
import re
import sys
import time
from collections import Counter
from pathlib import Path

import numpy as np
from pydantic import BaseModel

from ved_inference.config import EmbeddingConfig, ProviderConfig, get_settings
from ved_inference.llm.base import EmbedItem, ImagePart
from ved_inference.llm.registry import build_llm, build_provider
from ved_inference.llm.router import RoutedEmbedder

QWEN = {
    "file": "onnx/model_int8.onnx",
    "pooling": "last",
    "batch_size": 4,
    "query_prefix": "Instruct: Given a student's question about their study material, retrieve passages that answer it\n"
    "Query: ",
}
CANDIDATES = {
    "gemini-embedding-2 (API, multimodal)": (
        ProviderConfig(type="gemini", api_key_env="VED_INFERENCE_GEMINI_API_KEY"),
        EmbeddingConfig(
            provider="p", model="gemini-embedding-2", dimensions=768, multimodal=True, batch_size=64
        ),
    ),
    "Qwen3-Embedding-0.6B int8 (local CPU)": (
        ProviderConfig(type="onnx"),
        EmbeddingConfig(
            provider="p",
            model="onnx-community/Qwen3-Embedding-0.6B-ONNX",
            dimensions=768,
            multimodal=False,
            batch_size=32,
            options=QWEN,
        ),
    ),
}


class Question(BaseModel):
    index: int
    question: str


class Questions(BaseModel):
    questions: list[Question]


SYSTEM = """You write realistic questions a student would type to a study assistant. Each question must be answered by the
given item, but must NOT copy its wording: paraphrase, use everyday words, ask the way a confused learner would."""


def generate_questions(data: dict) -> list[dict]:
    llm = build_llm(get_settings())
    out: list[dict] = []
    for doc in data["documents"]:
        for kind in ("passages", "figures"):
            items = [i for i in doc[kind] if len(i["text"]) >= 120]
            for start in range(0, len(items), 15):
                batch = items[start : start + 15]
                listing = "\n\n".join(f"[{n}] {i['text'][:1200]}" for n, i in enumerate(batch))
                what = (
                    "passage"
                    if kind == "passages"
                    else "figure (described in words; ask about what it shows)"
                )
                result = llm.generate(
                    system=SYSTEM,
                    prompt=f"One question per {what}, same order, index = the number in brackets.\n\n{listing}",
                    schema=Questions,
                    temperature=0.7,
                    task="questions",
                )
                for q in result.questions:
                    if 0 <= q.index < len(batch):
                        out.append(
                            {
                                "doc": doc["title"],
                                "kind": kind,
                                "target": batch[q.index]["id"],
                                "q": q.question,
                            }
                        )
                print(f"  {doc['title']}: {kind} {start + len(batch)}/{len(items)}", file=sys.stderr)
    return out


_WORD = re.compile(r"[a-z0-9]+")


def _tokens(text: str) -> list[str]:
    return [w[:6] for w in _WORD.findall(text.lower()) if len(w) > 2]  # crude stemming, like a search index


def bm25_ranking(corpus: list[str], query: str, k1: float = 1.2, b: float = 0.75) -> list[int]:
    """Keyword ranking standing in for Postgres full-text search (production runs both and fuses them)."""
    docs = [Counter(_tokens(t)) for t in corpus]
    avg = sum(sum(d.values()) for d in docs) / max(len(docs), 1)
    df = Counter(w for d in docs for w in d)
    scores = []
    for d in docs:
        length = sum(d.values())
        score = 0.0
        for w in set(_tokens(query)):
            if w in d:
                idf = math.log(1 + (len(docs) - df[w] + 0.5) / (df[w] + 0.5))
                score += idf * d[w] * (k1 + 1) / (d[w] + k1 * (1 - b + b * length / avg))
        scores.append(score)
    return [int(i) for i in np.argsort(-np.array(scores)) if scores[i] > 0]


def rrf(rankings: list[list[int]], k: int = 60, candidates: int = 30) -> list[int]:
    """Reciprocal Rank Fusion, as HybridRetriever does."""
    score: dict[int, float] = {}
    for ranking in rankings:
        for pos, i in enumerate(ranking[:candidates]):
            score[i] = score.get(i, 0.0) + 1 / (k + pos + 1)
    return sorted(score, key=lambda i: -score[i])


def evaluate(
    name: str, provider_cfg: ProviderConfig, emb_cfg: EmbeddingConfig, data: dict, questions: list[dict]
):
    embedder = RoutedEmbedder(build_provider("p", provider_cfg), emb_cfg)
    scores = {"passages": [], "figures": [], "passages (hybrid)": [], "figures (hybrid)": []}
    seconds = 0.0
    for doc in data["documents"]:
        for kind in ("passages", "figures"):
            items = doc[kind]
            qs = [q for q in questions if q["doc"] == doc["title"] and q["kind"] == kind]
            if not items or not qs:
                continue
            embed_items = [
                EmbedItem(
                    text=i["text"],
                    image=ImagePart(Path(i["image"]).read_bytes(), i.get("mime", "image/png"))
                    if i.get("image")
                    else None,
                )
                for i in items
            ]
            t = time.time()
            corpus = np.array(embedder.embed(embed_items, "RETRIEVAL_DOCUMENT"))
            queries = np.array(embedder.embed([EmbedItem(text=q["q"]) for q in qs], "RETRIEVAL_QUERY"))
            seconds += time.time() - t
            # Copies of the same image (a diagram reused on several slides) count as the same answer.
            group = {i["id"]: i.get("group", i["id"]) for i in items}
            ids = [i["id"] for i in items]
            texts = [i["text"] for i in items]
            for q, vec in zip(qs, queries, strict=True):
                dense = [int(i) for i in np.argsort(-(corpus @ vec))]
                fused = rrf([bm25_ranking(texts, q["q"]), dense])
                fused += [i for i in dense if i not in set(fused)]  # beyond the candidates, dense order
                target = group[q["target"]]
                for label, order in ((kind, dense), (f"{kind} (hybrid)", fused)):
                    scores[label].append([group[ids[r]] for r in order].index(target) + 1)
    print(f"\n{name}  (embedding time {seconds:.1f}s)")
    for kind, ranks in scores.items():
        if not ranks:
            continue
        r = np.array(ranks)
        print(
            f"  {kind:18s} n={len(r):3d}  hit@1 {np.mean(r <= 1):.0%}  hit@3 {np.mean(r <= 3):.0%}  "
            f"hit@5 {np.mean(r <= 5):.0%}  MRR {np.mean(1 / r):.3f}"
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("export")
    parser.add_argument("--questions", default="eval_questions.json")
    args = parser.parse_args()
    get_settings()  # loads .env (API keys)
    data = json.loads(Path(args.export).read_text())
    qpath = Path(args.questions)
    if qpath.exists():
        questions = json.loads(qpath.read_text())
    else:
        print("Generating questions…", file=sys.stderr)
        questions = generate_questions(data)
        qpath.write_text(json.dumps(questions, indent=1))
    print(
        f"{len(questions)} questions over {sum(len(d['passages']) for d in data['documents'])} passages and "
        f"{sum(len(d['figures']) for d in data['documents'])} figures"
    )
    for name, (pcfg, ecfg) in CANDIDATES.items():
        evaluate(name, pcfg, ecfg, data, questions)


if __name__ == "__main__":
    main()
