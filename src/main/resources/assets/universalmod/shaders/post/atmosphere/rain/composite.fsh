#version 150
#moj_import <universalmod:rain_common.glsl>
uniform sampler2D Sampler0;
in vec2 texCoord;
out vec4 OutColor;

vec3 rain_hz() {
    return mix(vec3(0.085, 0.10, 0.135), vec3(0.50, 0.54, 0.58), SunDir.w);
}

vec2 rain_ripples(vec2 p, float t) {
    vec2 g = vec2(0.0);
    for (int k = 0; k < 3; k++) {
        vec2 q = p * 2.2 + vec2(float(k) * 0.37, float(k) * 0.71);
        vec2 c = floor(q);
        vec2 h = hash22(c + float(k) * 13.1);
        float ph = fract(t * (0.75 + 0.3 * h.y) + h.x);
        vec2 o = q - c - (0.25 + 0.5 * h);
        float r = length(o);
        float x = (r - ph * 0.42) * 38.0;
        float w = sin(x) * exp(-x * x * 0.12) * (1.0 - ph) * (1.0 - ph);
        g += o / max(r, 1.0e-3) * w;
    }
    return g;
}

vec3 rain_trace(vec3 vpos, vec3 vn, float dist, vec3 sk) {
    vec3 rd = normalize(reflect(normalize(vpos), vn));

    float face = 1.0 - smoothstep(0.45, 0.85, rd.z);
    if (face <= 0.0) return sk;
    float tw = clamp(rd.z * 4.0, 0.0, 1.0);
    float stepL = 0.1 + 0.01 * dist;
    vec3 p = vpos + vn * (0.03 + 0.002 * dist) + rd * stepL * ign(gl_FragCoord.xy);
    vec2 uv = vec2(-1.0);
    for (int i = 0; i < 22; i++) {
        vec3 a = p;
        p += rd * stepL;
        stepL *= 1.145;
        vec4 c = Proj * vec4(p, 1.0);
        if (c.w <= 0.0) break;
        vec2 nuv = c.xy / c.w * 0.5 + 0.5;
        if (nuv.x < 0.0 || nuv.y < 0.0 || nuv.x > 1.0 || nuv.y > 1.0) break;
        uv = nuv;
        float diff = viewZ(texture(DepthSampler, uv).r) - p.z;
        if (diff > 0.0 && diff < mix(stepL * 2.5 + 0.3, 0.06 + stepL * 0.5, tw)) {
            vec3 b = p;
            for (int r = 0; r < 4; r++) {
                vec3 m = (a + b) * 0.5;
                vec4 mc = Proj * vec4(m, 1.0);
                vec2 muv = mc.xy / mc.w * 0.5 + 0.5;
                if (viewZ(texture(DepthSampler, muv).r) - m.z > 0.0) b = m; else a = m;
            }
            vec4 bc = Proj * vec4(b, 1.0);
            vec2 huv = clamp(bc.xy / bc.w * 0.5 + 0.5, 0.001, 0.999);
            vec2 e = smoothstep(vec2(0.0), vec2(0.08), huv) * smoothstep(vec2(0.0), vec2(0.08), 1.0 - huv);
            float hit = e.x * e.y * (1.0 - smoothstep(14.0, 22.0, float(i))) * face;
            return mix(sk, texture(Sampler0, huv).rgb, hit);
        }
    }
    if (uv.x >= 0.0 && depthFar(texture(DepthSampler, uv).r)) return mix(sk, max(texture(Sampler0, uv).rgb, sk * 0.8), face);
    return sk;
}

