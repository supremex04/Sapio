"""Downloads the narration model (Kokoro-82M int8 and its voices) into /app/models, checked by SHA-256.

Run at image build time (see the Dockerfile).
"""

import hashlib
import pathlib
import urllib.request

base = "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/"
files = {
    "kokoro.onnx": (
        "kokoro-v1.0.int8.onnx",
        "6e742170d309016e5891a994e1ce1559c702a2ccd0075e67ef7157974f6406cb",
    ),
    "voices.bin": ("voices-v1.0.bin", "bca610b8308e8d99f32e6fe4197e7ec01679264efed0cac9140fe9c29f1fbf7d"),
}
out = pathlib.Path("/app/models")
out.mkdir(parents=True, exist_ok=True)
for name, (remote, sha) in files.items():
    data = urllib.request.urlopen(base + remote, timeout=300).read()
    assert hashlib.sha256(data).hexdigest() == sha, f"checksum mismatch for {remote}"
    (out / name).write_bytes(data)
