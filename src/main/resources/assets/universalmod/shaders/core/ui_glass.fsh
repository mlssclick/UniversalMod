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

float ralpha(vec2 size, vec2 coord, vec4 radius, float smoothness) {
    vec2 halfSize = max(size * 0.5, vec2(0.0001));
    vec2 pos = coord * size - halfSize;
    float dist = rdist(pos, halfSize, max(radius, vec4(0.0)));
    float aa = max(smoothness, 0.5);
    return 1.0 - smoothstep(-aa, aa, dist);
}

const vec2[4] RECT_VERTICES_COORDS = vec2[] (
    vec2(0.0, 0.0),
    vec2(0.0, 1.0),
    vec2(1.0, 1.0),
    vec2(1.0, 0.0)
);

vec2 rvertexcoord(int id) {
    return RECT_VERTICES_COORDS[id % 4];
}
in vec2 FragCoord;
uniform sampler2D Sampler0;
uniform vec4 BlurRegion;
uniform vec2 Size;
uniform vec4 Radius;
uniform float Squirt;
uniform float GlobalAlpha;
uniform float FresnelPower;
uniform vec4 FresnelColor;
uniform float BaseAlpha;
uniform float FresnelInvert;
uniform float FresnelMix;
uniform float DistortStrength;
uniform float Z;
out vec4 OutColor;

void main() {
    vec4 radius = max(Radius, vec4(0.0));
    vec4 sizeSmoothCorner = vec4(Size, 0.5, max(Squirt, 0.001));
    vec4 alphaPowerMix = vec4(GlobalAlpha, FresnelPower, BaseAlpha, FresnelMix);
    vec4 fresnelColor = FresnelColor;
    vec4 flagsDistortZ = vec4(FresnelInvert, DistortStrength, Z, 0.0);

    vec2 size = max(sizeSmoothCorner.xy, vec2(1.0));
    float smoothness = sizeSmoothCorner.z;
    float cornerSmoothness = max(sizeSmoothCorner.w, 0.001);
    float globalAlpha = clamp(alphaPowerMix.x, 0.0, 1.0);
    float fresnelPower = max(alphaPowerMix.y, 0.001);
    float baseAlpha = clamp(alphaPowerMix.z, 0.0, 1.0);
    float fresnelMix = clamp(alphaPowerMix.w, 0.0, 1.0);
    float fresnelInvert = flagsDistortZ.x;
    float distortStrength = flagsDistortZ.y;

    vec2 coord = clamp(FragCoord, vec2(0.0), vec2(1.0));
    vec2 center = size * 0.5;
    vec2 halfSize = max(center - 1.0, vec2(0.0));
    vec2 pos = center - coord * size;

    float alpha = ralpha(size, coord, radius, smoothness);
    float distToEdge = abs(rdist(pos, halfSize, radius));
    float maxDistNorm = max(min(halfSize.x, halfSize.y), 0.001);
    float edgeGradient = 1.0 - clamp(distToEdge / maxDistNorm, 0.0, 1.0);
    float fresnelBase = (fresnelInvert > 0.5) ? edgeGradient : (1.0 - edgeGradient);

    float fresnel;
    if (fresnelPower > 20.0) {
        fresnel = exp(fresnelPower * log(clamp(fresnelBase, 0.001, 1.0)));
    } else {
        fresnel = pow(clamp(fresnelBase, 0.0, 1.0), fresnelPower);
    }
    fresnel = clamp(fresnel, 0.0, 1.0);

    vec2 dir = (length(pos) > 0.001) ? normalize(-pos) : vec2(0.0);
    vec2 texCoord = clamp((gl_FragCoord.xy - BlurRegion.xy) / max(BlurRegion.zw, vec2(1.0)), vec2(0.0), vec2(1.0));
    vec4 texColor = texture(Sampler0, clamp(texCoord + dir * fresnel * distortStrength, vec2(0.0), vec2(1.0)));

    vec3 finalColor = mix(texColor.rgb, fresnelColor.rgb, fresnel * fresnelMix);
    float finalAlpha = mix(baseAlpha, fresnelColor.a, fresnel) * alpha * globalAlpha;

    if (finalAlpha < 0.001) {
        discard;
    }

    OutColor = vec4(finalColor, finalAlpha);
}
