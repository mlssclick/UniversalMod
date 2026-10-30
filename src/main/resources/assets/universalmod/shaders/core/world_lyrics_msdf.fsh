#version 150

uniform vec4 ColorModulator;

uniform sampler2D Sampler0;
in vec2 TexCoord;
in vec4 VertexColor;
out vec4 OutColor;

float median3(vec3 rgb) {
    return max(min(rgb.r, rgb.g), min(max(rgb.r, rgb.g), rgb.b));
}

void main() {
    vec3 sampleRgb = texture(Sampler0, TexCoord).rgb;
    float signedDistance = median3(sampleRgb) - 0.5;

    vec2 uvFwidth = max(fwidth(TexCoord), vec2(0.0000001));
    vec2 atlasSize = vec2(textureSize(Sampler0, 0));
    float pxRange = max(0.5 * dot(vec2(12.0) / (atlasSize * uvFwidth), vec2(1.0)), 1.0);
    float coverage = smoothstep(-0.48, 0.48, signedDistance * pxRange);
    float opacity = coverage * VertexColor.a;
    if (opacity <= 0.003) discard;
    OutColor = vec4(VertexColor.rgb, opacity) * ColorModulator;
}
