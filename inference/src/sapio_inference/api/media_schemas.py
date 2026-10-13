"""Wire records of the speech endpoints (audio / video uploads)."""

from pydantic import Field

from sapio_inference.api.schemas import ApiModel


class TranscriptSegmentModel(ApiModel):
    start: float = Field(ge=0, description="Seconds from the start of the recording")
    end: float = Field(ge=0)
    text: str


class TranscriptModel(ApiModel):
    language: str
    duration_seconds: float
    engine: str
    segments: list[TranscriptSegmentModel]


class LectureNotesRequest(ApiModel):
    """A transcript (from /v1/transcribe, stored by the backend) to turn into study notes."""

    title: str = Field(description="Shown to the model as the recording's name, e.g. the file name")
    transcript: TranscriptModel
