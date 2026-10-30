#version 150

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D SourceSampler;

layout(std140) uniform HandsBlurData {
    vec4 TexelAndDirection;
    vec4 RadiusData;
};

float gaussian(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma));
}

void main() {
    vec2 texel = TexelAndDirection.xy;
    vec2 direction = TexelAndDirection.zw;
    float radius = clamp(RadiusData.x, 1.0, 24.0);
    float sigma = max(radius * 0.5, 0.5);
    float sum = texture(SourceSampler, vUv).a;
    float weightSum = 1.0;

    for (int i = 1; i <= 23; i += 2) {
        float fi = float(i);
        if (fi > radius) break;
        float w0 = gaussian(fi, sigma);
        float nextIndex = fi + 1.0;
        float w1 = nextIndex <= radius ? gaussian(nextIndex, sigma) : 0.0;
        float pairWeight = w0 + w1;
        float sampleOffset = fi + (pairWeight > 0.0 ? w1 / pairWeight : 0.0);
        vec2 offset = texel * direction * sampleOffset;
        sum += texture(SourceSampler, vUv + offset).a * pairWeight;
        sum += texture(SourceSampler, vUv - offset).a * pairWeight;
        weightSum += pairWeight * 2.0;
    }

    float blurred = sum / max(weightSum, 0.0001);
    fragColor = vec4(blurred, blurred, blurred, blurred);
}
