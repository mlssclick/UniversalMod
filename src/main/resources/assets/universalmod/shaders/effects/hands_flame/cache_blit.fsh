#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D CacheSampler;

void main() {
    vec4 color = texture(CacheSampler, texCoord);
    if (color.a <= 0.001) {
        discard;
    }
    fragColor = color;
}
