"""Restore original Quick HUD assets from checked-in, lossless Java byte parts.
Run from any directory: python scripts/restore_quick_assets.py
"""
from pathlib import Path
import base64
import hashlib
import re

root = Path(__file__).resolve().parents[1]
manifest = root / "RainyDLC/src/embedded/quick/manifest.tsv"
sources = root / "RainyDLC/src/client/java/dev/hatek/quickembedded"
for line in manifest.read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    name, filename, expected = line.split("\t")
    parts = sorted(sources.glob(f"{name}Part*.java"))
    if not parts:
        raise RuntimeError(f"Missing generated parts for {name}")
    data = b"".join(
        base64.b64decode(chunk, validate=True)
        for part in parts
        for chunk in re.findall(r'copy\(output, cursor, "([A-Za-z0-9+/=]+)"\)', part.read_text(encoding="utf-8"))
    )
    if hashlib.sha256(data).hexdigest() != expected:
        raise RuntimeError(f"SHA-256 mismatch: {filename}")
    target = manifest.parent / filename
    if target.exists() and target.read_bytes() != data:
        raise RuntimeError(f"Refusing to overwrite modified asset: {target}")
    target.write_bytes(data)
    print(f"Verified/restored {filename}: {len(data)} bytes")
