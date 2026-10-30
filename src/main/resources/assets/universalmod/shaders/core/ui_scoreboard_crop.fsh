#version 150
uniform sampler2D Sampler0;
uniform vec4 SourceRect;
in vec2 TexCoord;
out vec4 OutColor;
void main() {
    OutColor = texture(Sampler0, SourceRect.xy + TexCoord * SourceRect.zw);
}
