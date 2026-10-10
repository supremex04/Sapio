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
import hashlib
import json
import math
import re
import sys
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


STYLES = {
    # Hard for keyword search: tests whether meaning search copes with a student's own words.
    "paraphrase": """You write realistic questions a student would type to a study assistant. Each question must be answered by
the given item, but must NOT copy its wording: paraphrase, use everyday words, ask the way a confused learner would.""",
    # How students often ask: in their own words, but naming the terms they saw in the material.
    "natural": """You write realistic questions a student would type to a study assistant after reading their material. Each
question must be answered by the given item. Ask naturally, as a student would: in your own words, and use the key terms
from the material where a student would (names, commands, technical terms), without quoting whole sentences.""",
}


def generate_questions(data: dict, style: str) -> list[dict]:
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
                    system=STYLES[style],
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


def rrf(rankings: list[list[int]], weights: list[float], k: int = 60, candidates: int = 30) -> list[int]:
    """Weighted Reciprocal Rank Fusion, as HybridRetriever does: score = sum of weight / (k + rank)."""
    score: dict[int, float] = {}
    for ranking, weight in zip(rankings, weights, strict=True):
        for pos, i in enumerate(ranking[:candidates]):
            score[i] = score.get(i, 0.0) + weight / (k + pos + 1)
    return sorted(score, key=lambda i: -score[i])


def fused_rank(case: dict, keyword_weight: float, vector_weight: float, k: int, candidates: int = 30) -> int:
    fused = rrf([case["keyword"], case["dense"]], [keyword_weight, vector_weight], k, candidates)
    seen = set(fused)
    fused += [i for i in case["dense"] if i not in seen]  # beyond the candidates, dense order
    return [case["groups"][r] for r in fused].index(case["target"]) + 1


def metrics(ranks: list[int]) -> str:
    r = np.array(ranks)
    return (
        f"n={len(r):3d}  hit@1 {np.mean(r <= 1):.0%}  hit@3 {np.mean(r <= 3):.0%}  hit@5 {np.mean(r <= 5):.0%}  "
        f"MRR {np.mean(1 / r):.3f}"
    )


def build_cases(
    name: str,
    provider_cfg: ProviderConfig,
    emb_cfg: EmbeddingConfig,
    data: dict,
    questions: list[dict],
    cache: Path,
) -> list[dict]:
    """Per question: keyword and meaning rankings of its document's items (vectors cached on disk per model)."""
    embedder = None
    cases: list[dict] = []
    slug = re.sub(r"[^a-z0-9]+", "-", name.lower())
    for doc in data["documents"]:
        for kind in ("passages", "figures"):
            items = doc[kind]
            qs = [q for q in questions if q["doc"] == doc["title"] and q["kind"] == kind]
            if not items or not qs:
                continue
            key = hashlib.sha1(
                json.dumps([doc["title"], kind, [i["id"] for i in items], [q["q"] for q in qs]]).encode()
            )
            path = cache / f"{slug}-{key.hexdigest()[:16]}.npz"
            if path.exists():
                saved = np.load(path)
                corpus, queries = saved["corpus"], saved["queries"]
            else:
                embedder = embedder or RoutedEmbedder(build_provider("p", provider_cfg), emb_cfg)
                embed_items = [
                    EmbedItem(
                        text=i["text"],
                        image=ImagePart(Path(i["image"]).read_bytes(), i.get("mime", "image/png"))
                        if i.get("image")
                        else None,
                    )
                    for i in items
                ]
                corpus = np.array(embedder.embed(embed_items, "RETRIEVAL_DOCUMENT"))
                queries = np.array(embedder.embed([EmbedItem(text=q["q"]) for q in qs], "RETRIEVAL_QUERY"))
                np.savez(path, corpus=corpus, queries=queries)
            # Copies of the same image (a diagram reused on several slides) count as the same answer.
            groups = [i.get("group", i["id"]) for i in items]
            target_of = {i["id"]: i.get("group", i["id"]) for i in items}
            texts = [i["text"] for i in items]
            for q, vec in zip(qs, queries, strict=True):
                cases.append(
                    {
                        "kind": kind,
                        "set": q.get("set", ""),
                        "dense": [int(i) for i in np.argsort(-(corpus @ vec))],
                        "keyword": bm25_ranking(texts, q["q"]),
                        "groups": groups,
                        "target": target_of[q["target"]],
                    }
                )
    return cases


