#version 150
#define RAIN_FRAGMENT
#moj_import <universalmod:rain_common.glsl>
in vec2 SpriteQ;
flat in vec4 DropColor;
flat in vec4 DropSurface;
flat in vec2 DropSize;
in float ParticleViewZ;
out vec4 OutColor;
float spriteCover(vec2 q, float pw, float hl) {
    float aa = max(length(vec2(dFdx(q.x), dFdy(q.x))), 1e-5);
    float across = 1.0 - smoothstep(max(0.0, pw - aa), pw + aa, abs(q.x));
    return across * (1.0 - smoothstep(hl, hl + aa, abs(q.y)));
}
 
vec4 modeSprite(vec2 q, float pw, float hl, vec4 c, vec4 s) {
    float y = clamp(q.y / max(hl, 1.0e-3), -1.0, 1.0), x = 0.5 + 0.5 * y;
    return c * (spriteCover(q, pw, hl) * smoothstep(0.0, 0.35, x) * (1.0 - smoothstep(0.8, 1.0, x)) * smoothstep(0.0, 0.3, s.x + s.y * y));
}

void main() {
    if (DropColor.a <= 0.0) discard;
    vec2 uv = gl_FragCoord.xy / Viewport.xy;
    float d = texture(DepthSampler, uv).r;
    float soft = depthFar(d) ? 1.0 : smoothstep(0.0, 0.12, ParticleViewZ - viewZ(d));
    vec4 drop = modeSprite(SpriteQ, DropSize.x, DropSize.y, DropColor, DropSurface);
    drop *= soft;
    if (drop.a <= 0.001) discard;
    OutColor = drop;
}
