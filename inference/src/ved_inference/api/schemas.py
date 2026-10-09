from typing import Literal

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    """JSON contracts use camelCase, matching the Java backend."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class SegmentOut(ApiModel):
    page_from: int
    page_to: int
    method: Literal["text", "ocr"]
    markdown: str


class ExtractResponse(ApiModel):
    markdown: str
    engine: str
    page_count: int
    text_pages: int
    ocr_pages: int
    segments: list[SegmentOut]


class HealthResponse(ApiModel):
    status: str
    ocr_engine: str
    llm_model: str