def report(name: str, cases: list[dict], keyword_weight: float, vector_weight: float, k: int) -> None:
    print(f"\n{name}   (fusion: keyword {keyword_weight} / vector {vector_weight}, k={k})")
    for qset in sorted({c["set"] for c in cases}):
        for kind in ("passages", "figures"):
            sel = [c for c in cases if c["kind"] == kind and c["set"] == qset]
            if not sel:
                continue
            dense = [[c["groups"][r] for r in c["dense"]].index(c["target"]) + 1 for c in sel]
            fused = [fused_rank(c, keyword_weight, vector_weight, k) for c in sel]
            print(f"  [{qset}] {kind:8s} meaning only  {metrics(dense)}")
            print(f"  [{qset}] {kind:8s} hybrid        {metrics(fused)}")


def tune(name: str, cases: list[dict]) -> None:
    """Grid over fusion settings; ranks by mean MRR over question sets (each set counts equally)."""
    sets = sorted({c["set"] for c in cases})
    rows = []
    for kw in (0.5, 0.75, 1.0):
        for vw in (1.0, 1.5, 2.0, 3.0):
            for k in (10, 30, 60):
                per_set = []
                for qset in sets:
                    ranks = [
                        fused_rank(c, kw, vw, k)
                        for c in cases
                        if c["set"] == qset and c["kind"] == "passages"
                    ]
                    per_set.append(
                        (
                            np.mean(1 / np.array(ranks)),
                            np.mean(np.array(ranks) <= 1),
                            np.mean(np.array(ranks) <= 3),
                        )
                    )
                rows.append((np.mean([p[0] for p in per_set]), kw, vw, k, per_set))
    rows.sort(key=lambda r: -r[0])
    print(f"\n{name}: fusion settings by mean passage MRR over {sets}")
    for mean, kw, vw, k, per_set in rows[:8]:
        detail = "  ".join(
            f"{s}: MRR {m:.3f} @1 {h1:.0%} @3 {h3:.0%}" for s, (m, h1, h3) in zip(sets, per_set, strict=True)
        )
        print(f"  keyword {kw:<4} vector {vw:<4} k={k:<3} mean MRR {mean:.3f} | {detail}")
    base = next(r for r in rows if (r[1], r[2], r[3]) == (1.0, 1.0, 60))
    print(f"  current production (1 / 1 / k=60): mean MRR {base[0]:.3f}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("export")
    parser.add_argument(
        "--questions",
        action="append",
        metavar="STYLE=FILE",
        help="question set to use (generated if FILE is missing); styles: " + ", ".join(STYLES),
    )
    parser.add_argument("--models", default="all", help="comma-separated substrings of CANDIDATES names")
    parser.add_argument("--fusion", default="1,1,60", help="keyword weight, vector weight, k for the report")
    parser.add_argument("--tune", action="store_true", help="grid-search the fusion settings")
    parser.add_argument("--cache", default=".eval_cache", help="directory for cached vectors")
    args = parser.parse_args()
    get_settings()  # loads .env (API keys)
    data = json.loads(Path(args.export).read_text())
    questions: list[dict] = []
    for spec in args.questions or ["paraphrase=eval_questions.json"]:
        style, _, file = spec.partition("=")
        qpath = Path(file)
        if not qpath.exists():
            print(f"Generating {style} questions…", file=sys.stderr)
            qpath.write_text(json.dumps(generate_questions(data, style), indent=1))
        questions += [{**q, "set": style} for q in json.loads(qpath.read_text())]
    print(
        f"{len(questions)} questions over {sum(len(d['passages']) for d in data['documents'])} passages and "
        f"{sum(len(d['figures']) for d in data['documents'])} figures"
    )
    cache = Path(args.cache)
    cache.mkdir(exist_ok=True)
    kw, vw, k = (float(x) for x in args.fusion.split(","))
    for name, (pcfg, ecfg) in CANDIDATES.items():
        if args.models != "all" and not any(
            m.strip().lower() in name.lower() for m in args.models.split(",")
        ):
            continue
        cases = build_cases(name, pcfg, ecfg, data, questions, cache)
        report(name, cases, kw, vw, int(k))
        if args.tune:
            tune(name, cases)


if __name__ == "__main__":
    main()
