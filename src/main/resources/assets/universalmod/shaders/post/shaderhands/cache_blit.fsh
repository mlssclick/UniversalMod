#version 150

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D CacheSampler;

void main() {
    vec4 color = texture(CacheSampler, vUv);
    if (color.a <= 0.001) discard;
    fragColor = color;
}
