"""Cheap, deterministic language guess for search configuration (stemming). LLMs refine it later."""

import re

_STOPWORDS = {
    "en": {
        "the",
        "and",
        "of",
        "to",
        "is",
        "in",
        "that",
        "it",
        "for",
        "are",
        "with",
        "as",
        "this",
        "be",
        "by",
    },
    "es": {"el", "la", "de", "que", "y", "en", "los", "las", "es", "por", "un", "una", "con", "para", "del"},
    "fr": {
        "le",
        "la",
        "les",
        "de",
        "et",
        "des",
        "est",
        "en",
        "un",
        "une",
        "du",
        "que",
        "pour",
        "dans",
        "qui",
    },
    "de": {
        "der",
        "die",
        "und",
        "das",
        "ist",
        "in",
        "den",
        "von",
        "zu",
        "mit",
        "sich",
        "des",
        "auf",
        "ein",
        "eine",
    },
    "pt": {"o", "a", "de", "que", "e", "do", "da", "em", "um", "para", "os", "uma", "com", "no", "na"},
    "it": {
        "il",
        "di",
        "che",
        "e",
        "la",
        "per",
        "un",
        "in",
        "una",
        "sono",
        "con",
        "del",
        "della",
        "non",
        "le",
    },
}
_WORD = re.compile(r"[^\W\d_]+", re.UNICODE)


def detect_language(text: str) -> str:
    """ISO 639-1 code, or "und" when undetermined (non-Latin scripts, too little text)."""
    words = [w.lower() for w in _WORD.findall(text[:20_000])]
    if len(words) < 20:
        return "und"
    if sum(1 for w in words if not w.isascii()) > 0.3 * len(words):
        return "und"  # e.g. Devanagari: no stemmer in Postgres; 'simple' config is used
    scores = {lang: sum(1 for w in words if w in stop) for lang, stop in _STOPWORDS.items()}
    best = max(scores, key=scores.__getitem__)
    return best if scores[best] >= 0.05 * len(words) else "und"
