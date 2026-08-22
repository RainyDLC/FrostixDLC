#version 150

// Фоновый узор клик-гуи: mode 0 — квадратная сетка с узлами,
// mode 1 — соты (гексагональная решётка). Оба реагируют на курсор.

in vec2 pixelCoord;
in vec4 vParams;
in vec2 vPointer;
in vec4 vTint;
in float vMode;

out vec4 fragColor;

float hexDist(vec2 p) {
    p = abs(p);
    float c = dot(p, normalize(vec2(1.0, 1.7320508)));
    return max(c, p.x);
}

vec3 hexCoords(vec2 uv) {
    vec2 r = vec2(1.0, 1.7320508);
    vec2 h = r * 0.5;
    vec2 a = mod(uv, r) - h;
    vec2 b = mod(uv - h, r) - h;
    vec2 gv = dot(a, a) < dot(b, b) ? a : b;
    return vec3(gv, 0.5 - hexDist(gv));
}

void main() {
    float spacing = max(vParams.x, 1.0);
    float reach = max(vParams.w, 320.0);

    // подсветка возле курсора: 1 у курсора, плавный спад к краю радиуса
    float glow = 1.0 - clamp(length(pixelCoord - vPointer) / reach, 0.0, 1.0);
    glow = glow * glow * (3.0 - 2.0 * glow);

    float alpha;

    if (vMode < 0.5) {
        // ── сетка: тонкие линии + узлы на пересечениях ──
        vec2 g = abs(mod(pixelCoord, spacing) - spacing * 0.5);
        float aa = max(fwidth(g.x), fwidth(g.y));
        float lw = 0.65;
        float lx = 1.0 - smoothstep(lw, lw + 1.2 + aa, g.x);
        float ly = 1.0 - smoothstep(lw, lw + 1.2 + aa, g.y);
        float line = max(lx, ly);

        vec2 node = (floor(pixelCoord / spacing) + 0.5) * spacing;
        float nd = length(pixelCoord - node);
        float nodeM = 1.0 - smoothstep(1.1, 2.3 + fwidth(nd), nd);

        alpha = (line * (0.10 + 0.34 * glow) + nodeM * (0.16 + 0.42 * glow)) * vTint.a;
    } else {
        // ── соты: грани шестиугольников + лёгкая заливка у курсора ──
        float scale = spacing * 1.9;
        vec3 hc = hexCoords(pixelCoord / scale);

        float e = 0.05 + fwidth(hc.y) * 0.6;
        float border = smoothstep(e, e * 0.35, hc.z);
        float fill = clamp(hc.z * 2.0, 0.0, 1.0) * glow * 0.07;

        alpha = (border * (0.09 + 0.50 * glow) + fill) * vTint.a;
    }

    if (alpha < 0.004) {
        discard;
    }

    fragColor = vec4(vTint.rgb, alpha);
}
