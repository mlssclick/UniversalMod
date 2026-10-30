#version 150

#moj_import <universalmod:common.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform GuiSurfaceParams {
    vec4 params[3072];
};

out vec4 OutColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    int base = QuadIndex * 4;
    vec4 radius = max(params[base], vec4(0.0));
    vec4 sizeAlphaSmooth = params[base + 1];
    vec4 baseColorParam = params[base + 2];
    vec4 accentColorParam = params[base + 3];

    vec2 size = max(sizeAlphaSmooth.xy, vec2(1.0));
    float alpha = clamp(sizeAlphaSmooth.z, 0.0, 1.0);
    float smoothness = max(sizeAlphaSmooth.w, 0.45);
    float accentStrength = clamp(baseColorParam.a, 0.0, 1.0);
    vec3 baseColor = clamp(baseColorParam.rgb, 0.0, 1.0);
    vec3 accent = clamp(accentColorParam.rgb, 0.0, 1.0);

    vec2 localPx = FragCoord * size;
    vec2 center = size * 0.5;
    vec2 halfSize = max(center - 0.75, vec2(0.0));
    float distanceField = rdist(center - localPx, halfSize, radius);
    float mask = 1.0 - smoothstep(1.0 - smoothness, 1.0, distanceField);
    if (mask <= 0.001 || alpha <= 0.001) discard;

    vec2 uv = clamp(FragCoord, 0.0, 1.0);


    float topSheen = exp(-uv.y * 15.0);
    float leftSheen = exp(-uv.x * 18.0) * (1.0 - uv.y * 0.55);
    float bottomShade = smoothstep(0.35, 1.0, uv.y);

    vec3 col = baseColor;
    col += accent * (0.010 * topSheen + 0.005 * leftSheen) * accentStrength;
    col *= 1.0 - 0.025 * bottomShade;


    float grain = hash12(floor(localPx)) - 0.5;
    col += grain * 0.0025;


    float border = 1.0 - smoothstep(0.0, 0.72, abs(distanceField));
    vec3 borderColor = mix(vec3(0.25), accent, 0.18 * accentStrength);
    col = mix(col, borderColor, border * (0.055 + 0.045 * accentStrength));

    OutColor = vec4(clamp(col, 0.0, 1.0), alpha * mask);
}
