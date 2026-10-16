"""Visualization planning and coding with the LLM. Rendering happens elsewhere (the renderer service)."""

import re

from sapio_inference.api import visualize_schemas as api
from sapio_inference.llm.router import RoutedLlm
from sapio_inference.visualize import prompts
from sapio_inference.visualize.models import SceneCode, Storyboard

_FENCE = re.compile(r"^```(?:python)?\s*\n|\n```\s*$")


class VisualizeService:
    def __init__(self, llm: RoutedLlm) -> None:
        self._llm = llm

    def storyboard(self, request: api.StoryboardRequest) -> api.StoryboardResponse:
        board = self._llm.generate(
            system=prompts.storyboard_system(request.language, request.learner),
            prompt=prompts.storyboard_prompt(request.request, request.context),
            schema=Storyboard,
            temperature=0.5,
            task="visualize_plan",
        )
        beats = [
            api.BeatIo(shows=b.shows.strip(), says=b.says.strip()) for b in board.beats if b.shows.strip()
        ][:6]
        feasible = board.feasible and len(beats) > 0
        return api.StoryboardResponse(
            feasible=feasible,
            reason=board.reason.strip() if not feasible else "",
            title=board.title.strip()[:300] or "Visualization",
            kind=board.kind,
            beats=beats,
            model=self._llm.model_name,
        )

    def code(self, request: api.SceneCodeRequest) -> api.SceneCodeResponse:
        beats = [(b.shows, b.says) for b in request.beats]
        prompt = (
            prompts.repair_prompt(request.title, beats, request.previous_code, request.error)
            if request.previous_code and request.error
            else prompts.code_prompt(request.title, beats)
        )
        scene = self._llm.generate(
            system=prompts.CODE_SYSTEM,
            prompt=prompt,
            schema=SceneCode,
            temperature=0.2,
            task="visualize_code",
        )
        return api.SceneCodeResponse(code=strip_fences(scene.code), model=self._llm.model_name)


def strip_fences(code: str) -> str:
    """Models sometimes wrap the file in a Markdown code fence inside the JSON string."""
    return _FENCE.sub("", code.strip()).strip() + "\n"
