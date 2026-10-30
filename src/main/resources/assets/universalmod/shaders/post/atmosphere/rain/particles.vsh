#version 150
#moj_import <universalmod:rain_common.glsl>
out vec2 SpriteQ;
flat out vec4 DropColor;
flat out vec4 DropSurface;
flat out vec2 DropSize;
out float ParticleViewZ;

vec3 rain_vel() { return vec3(1.3, -11.0, 0.5); }
vec3 rain_hz() {
    return mix(vec3(0.085, 0.10, 0.135), vec3(0.50, 0.54, 0.58), SunDir.w);
}
 
 
 
 
bool modeParticle(int i, float t, float lv, out vec3 P, out vec3 V, out float r, out vec4 c, out vec4 s) {
    P = vec3(0.0); V = vec3(0.0); r = 0.0; c = vec4(0.0); s = vec4(0.0);
    vec3 cs = vec3(1.9, 2.4, 1.9), id;
    vec3 base = gridCell(i >> 1, ivec3(13, 11, 13), cs, -rain_vel() * t, id);
    bool d1 = (i & 1) == 0;
     
    vec3 h = fract(sin(mod(dot(id, vec3(12.9898, 78.233, 37.719)), 6283.1853)) * vec3(43758.5453, 22578.1459, 19642.349));
    if (h.z >= (d1 ? 0.95 * lv + 0.03 : 0.8 * lv)) return false;
    vec2 p = d1 ? h.xy : 1.0 - h.xy;
    vec3 v = normalize(rain_vel()), iq = id + (d1 ? 0.31 : 0.77);
    vec3 g = fract(sin(dot(mod(iq, 289.0), vec3(12.9898, 78.233, 37.719))) * vec3(43758.5453, 22578.1459, 19642.349));
    float len = 0.45 + 0.4 * g.x;
    V = v * (0.5 * len);
    P = base + vec3(0.12 + 0.6 * p.x, 1.0, 0.12 + 0.6 * p.y) * vec3(cs.x, cs.y - 0.06, cs.z)
      + v * (((cs.y - 0.12) / -v.y - len) * g.y + 0.5 * len);
     
     
    float d = length(P), b = dot(P, v) / d, rng = clamp(11.0 * sqrt(1.0 - b * b), 5.5, 11.0);
    if (d > rng || d < 0.35) return false;
    s = vec4(P.y - topAt(P.xz), V.y, 0.0, 0.0);
    if (s.x < V.y) return false;    
     
     
    float px = d / Sky.a, w = max(0.003, px * 0.7);
    r = 0.25 * w + 0.625 * px;
    float a = (0.17 + 0.17 * g.z) * min(1.0, 0.003 / w + 0.25) * smoothstep(0.35, 1.6, d) * (1.0 - smoothstep(rng * 0.68, rng, d));
    c = vec4(mix(rain_hz(), Sky.rgb, 0.4) * 1.45 + 0.04 + Mode.z * vec3(0.8, 0.85, 1.0), 0.6) * a;
    return true;
}

void main() {
    vec2 corners[6] = vec2[](vec2(-1,-1), vec2(1,-1), vec2(1,1),
                             vec2(-1,-1), vec2(1,1), vec2(-1,1));
    vec3 P, V;
    float r;
    vec4 c, s;
    SpriteQ = vec2(0.0); DropColor = vec4(0.0); DropSurface = vec4(0.0);
    DropSize = vec2(1.0); ParticleViewZ = -1.0;
    if (!modeParticle(gl_VertexID / 6, Mode.x, Mode.y, P, V, r, c, s)) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
    vec3 p = mat3(WorldToView) * P;
    vec3 velocity = mat3(WorldToView) * V;
    float halfLength = length(velocity);
    vec3 axis = velocity / max(halfLength, 1e-5);
    vec3 side = cross(axis, normalize(-p));
    side = dot(side, side) < 1e-6 ? vec3(1,0,0) : normalize(side);
    float px = length(P) / Sky.a;
    vec2 q = corners[gl_VertexID % 6] * vec2(r + px, halfLength + px);
    vec3 point = p + side * q.x + axis * q.y;
    gl_Position = Proj * vec4(point, 1.0);
    SpriteQ = q; DropColor = c; DropSurface = s; DropSize = vec2(r, halfLength);
    ParticleViewZ = point.z;
}
