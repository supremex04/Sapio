"""The committed contract (contracts/inference-openapi.json) must describe exactly what the service serves."""

import importlib.util
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "export_openapi.py"


def _exporter():
    spec = importlib.util.spec_from_file_location("export_openapi", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_committed_contract_matches_the_code():
    exporter = _exporter()
    assert exporter.CONTRACT.read_text() == exporter.render(), (
        "The inference API changed: review the change (the backend's DTOs and InferenceContractTest depend on "
        "it), bump CONTRACT_VERSION if it breaks callers, then run scripts/export_openapi.py"
    )


def test_every_endpoint_documents_the_error_envelope():
    import json

    contract = json.loads(_exporter().CONTRACT.read_text())
    for path, methods in contract["paths"].items():
        if path == "/health":
            continue
        for op in methods.values():
            for status in ("422", "503"):
                ref = op["responses"][status]["content"]["application/json"]["schema"]["$ref"]
                assert ref.endswith("/ErrorResponse"), (path, status)
