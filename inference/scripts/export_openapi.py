"""Writes the inference service's wire contract (OpenAPI) to contracts/inference-openapi.json.

The file is the single source of truth both services are tested against: tests/test_contract.py fails when the
code and the file disagree, and the backend's InferenceContractTest checks its DTOs against the same file.
After an intentional API change run:  .venv/bin/python scripts/export_openapi.py
"""

import json
import sys
from pathlib import Path

from sapio_inference.main import create_app

CONTRACT = Path(__file__).resolve().parents[2] / "contracts" / "inference-openapi.json"


def render() -> str:
    return json.dumps(create_app().openapi(), indent=2, sort_keys=True) + "\n"


def main() -> None:
    CONTRACT.write_text(render())
    print(f"wrote {CONTRACT}", file=sys.stderr)


if __name__ == "__main__":
    main()
