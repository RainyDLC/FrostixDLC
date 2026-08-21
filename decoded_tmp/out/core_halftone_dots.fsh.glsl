#version 150

// Полутоновая сетка: точки в шахматном (гексагональном) порядке, размер растёт
// к позиции курсора. Фон не закрашивается — только сами точки.

in vec2 pixelCoord;
in vec4 vParams;
in vec2 vPointer;
in vec4 vTint;

out vec4 fragColor;

void main() {
    float spacing = max(vParams.x, 1.0);
    float minRadius = vParams.y;
    float maxRadius = vParams.z;
    float reach = max(vParams.w, 1.0);

    // ряды сдвинуты на полшага — сетка выходит гексагональной, как в полутоне
    float rowHeight = spacing * 0.8660254;

    float row = floor(pixelCoord.y / rowHeight);
    float rowOffset = mod(row, 2.0) * spacing * 0.5;
    float col = floor((pixelCoord.x - rowOffset) / spacing);

    vec2 node = vec2((col + 0.5) * spacing + rowOffset, (row + 0.5) * rowHeight);

    // размер точки — от расстояния её центра до курсора
    float toPointer = length(node - vPointer);
    float weight = 1.0 - clamp(toPointer / reach, 0.0, 1.0);
    weight = weight * weight * (3.0 - 2.0 * weight);

    float radius = mix(minRadius, maxRadius, weight);

    if (radius <= 0.05) {
        discard;
    }

    float dist = length(pixelCoord - node);

    float aa = max(fwidth(dist), 0.4);
    float dot_ = 1.0 - smoothstep(radius - aa, radius + aa, dist);

    float alpha = dot_ * vTint.a;

    if (alpha < 0.004) {
        discard;
    }

    fragColor = vec4(vTint.rgb, alpha);
}
