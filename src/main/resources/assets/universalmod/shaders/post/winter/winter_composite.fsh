#version 150

uniform sampler2D Sampler0;
#define InSampler Sampler0
#define Intensity 0.46
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 blurred = texture(InSampler, texCoord);
    float alpha = min(blurred.a * 1.35, 1.0);
    if (alpha <= 0.001) discard;

    vec3 glow = blurred.rgb * (0.75 + Intensity * 2.6);
    fragColor = vec4(glow, alpha);
}
