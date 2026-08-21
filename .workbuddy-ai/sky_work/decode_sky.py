#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Decode Nightix ShaderData sources (Base64 + XOR with ShaderStore KEY)."""
import base64, re, sys, os

KEY = b"Nightix//shader-veil//2026"
SRC = r"C:/Users/selse/Desktop/Nightix-1_21_11-master/src/client/java/ru/white/utils/render/shader/ShaderData.java"
OUT = r"C:/Users/selse/Desktop/Nightix-1_21_11-master/.workbuddy-ai/sky_work"

os.makedirs(OUT, exist_ok=True)

with open(SRC, "r", encoding="utf-8") as f:
    text = f.read()

pat = re.compile(r'SOURCES\.put\("(core/shader_sky[^"]*)",\s*"([^"]+)"\)')
found = pat.findall(text)
for name, b64 in found:
    raw = base64.b64decode(b64)
    dec = bytes(b ^ KEY[i % len(KEY)] for i, b in enumerate(raw))
    try:
        glsl = dec.decode("utf-8")
    except UnicodeDecodeError:
        print("DECODE FAIL:", name)
        continue
    fn = name.replace("/", "_").replace("|", "_") + ".glsl"
    with open(os.path.join(OUT, fn), "w", encoding="utf-8", newline="\n") as f:
        f.write(glsl)
    print("OK", name, "->", fn, len(glsl), "chars")
