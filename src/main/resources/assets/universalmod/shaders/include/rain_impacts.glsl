 
vec2 rainImpactHash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    q += dot(q, q.yzx + 33.33);
    return fract((q.xx + q.yz) * q.zy);
}
vec2 rainImpactGradient(vec2 p, float t) {
    vec2 gradient = vec2(0.0);
    for (int k = 0; k < 3; k++) {
        vec2 q = p * 2.2 + vec2(float(k) * 0.37, float(k) * 0.71);
        vec2 cell = floor(q);
        vec2 h = rainImpactHash(cell + float(k) * 13.1);
        float phase = fract(t * (0.75 + 0.3 * h.y) + h.x);
        vec2 offset = q - cell - (0.25 + 0.5 * h);
        float radius = length(offset);
        float x = (radius - phase * 0.42) * 38.0;
        float wave = sin(x) * exp(-x * x * 0.12) * (1.0 - phase) * (1.0 - phase);
        gradient += offset / max(radius, 1.0e-3) * wave;
    }
    return gradient;
}
