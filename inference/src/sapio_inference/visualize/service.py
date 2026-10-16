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
            prompt=prompts.storyboard_prompt(request.request, request.context, request.engine),
            schema=Storyboard,
            temperature=0.5,
            task="visualize_plan",
        )
        beats = [
            api.BeatIo(shows=b.shows.strip(), says=b.says.strip(), focus=b.focus.strip())
            for b in board.beats
            if b.shows.strip()
        ][:6]
        engine, reason = _engine(board, request.engine)
        feasible = board.feasible and len(beats) > 0 and not reason
        return api.StoryboardResponse(
            feasible=feasible,
            reason=(reason or board.reason.strip()) if not feasible else "",
            title=board.title.strip()[:300] or "Visualization",
            kind=board.kind,
            engine=engine,
            molecule=api.MoleculeIo(name=board.molecule.name.strip(), smiles=board.molecule.smiles.strip())
            if engine == "molecule" and board.molecule
            else None,
            protein=api.ProteinIo(
                pdb_id=board.protein.pdb_id.strip().upper(), name=board.protein.name.strip()
            )
            if engine == "protein" and board.protein
            else None,
            program=strip_fences(board.program) if engine == "code" else "",
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


def _engine(board: Storyboard, chosen: str) -> tuple[str, str]:
    """The engine to use and, when it cannot be used, why. Missing data falls back to an animation, unless the
    learner chose the engine (then they hear why it did not work)."""
    engine = board.engine if chosen == "auto" else chosen
    missing = {
        "molecule": not (board.molecule and board.molecule.smiles.strip()),
        "protein": not (board.protein and len(board.protein.pdb_id.strip()) == 4),
        "code": not board.program.strip(),
    }.get(engine, False)
    if not missing:
        return engine, ""
    if chosen == "auto":
        return "animation", ""
    what = {"molecule": "a molecule", "protein": "a structure in the Protein Data Bank", "code": "a program"}
    return engine, f"Could not find {what[engine]} to show for this request."


def strip_fences(code: str) -> str:
    """Models sometimes wrap the file in a Markdown code fence inside the JSON string."""
    return _FENCE.sub("", code.strip()).strip() + "\n"