vec3 rainSurface(vec3 col, vec3 vpos, vec3 rel, vec3 wn, vec2 cov, float lv, float t) {
    float dist = length(rel);
    float fade = lv * (1.0 - smoothstep(120.0, 160.0, dist));
    if (fade <= 0.001 || wn.y < -0.3) return col;
    vec2 pw = rel.xz + Cam.xz;

    vec2 m = mapAt(rel.xz);
    float tol = 0.02 + 0.0015 * dist;
    float ax = rel.y - m.x;
    float onTop = max(step(abs(ax), tol), step(abs(ax + 0.5), tol));
    float wc = abs(wn.x) > abs(wn.z) ? pw.x : pw.y;
    float onPlane = step(abs(fract(wc * 2.0 + 0.5) - 0.5), 0.004 + 0.0004 * dist);
    float flatTop = onTop * max(step(0.5, wn.y), 1.0 - onPlane);
    float open = m.x < -9000.0 ? 1.0 : max(cov.x, flatTop);
    float up = max(smoothstep(0.82, 0.96, wn.y), flatTop);
    if (up <= 0.001) return col * (1.0 - 0.12 * open * fade);

    float seep = 0.0;
    if (open < 0.5) {
        float n0 = fbm(pw * 0.9);
        float o = 0.0;
        for (int i = 0; i < 8; i++) {
            float r = (i < 4 ? 0.5 : 1.6) * (0.5 + n0);
            int k = i < 4 ? i : i - 4;
            vec2 d = k == 0 ? vec2(r, 0.0) : k == 1 ? vec2(-r, 0.0) : k == 2 ? vec2(0.0, r) : vec2(0.0, -r);
            vec2 nm = mapAt(rel.xz + d);
            o += step(nm.x, rel.y + 0.45) * step(rel.y - 0.95, nm.x) * (i < 4 ? 0.6 : 0.4);
        }
        seep = smoothstep(0.05, 0.4, o * 0.25 * (0.4 + n0));
    }

    float drip = cov.y * smoothstep(0.42, 0.62, fbm(pw * 0.9 + 11.0) * 0.75 + vnoise(pw * 3.3) * 0.25);
    float wet = up * fade * max(open, max(seep * 0.85, drip * 0.75));
    if (wet <= 0.001) return col;

    vec2 wp = pw * 0.13 + vec2(vnoise(pw * 0.21 + 3.7), vnoise(pw * 0.21 - 5.1)) * 0.9;
    float f = fbm(wp) * 0.8 + vnoise(pw * 0.7) * 0.2;
    float thr = 0.78 - 0.55 * pow(Opts.x, 1.6);
    float pm = smoothstep(thr, thr + 0.07, f);

    float puddle = pm * smoothstep(0.3, 0.95, wet);
    float damp = smoothstep(thr - 0.18, thr, f);
    float day = SunDir.w;

    float wd = wet * (0.55 + 0.45 * damp);

    col = mix(col, (col + (col - luma(col)) * 0.25) * 0.7, wd * 0.8);
    col = mix(col, col * mix(0.75, 0.6, day), puddle);

    float near = 1.0 - smoothstep(10.0, 26.0, dist);
    vec2 rp = rain_ripples(pw, t) * near * lv * Opts.y;
    vec2 ruffle = vec2(vnoise(pw * 2.3 + t * 0.6), vnoise(pw * 2.1 - t * 0.5 + 9.0)) - 0.5;
    vec2 tilt = rp * mix(0.04, 0.15, pm) + ruffle * mix(0.12, 0.03, pm);
    vec3 n = normalize(vec3(tilt.x, 1.0, tilt.y));
    vec3 vn = normalize(mat3(WorldToView) * n);

    float ndv = max(dot(-normalize(vpos), vn), 0.0);
    float fres = pow(1.0 - ndv, 5.0);
    float kp = clamp(0.45 + 0.55 * fres, 0.0, 0.95);
    float kg = fres * 0.55 * (0.4 + 0.6 * damp);
    float k = puddle * kp + max(wet - puddle, 0.0) * kg;

    vec3 sk = max(mix(Sky.rgb, rain_hz(), 0.5), vec3(0.10, 0.12, 0.16));
    vec3 refl = dist < 72.0 && k > 0.03 ? rain_trace(vpos, vn, dist, sk) : sk;
    vec3 rw = reflect(rel / max(dist, 1.0e-4), n);
    vec3 L = SunDir.y > -0.05 ? SunDir.xyz : -SunDir.xyz;
    refl += pow(max(dot(rw, L), 0.0), 300.0) * mix(vec3(0.55, 0.65, 0.85), vec3(1.0, 0.9, 0.75), day) * 1.5;
    return mix(col, refl, k);
}

vec3 rainGrade(vec3 c, float dist, bool sky, vec3 wdir) {
    c = haze(c, rain_hz(), dist, 0.011, sky, 0.7);
    c = desat(c, 0.26) * vec3(0.88, 0.92, 0.97);

    if (!sky) c = mix(c, c * c * (3.0 - 2.0 * c), 0.16);
    return max(c, 0.0) + Mode.z * vec3(0.55, 0.6, 0.75);
}

vec3 rainFog(vec3 c, vec3 rd, float dist, float t, float lv) {
    float sheet = vnoise(vec2(rd.x * 7.0 - rd.z * 5.0 + t * 0.12, rd.y * 2.5 + t * 0.6))
                + vnoise(rd.xz * 9.0 + vec2(t * 0.05, -t * 0.04)) - 1.0;
    float k = (1.0 - exp(-dist * 0.025)) * smoothstep(6.0, 35.0, dist) * (1.0 - smoothstep(0.05, 0.6, rd.y));
    k *= lv * clamp(0.32 + 0.4 * sheet, 0.0, 0.6);
    vec3 mc = rain_hz() * 1.08 + Mode.z * vec3(0.5, 0.55, 0.7);
    return mix(c, mc, k);
}

vec4 modeAux(vec3 wdir, float dist, bool sky, float lv, float t) { return vec4(0.0); }

vec3 modeComposite(vec3 scene, vec3 vpos, vec3 wdir, float dist, bool sky, float lv, float t) {
    vec3 rel = wdir * dist, col = scene;
    if (!sky) {
        vec3 wn = surfNormal(rel, wdir);
        col = rainSurface(col, vpos, rel, wn, skyCover(rel, wn), lv, t);
    }
    col = mix(scene, rainGrade(col, dist, sky, wdir), lv);
    return rainFog(col, wdir, sky ? 80.0 : dist, t, lv);
}

void main() {
    vec4 scene = texture(Sampler0, texCoord);
    float depth = texture(DepthSampler, texCoord).r;
    bool sky = depthFar(depth);
    vec3 relative;
    if (sky) {

        relative = relativeAt(texCoord, 0.5);
    } else relative = relativeAt(texCoord, depth);
    float dist = max(length(relative), 1e-4);
    vec3 wdir = relative / dist;
    vec3 vpos = mat3(WorldToView) * relative;
    vec3 col = modeComposite(scene.rgb, vpos, wdir, dist, sky, Mode.y, Mode.x);
    OutColor = vec4(max(col, vec3(0.0)), scene.a);
}
