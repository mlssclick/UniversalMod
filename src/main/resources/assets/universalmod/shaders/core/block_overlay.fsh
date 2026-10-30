#version 150
uniform vec2 ScreenSize;
uniform float Time;
uniform float Mode;
uniform vec4 Tint;
in vec4 vColor;
in vec3 vPos;
out vec4 fragColor;

#define NUM_OCTAVES 5
float randomNebula(vec2 pos) {
    return fract(sin(dot(pos.xy, vec2(13.9898, 78.233))) * 43758.5453123);
}
float noiseNebula(vec2 pos) {
    vec2 i = floor(pos); vec2 f = fract(pos);
    float a = randomNebula(i + vec2(0.0, 0.0));
    float b = randomNebula(i + vec2(1.0, 0.0));
    float c = randomNebula(i + vec2(0.0, 1.0));
    float d = randomNebula(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}
float fbmNebula(vec2 pos, float t) {
    float v = 0.0; float a = 0.5; vec2 shift = vec2(100.0);
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < NUM_OCTAVES; i++) {
        float dir = mod(float(i), 2.0) > 0.5 ? 1.0 : -1.0;
        v += a * noiseNebula(pos + dir * t * 0.3);
        pos = rot * pos * 2.0 + shift; a *= 0.5;
    }
    return v;
}
vec3 nebulaFx(vec2 fragCoord, vec2 screenSize, float time, vec3 baseColor) {
    vec2 uv = fragCoord / screenSize; vec2 p = (uv * 2.0 - 1.0) * 3.0; float t = time;
    vec2 q = vec2(fbmNebula(p, t * 0.8), fbmNebula(p + vec2(1.0), t * 0.6));
    vec2 r = vec2(fbmNebula(p + q + vec2(1.7, 1.2), t * 0.7), fbmNebula(p + q + vec2(8.3, 2.8), t * 0.9));
    float f = fbmNebula(p + r, t);
    vec3 color1 = baseColor * 1.4; vec3 color2 = baseColor * 0.5;
    vec3 color3 = vec3(baseColor.r * 0.4, baseColor.g * 0.3, baseColor.b * 1.4);
    vec3 color = mix(color1, color2, clamp((f * f) * 4.0, 0.0, 1.0));
    color = mix(color, color1, clamp(length(q), 0.0, 1.0));
    color = mix(color, color3, clamp(length(r.x), 0.0, 1.0));
    color = (f * f * f + 0.6 * f * f + 0.5 * f) * color;
    return clamp(color * 1.8, 0.0, 1.0);
}

const vec3 cobEps = vec3(0.001, 0.0, 0.0);
const int cobIter = 64;
const float cobSq = 0.70710678;
float cobC(vec3 p) {
    vec3 q = abs(mod(p + vec3(cos(p.z * 0.5), cos(p.x * 0.5), cos(p.y * 0.5)), 2.0) - 1.0);
    float a = q.x + q.y + q.z - min(min(q.x, q.y), q.z) - max(max(q.x, q.y), q.z);
    q = vec3(p.x + p.y, p.y + p.z, p.z + p.x) * cobSq;
    q = abs(mod(q, 2.0) - 1.0);
    float b = q.x + q.y + q.z - min(min(q.x, q.y), q.z) - max(max(q.x, q.y), q.z);
    return min(a, b);
}
vec3 cobN(vec3 p) {
    float o = cobC(p);
    return normalize(o - vec3(cobC(p - cobEps), cobC(p - cobEps.zxy), cobC(p - cobEps.yzx)));
}
vec3 cobwebFx(vec2 fragCoord, vec2 screenSize, float time, vec3 baseColor) {
    float aspect = screenSize.x / screenSize.y;
    vec2 p = fragCoord / screenSize * 2.0 - 1.0;
    vec2 m = vec2(sin(time * 0.3) * 0.3, cos(time * 0.2) * 0.3);
    p.x *= aspect; m.x *= aspect;
    vec3 o = vec3(0.0, 0.0, time * 0.5); vec3 s = vec3(m, 0.0); vec3 b = vec3(0.0);
    vec3 d = vec3(p, 1.0) / 32.0; vec3 t = vec3(0.5);
    for (int i = 0; i < cobIter; ++i) { float h = cobC(b + s + o); b += h * 10.0 * d; t += h; }
    t /= float(cobIter); vec3 a = cobN(b + s + o); float x = dot(a, t);
    vec3 diffuse = baseColor; t = (t + pow(x, 4.0)) * (1.0 - t * 0.01) * diffuse; t *= b.z * 0.125;
    vec2 vig = p * 0.43; vig.y *= aspect; float vigAmount = 1.0 - length(vig) * 0.5;
    vec3 color = vec3(t * 3.0) * vigAmount; color = mix(color, color * diffuse * 1.5, 0.35);
    return clamp(color, 0.0, 1.0);
}

float plasmaField(vec2 uv, float t) {
    float v = 0.0;
    v += sin(uv.x * 10.0 + t); v += sin((uv.y * 10.0 + t) * 0.5);
    v += sin((uv.x * 10.0 + uv.y * 10.0 + t) * 0.5);
    vec2 c = uv * 10.0 - vec2(5.0); v += sin(length(c) + t);
    c = uv * 10.0 - vec2(5.0 + sin(t * 0.5) * 3.0, 5.0 + cos(t * 0.7) * 3.0);
    v += sin(length(c) + t * 1.5); return v * 0.2;
}
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}
vec3 plasmaFx(vec2 fragCoord, vec2 screenSize, float time, vec3 baseColor) {
    vec2 uv = fragCoord / screenSize; float t = time * 0.8; float p = plasmaField(uv, t);
    float hue = p + time * 0.1; vec3 rainbow = hsv2rgb(vec3(hue, 0.8, 1.0));
    float pulse = 0.8 + sin(time * 2.0 + p * 5.0) * 0.2;
    vec3 color = rainbow * pulse; color = mix(color, color * baseColor * 2.0, 0.55);
    color += baseColor * pow(abs(sin(p * 3.14159 * 2.0)), 2.0) * 0.25;
    return clamp(color, 0.0, 1.0);
}

