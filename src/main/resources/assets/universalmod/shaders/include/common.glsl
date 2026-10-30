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
