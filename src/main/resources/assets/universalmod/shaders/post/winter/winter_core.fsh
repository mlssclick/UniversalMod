#version 150
in vec2 texCoord;
uniform sampler2D Sampler0;
out vec4 fragColor;
void main() {
    vec4 flake = texture(Sampler0, texCoord);
    fragColor = vec4(flake.rgb * flake.a, flake.a);
}
