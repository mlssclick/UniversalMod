layout(std140) uniform RainData {
    mat4 Proj;
    mat4 InvProj;
    mat4 WorldToView;
    mat4 ViewToWorld;
    mat4 InvViewProj;
    vec4 Cam;
    vec4 Sky;
    vec4 SunDir;
    vec4 Mode;
    vec4 Opts;
    vec4 Height;
    vec4 Viewport;
};
uniform sampler2D DepthSampler;
uniform sampler2D HeightSampler;

bool depthFar(float d) { return Viewport.z > 0.5 ? d <= 0.000001 : d >= 0.999999; }
float ndcDepth(float d) { return Viewport.z > 0.5 ? d : d * 2.0 - 1.0; }
vec3 relativeAt(vec2 uv, float d) {
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, ndcDepth(d), 1.0);
    return p.xyz / p.w;
}
float viewZ(float d) {
    vec4 p = InvProj * vec4(0.0, 0.0, ndcDepth(d), 1.0);
    return p.z / p.w;
}
float ign(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))); }
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 desat(vec3 c, float k) { return mix(c, vec3(luma(c)), k); }
vec3 haze(vec3 c, vec3 fog, float d, float density, bool sky, float skyMix) {
    return mix(c, fog, sky ? skyMix : 1.0 - exp(-max(d, 0.0) * density));
}
vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash22(i).x, hash22(i + vec2(1, 0)).x, f.x),
               mix(hash22(i + vec2(0, 1)).x, hash22(i + vec2(1, 1)).x, f.x), f.y);
}
float fbm(vec2 p) {
    float n = 0.0, a = 0.5333333;
    for (int i = 0; i < 4; i++) {
        n += a * vnoise(p);
        p = mat2(1.6, -1.2, 1.2, 1.6) * p + 13.7;
        a *= 0.5;
    }
    return n;
}
vec2 mapAt(vec2 rel) {
    vec2 local = rel - Height.xy;
    if (any(lessThan(local, vec2(0.0))) || any(greaterThanEqual(local, vec2(Height.z)))) return vec2(-10000.0);
    ivec2 world = ivec2(floor(rel + Cam.xz));
    int size = int(Height.z);
    ivec2 address = ivec2((world.x % size + size) % size, (world.y % size + size) % size);
    vec4 packed = floor(texelFetch(HeightSampler, address, 0) * 255.0 + 0.5);
    vec2 h = vec2(packed.r + packed.g * 256.0, packed.b + packed.a * 256.0);
    if (h.x < 0.5) return vec2(-10000.0);
    return h / 16.0 + Height.w - 1.0 - Cam.y;
}
float topAt(vec2 rel) { vec2 m = mapAt(rel); return max(m.x, m.y); }
vec2 skyCover(vec3 rel, vec3 normal) {
    vec2 m = mapAt(rel.xz + normal.xz * 0.02);
    float clearSolid = step(m.x, rel.y + 0.08);
    float clearLeaves = step(m.y, rel.y + 0.08);
    return vec2(clearSolid * clearLeaves, clearSolid * (1.0 - clearLeaves));
}
vec3 surfNormal(vec3 rel, vec3 ray) {
    vec2 uv = gl_FragCoord.xy / Viewport.xy;
    vec2 pixel = 1.0 / Viewport.xy;
    vec2 l = uv - vec2(pixel.x, 0), r = uv + vec2(pixel.x, 0);
    vec2 d = uv - vec2(0, pixel.y), u = uv + vec2(0, pixel.y);
    vec3 pl = relativeAt(l, texture(DepthSampler, l).r);
    vec3 pr = relativeAt(r, texture(DepthSampler, r).r);
    vec3 pd = relativeAt(d, texture(DepthSampler, d).r);
    vec3 pu = relativeAt(u, texture(DepthSampler, u).r);
    vec3 dx = length(rel - pl) < length(pr - rel) ? rel - pl : pr - rel;
    vec3 dy = length(rel - pd) < length(pu - rel) ? rel - pd : pu - rel;
    vec3 n = cross(dx, dy);
    n = dot(n, n) > 1e-12 ? normalize(n) : -ray;
    return dot(n, ray) > 0.0 ? -n : n;
}
vec3 gridCell(int i, ivec3 dimensions, vec3 size, vec3 shift, out vec3 id) {
    ivec3 cell = ivec3(i % dimensions.x, (i / dimensions.x) % dimensions.y,
                     i / (dimensions.x * dimensions.y));
    id = floor((Cam.xyz + shift) / size) + vec3(cell - dimensions / 2);
    return id * size - shift - Cam.xyz;
}
