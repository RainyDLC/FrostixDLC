#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Encode GLSL -> Base64(XOR(KEY)) and patch ShaderData.java in place."""
import base64, re, sys

KEY = b"Nightix//shader-veil//2026"
SRC = r"C:/Users/selse/Desktop/Nightix-1_21_11-master/src/client/java/ru/white/utils/render/shader/ShaderData.java"
GLSL = r"C:/Users/selse/Desktop/Nightix-1_21_11-master/.workbuddy-ai/sky_work/core_shader_sky_fsh.glsl"
TARGET_KEY = "core/shader_sky|fsh"

with open(GLSL, "r", encoding="utf-8", newline="") as f:
    glsl = f.read()

# sanity: no CR, no tabs that could surprise, ascii only
assert "\r" not in glsl, "CR found"
glsl.encode("ascii")  # raises if non-ascii

enc = bytes(b ^ KEY[i % len(KEY)] for i, b in enumerate(glsl.encode("utf-8")))
b64 = base64.b64encode(enc).decode("ascii")

with open(SRC, "r", encoding="utf-8") as f:
    text = f.read()

pat = re.compile(r'(SOURCES\.put\("' + re.escape(TARGET_KEY) + r'",\s*")[^"]*("\))')
new_text, n = pat.subn(lambda m: m.group(1) + b64 + m.group(2), text, count=1)
if n != 1:
    print("PATCH FAILED: entry not found")
    sys.exit(1)

with open(SRC, "w", encoding="utf-8", newline="") as f:
    f.write(new_text)

# verify round-trip
with open(SRC, "r", encoding="utf-8") as f:
    check = f.read()
m = pat.search(check)
dec = bytes(b ^ KEY[i % len(KEY)] for i, b in enumerate(base64.b64decode(m.group(0).split('"')[3])))
assert dec.decode("utf-8") == glsl, "round-trip mismatch"
print("PATCH OK:", TARGET_KEY, "glsl", len(glsl), "chars -> b64", len(b64), "chars; round-trip verified")
