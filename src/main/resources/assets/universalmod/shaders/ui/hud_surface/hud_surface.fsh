#version 150

#moj_import <universalmod:common.glsl>

in vec2 FragCoord;
flat in int QuadIndex;

layout(std140) uniform HudSurfaceParams {
    vec4 params[1536];
};

out vec4 OutColor;

void main() {
    int base = QuadIndex * 3;
    vec4 radii = max(params[base], vec4(0.0));
    vec4 sizeAlphaSmooth = params[base + 1];
    float darkness = clamp(params[base + 2].x, 0.0, 1.0);

    vec2 size = max(sizeAlphaSmooth.xy, vec2(1.0));
    float alpha = clamp(sizeAlphaSmooth.z, 0.0, 1.0);
    float smoothness = max(sizeAlphaSmooth.w, 0.45);
    vec2 uv = clamp(FragCoord, vec2(0.0), vec2(1.0));


    vec2 center = size * 0.5;
    float dist = rdist(center - uv * size, max(center - 1.0, vec2(0.0)), radii);
    float mask = 1.0 - smoothstep(1.0 - smoothness, 1.0, dist);
    if (mask <= 0.001) discard;

    float topLift = 1.0 - smoothstep(0.0, 0.32, uv.y);
    float sideFalloff = 1.0 - abs(uv.x * 2.0 - 1.0);

    vec2 bloomDelta = (uv - vec2(0.52, -0.22)) * vec2(0.95, 1.0);
    float bloomD2 = dot(bloomDelta, bloomDelta);
    float violetBloom = 1.0 - smoothstep(0.05, 0.92, bloomD2);
    violetBloom *= violetBloom;

    float lowerShade = smoothstep(0.28, 1.0, uv.y);
    float edge = mask * smoothstep(-1.20, 0.16, dist);
    float topEdge = edge * (1.0 - smoothstep(0.0, 0.23, uv.y));

    vec3 deep = vec3(0.0065, 0.0068, 0.0090);
    vec3 lifted = vec3(0.0180, 0.0185, 0.0230);
    vec3 color = mix(lifted, deep, darkness);
    color += vec3(0.018, 0.018, 0.024) * topLift * (0.45 + 0.55 * sideFalloff);
    color += vec3(0.080, 0.058, 0.175) * violetBloom * 0.085;
    color *= 1.0 - lowerShade * 0.045;
    color += vec3(0.105, 0.105, 0.128) * edge * 0.070;
    color += vec3(0.255, 0.220, 0.520) * topEdge * 0.036;

    OutColor = vec4(clamp(color, vec3(0.0), vec3(1.0)), alpha * mask * 0.995);
}
