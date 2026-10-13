"""Audio and video: transcription, then study notes from the transcript (two calls, so the backend can keep the
transcript and retry the notes alone when the AI is busy)."""

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, UploadFile, status
from fastapi.concurrency import run_in_threadpool

from sapio_inference.api import media_schemas as m
from sapio_inference.api.errors import ERROR_RESPONSES
from sapio_inference.api.routes import to_response
from sapio_inference.api.schemas import ExtractResponse
from sapio_inference.api.security import require_internal_api_key
from sapio_inference.config import Settings, get_settings
from sapio_inference.errors import DocumentError
from sapio_inference.speech.audio import decode_windows, probe_duration
from sapio_inference.speech.base import SpeechEngine, Transcript, TranscriptSegment
from sapio_inference.speech.notes import LectureNotes

router = APIRouter(dependencies=[Depends(require_internal_api_key)], responses=ERROR_RESPONSES)


def get_speech(request: Request) -> SpeechEngine:
    return request.app.state.speech


def get_lecture_notes(request: Request) -> LectureNotes:
    return request.app.state.lecture_notes


@router.post("/v1/transcribe", response_model=m.TranscriptModel)
async def transcribe(
    file: UploadFile,
    speech: Annotated[SpeechEngine, Depends(get_speech)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> m.TranscriptModel:
    config = settings.speech
    if file.size is not None and file.size > config.max_upload_bytes:
        raise HTTPException(status.HTTP_413_CONTENT_TOO_LARGE, detail="File too large")

    def work() -> Transcript:
        duration = probe_duration(file.file)
        if duration > config.max_duration_seconds:
            limit = config.max_duration_seconds // 60
            raise DocumentError(f"Recordings can be at most {limit} minutes long")
        return speech.transcribe(decode_windows(file.file, config.window_seconds), duration)

    # Decoding and the model are blocking (and long): keep the event loop free.
    transcript = await run_in_threadpool(work)
    return m.TranscriptModel(
        language=transcript.language,
        duration_seconds=transcript.duration,
        engine=transcript.engine,
        segments=[
            m.TranscriptSegmentModel(start=s.start, end=s.end, text=s.text) for s in transcript.segments
        ],
    )


@router.post("/v1/extract/transcript", response_model=ExtractResponse)
def lecture_notes(
    body: m.LectureNotesRequest, notes: Annotated[LectureNotes, Depends(get_lecture_notes)]
) -> ExtractResponse:
    t = body.transcript
    transcript = Transcript(
        language=t.language,
        duration=t.duration_seconds,
        engine=t.engine,
        segments=[TranscriptSegment(s.start, s.end, s.text) for s in t.segments],
    )
    return to_response(notes.build(transcript, body.title))
