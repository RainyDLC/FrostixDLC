import base64, re, os

KEY = "Nightix//shader-veil//2026".encode("utf-8")
ROOT = r"C:\Users\selse\Desktop\Nightix-1_21_11-master"
src = os.path.join(ROOT, "src/client/java/ru/white/utils/render/shader/ShaderData.java")
out = r"C:\Users\selse\Desktop\Nightix-1_21_11-master\.workbuddy-ai"
with open(src, "r", encoding="utf-8") as f:
    text = f.read()

def decrypt(b64):
    data = base64.b64decode(b64)
    return bytes(data[i] ^ KEY[i % len(KEY)] for i in range(len(data))).decode("utf-8", "replace")

pat = re.compile(r'SOURCES\.put\("([^"]+)",\s*"([^"]+)"\)')
want = {"core/shader_sky|vsh", "core/shader_sky_fullscreen|vsh", "core/shader_sky_blur|fsh"}
for name, b64 in pat.findall(text):
    if name in want:
        glsl = decrypt(b64)
        fn = name.replace("/", "_").replace("|", ".") + ".glsl"
        with open(os.path.join(out, fn), "w", encoding="utf-8") as o:
            o.write(glsl)
        print("==== %s ====" % name)
        print(glsl)
        print()