#define starIterations 14
#define starFormuparam2 0.79
#define starVolsteps 5
#define starStepsize 0.390
#define starZoom 0.900
#define starTile 0.850
#define starBrightness 0.003
#define starDistfading 0.560
#define starSaturation 0.800
#define starTransverseSpeed 1.8
#define starCloud 0.011
float starField(in vec3 p, float time) {
    float strength = 7.0 + 0.03 * log(1.0e-6 + fract(sin(time) * 4373.11));
    float accum = 0.0; float prev = 0.0; float tw = 0.0;
    float mag = dot(p, p);
    p = abs(p) / mag + vec3(-0.5, -0.8 + 0.1 * sin(time * 0.7 + 2.0), -1.1 + 0.3 * cos(time * 0.3));
    float w = exp(0.0); accum += w * exp(-strength * pow(abs(mag - prev), 2.3)); tw += w; prev = mag;
    return max(0.0, 5.0 * accum / tw - 0.7);
}
vec3 starfieldFx(vec2 fragCoord, vec2 screenSize, float rawTime, vec3 baseColor) {
    float time = rawTime * 0.4; vec2 uv2 = fragCoord / screenSize; vec2 uvs = uv2 * 2.0 - 1.0;
    float speed = 0.01 * cos(time * 0.02 + 3.1415926 / 4.0); float formuparam = starFormuparam2;
    vec2 uv = uvs; float a_xz = 0.9; float a_yz = -0.6; float a_xy = 0.9 + time * 0.08;
    mat2 rot_xz = mat2(cos(a_xz), sin(a_xz), -sin(a_xz), cos(a_xz));
    mat2 rot_yz = mat2(cos(a_yz), sin(a_yz), -sin(a_yz), cos(a_yz));
    mat2 rot_xy = mat2(cos(a_xy), sin(a_xy), -sin(a_xy), cos(a_xy));
    vec3 dir = vec3(uv * starZoom, 1.0); vec3 from = vec3(0.0); vec3 forward = vec3(0.0, 0.0, 1.0);
    from.x += starTransverseSpeed * cos(0.01 * time) + 0.001 * time;
    from.y += starTransverseSpeed * sin(0.01 * time) + 0.001 * time; from.z += 0.003 * time;
    dir.xy *= rot_xy; forward.xy *= rot_xy; dir.xz *= rot_xz; forward.xz *= rot_xz; dir.yz *= rot_yz; forward.yz *= rot_yz;
    from.xy *= -rot_xy; from.xz *= rot_xz; from.yz *= rot_yz;
    float zooom = (time - 3311.0) * speed; from += forward * zooom;
    float sampleShift = mod(zooom, starStepsize); float zoffset = -sampleShift; sampleShift /= starStepsize;
    float s = 0.24; float s3 = s + starStepsize / 2.0; vec3 v = vec3(0.0); float t3 = 0.0; vec3 backCol2 = vec3(0.0);
    for (int r = 0; r < starVolsteps; r++) {
        vec3 p2 = from + (s + zoffset) * dir; vec3 p3 = from + (s3 + zoffset) * dir;
        p2 = abs(vec3(starTile) - mod(p2, vec3(starTile * 2.0))); p3 = abs(vec3(starTile) - mod(p3, vec3(starTile * 2.0)));
        t3 = starField(p3, time); float pa = 0.0; float a = 0.0;
        for (int i = 0; i < starIterations; i++) { p2 = abs(p2) / dot(p2, p2) - formuparam; float d = abs(length(p2) - pa); a += i > 7 ? min(12.0, d) : d; pa = length(p2); }
        a *= a * a; float s1 = s + zoffset; float fade = pow(starDistfading, max(0.0, float(r) - sampleShift)); v += fade;
        if (r == 0) fade *= (1.0 - sampleShift); if (r == starVolsteps - 1) fade *= sampleShift;
        v += vec3(s1, s1 * s1, s1 * s1 * s1 * s1) * a * starBrightness * fade;
        backCol2 += vec3(1.15 * t3 * t3, 0.35 * t3 * t3 * t3, 1.7 * t3) * fade * 0.1;
        s += starStepsize; s3 += starStepsize;
    }
    v = mix(vec3(length(v)), v, starSaturation); vec4 forCol2 = vec4(v * 0.01, 1.0);
    backCol2 *= starCloud; backCol2.r *= 1.25; backCol2.g *= 0.35; backCol2.b *= 1.75;
    backCol2.bg = mix(backCol2.gb, backCol2.bg, 0.5 * (cos(time * 0.01) + 1.0));
    vec4 result = forCol2 + vec4(backCol2, 1.0); result.rgb = mix(result.rgb, result.rgb * baseColor * 2.0, 0.1); result.rgb *= 0.8;
    return clamp(result.rgb, 0.0, 1.0);
}

void main() {
    vec2 size = max(ScreenSize, vec2(1.0));
    vec3 color;
    if (Mode < 1.5) color = nebulaFx(gl_FragCoord.xy, size, Time, Tint.rgb);
    else if (Mode < 2.5) color = cobwebFx(gl_FragCoord.xy, size, Time, Tint.rgb);
    else if (Mode < 3.5) color = plasmaFx(gl_FragCoord.xy, size, Time, Tint.rgb);
    else color = starfieldFx(gl_FragCoord.xy, size, Time, Tint.rgb);
    fragColor = vec4(color, Tint.a) * vColor;
}
