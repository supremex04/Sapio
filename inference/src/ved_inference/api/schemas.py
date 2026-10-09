from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    """JSON contracts use camelCase, matching the Java backend."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class OcrResponse(ApiModel):
    markdown: str
    engine: str
    page_count: int


class HealthResponse(ApiModel):
    status: str
    engine: str
