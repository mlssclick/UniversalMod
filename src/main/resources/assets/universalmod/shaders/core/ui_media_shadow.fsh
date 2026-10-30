#version 150

in vec2 FragCoord;
in vec4 FragColor;
out vec4 OutColor;
uniform vec2 Size;
uniform vec2 ShapeSize;
uniform vec4 Radius;
uniform float Padding;
uniform float Sigma;
uniform float Spread;
float roundedBoxSDF(vec2 centerPosition, vec2 halfSize, vec4 radii) {
    radii = min(radii, vec4(min(halfSize.x,halfSize.y)));
    radii.xy = centerPosition.x > 0.0 ? radii.xy : radii.zw;
    radii.x = centerPosition.y > 0.0 ? radii.x : radii.y;
    vec2 q=abs(centerPosition)-halfSize+radii.x;
    return min(max(q.x,q.y),0.0)+length(max(q,0.0))-radii.x;
}
float erfApprox(float value) {
    float signValue = value < 0.0 ? -1.0 : 1.0;
    float x = abs(value);
    float t = 1.0 / (1.0 + 0.3275911 * x);
    float polynomial = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
            - 0.284496736) * t + 0.254829592) * t;
    return signValue * (1.0 - polynomial * exp(-x*x));
}
void main() {
    vec2 local = FragCoord * Size - vec2(Padding);
    vec2 centered = ShapeSize * .5 - local;
    float distance = roundedBoxSDF(centered, ShapeSize * .5, Radius) - Spread;
    float coverage = Sigma > 0.0001
            ? .5 - .5 * erfApprox(distance / (1.41421356237 * Sigma))
            : 1.0 - smoothstep(-.5, .5, distance);
    vec4 result = vec4(FragColor.rgb, FragColor.a * coverage);
    if (result.a <= .0001) discard;
    OutColor = result;
}
