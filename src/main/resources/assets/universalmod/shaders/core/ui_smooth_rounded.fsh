#version 150
in vec2 FragCoord;
in vec4 LinearColor;
uniform vec2 Size;
uniform float Radius;
out vec4 OutColor;
const float SQUIRCLE_N = 4.0;

float signedRoundedBox(vec2 position, vec2 halfSize, float signedRadius) {
    float r = abs(signedRadius);
    vec2 q = abs(position) - halfSize + r;
    vec2 qc = max(q, vec2(0.0));
    float corner = signedRadius < 0.0
        ? pow(pow(qc.x, SQUIRCLE_N) + pow(qc.y, SQUIRCLE_N), 1.0 / SQUIRCLE_N)
        : length(qc);
    return min(max(q.x, q.y), 0.0) + corner - r;
}
vec3 linearToSrgb(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}
void main() {
    vec2 halfSize = max(Size * 0.5, vec2(0.0001));
    vec2 pos = FragCoord * Size - halfSize;
    float d = signedRoundedBox(pos, halfSize, Radius);
    float aa = max(fwidth(d) * 0.75, 0.00001);
    float alpha = 1.0 - smoothstep(-aa, aa, d);
    OutColor = vec4(linearToSrgb(LinearColor.rgb), LinearColor.a * alpha);
    if (OutColor.a <= 0.0005) discard;
}
