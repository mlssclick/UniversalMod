#version 150

float rdist(vec2 pos, vec2 halfSize, vec4 radius) {
    
    
    float cornerRadius;
    if (pos.x < 0.0) {
        cornerRadius = pos.y < 0.0 ? radius.x : radius.w;
    } else {
        cornerRadius = pos.y < 0.0 ? radius.y : radius.z;
    }
    cornerRadius = max(cornerRadius, 0.0);
    vec2 q = abs(pos) - halfSize + cornerRadius;
    float qMax = max(q.x, q.y);
    vec2 qClamped = max(q, vec2(0.0));
    return min(qMax, 0.0) + length(qClamped) - cornerRadius;
}



in vec2 FragCoord;
uniform vec2 Size;
uniform vec4 Radius;
uniform float Smoothness;
uniform vec4 TopLeft;
uniform vec4 TopRight;
uniform vec4 BottomRight;
uniform vec4 BottomLeft;
uniform float VerticalSplit;
uniform vec4 SplitColor;





out vec4 OutColor;

float roundedAlpha(vec2 coord, vec2 size, vec4 radius, float smoothness) {
    vec2 halfSize = max(size * 0.5, vec2(0.0001));
    vec2 pos = coord * size - halfSize;
    float d = rdist(pos, halfSize, max(radius, vec4(0.0)));
    float aa = max(fwidth(d) * 0.75 * max(smoothness, 0.5), 0.0001);
    return 1.0 - smoothstep(-aa, aa, d);
}

vec4 rectangleColor(vec2 coord, vec4 topLeft, vec4 topRight, vec4 bottomRight, vec4 bottomLeft) {
    vec4 top = mix(topLeft, topRight, coord.x);
    vec4 bottom = mix(bottomLeft, bottomRight, coord.x);
    return mix(top, bottom, coord.y);
}

void main() {
    vec4 radius = Radius;
    vec4 sizeSmoothSplit = vec4(Size, Smoothness, VerticalSplit);
    vec4 topLeft = TopLeft;
    vec4 topRight = TopRight;
    vec4 bottomRight = BottomRight;
    vec4 bottomLeft = BottomLeft;
    vec4 splitColor = SplitColor;

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 size = max(sizeSmoothSplit.xy, vec2(1.0));
    float alpha = roundedAlpha(coord, size, max(radius, vec4(0.0)), sizeSmoothSplit.z);
    vec4 color = rectangleColor(coord, topLeft, topRight, bottomRight, bottomLeft);

    float splitY = sizeSmoothSplit.w;
    if (splitY >= 0.0 && coord.y * size.y < splitY) {
        color = splitColor;
    }

    color.a *= alpha;
    if (color.a <= 0.001) {
        discard;
    }

    OutColor = color;
}
