#version 150
uniform sampler2D Sampler0;
uniform vec2 InSize;
uniform vec2 BlurDir;
uniform float Radius;
in vec2 TexCoord;
out vec4 OutColor;
void main() {
    vec2 sampleStep = (1.0 / InSize) * BlurDir;
    float actualRadius = max(round(Radius), 1.0);
    vec4 blurred = vec4(0.0);
    for (float offset = -actualRadius + 0.5; offset <= actualRadius; offset += 2.0)
        blurred += texture(Sampler0, TexCoord + sampleStep * offset);
    blurred += texture(Sampler0, TexCoord + sampleStep * actualRadius) / 2.0;
    OutColor = blurred / (actualRadius + 0.5);
}
