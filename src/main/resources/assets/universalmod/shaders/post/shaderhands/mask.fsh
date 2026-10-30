#version 150

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D BeforeDepth;
uniform sampler2D AfterDepth;

void main() {
    float depthBefore = texture(BeforeDepth, vUv).r;
    float depthAfter = texture(AfterDepth, vUv).r;
    float result = step(0.0001, depthBefore - depthAfter);
    fragColor = vec4(result, 0.0, 0.0, 1.0);
}
