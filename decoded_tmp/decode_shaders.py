import re, base64, os

path = "src/client/java/ru/white/utils/render/shader/ShaderData.java"
src = open(path, encoding="utf-8").read()
KEY = "Nightix//shader-veil//2026".encode("utf-8")

pat = re.compile(r'SOURCES\.put\(\s*"([^"]+)"\s*,\s*"([^"]*)"\s*\)')
outdir = "decoded_tmp/out"
os.makedirs(outdir, exist_ok=True)
for m in pat.finditer(src):
    key = m.group(1)
    b64 = m.group(2)
    try:
        data = base64.b64decode(b64)
        dec = bytes(data[i] ^ KEY[i % len(KEY)] for i in range(len(data)))
        text = dec.decode("utf-8", errors="replace")
    except Exception as e:
        text = "<<DECODE ERROR: %s>>" % e
    fname = key.replace("/", "_").replace("|", ".")
    with open(os.path.join(outdir, fname + ".glsl"), "w", encoding="utf-8") as f:
        f.write(text)
    print("KEY:", key, "->", fname, "len=", len(text))
