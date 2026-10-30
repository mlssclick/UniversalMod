#version 150

uniform sampler2D Sampler0;
layout(std140) uniform WinterBlurData { vec4 BlurData; };
#define BlurOffset BlurData.x
#define InSampler Sampler0
in vec2 texCoord;
out vec4 fragColor;

const int MAX_TAPS = 48;

void main() {
    vec2 px = 1.0 / vec2(textureSize(InSampler, 0));
    vec2 dir = BlurOffset >= 0.0 ? vec2(px.x, 0.0) : vec2(0.0, px.y);

    float sigma = max(abs(BlurOffset) * 1.5, 0.5);
    int radius = min(MAX_TAPS, int(ceil(sigma * 2.0)));
    float twoSigmaSq = 2.0 * sigma * sigma;

    vec4 sum = texture(InSampler, texCoord);
    float weightSum = 1.0;
    for (int i = 1; i <= MAX_TAPS; i += 2) {
        if (i > radius) break;
        float fi = float(i);
        float w1 = exp(-(fi * fi) / twoSigmaSq);
        float pairWeight = w1;
        float off = fi;
        if (i + 1 <= radius) {
            float fi2 = float(i + 1);
            float w2 = exp(-(fi2 * fi2) / twoSigmaSq);
            pairWeight = w1 + w2;
            off = (fi * w1 + fi2 * w2) / pairWeight;
        }
        vec2 delta = dir * off;
        sum += pairWeight * (texture(InSampler, texCoord + delta) + texture(InSampler, texCoord - delta));
        weightSum += pairWeight * 2.0;
    }

    fragColor = sum / weightSum;
}
