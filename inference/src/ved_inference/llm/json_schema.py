"""Turns a Pydantic model into the self-contained JSON Schema subset that LLM APIs accept."""

from typing import Any

from pydantic import BaseModel

# Schema *metadata* keywords to drop. Never applied to the keys of a "properties" map,
# which are field names (a field may well be called "title").
_DROP_KEYWORDS = {"title", "default"}


def response_schema(model: type[BaseModel]) -> dict[str, Any]:
    raw = model.model_json_schema()
    defs = raw.pop("$defs", {})

    def resolve(node: Any) -> Any:
        if isinstance(node, list):
            return [resolve(v) for v in node]
        if not isinstance(node, dict):
            return node
        if "$ref" in node:
            return resolve(defs[node["$ref"].rsplit("/", 1)[-1]])
        out: dict[str, Any] = {}
        for key, value in node.items():
            if key in _DROP_KEYWORDS:
                continue
            if key == "properties":
                out[key] = {name: resolve(prop) for name, prop in value.items()}
            else:
                out[key] = resolve(value)
        if out.get("type") == "object":
            out.setdefault("additionalProperties", False)
            out["required"] = list(out.get("properties", {}).keys())
        return out

    return resolve(raw)
