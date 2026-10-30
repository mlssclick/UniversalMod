#version 150
layout(std140) uniform MoodBlurData { vec4 MoodBlurParams[2]; };
#define InSize MoodBlurParams[0].xy
#define Direction MoodBlurParams[0].zw
#define Radius MoodBlurParams[1].x
uniform sampler2D InSampler;
in vec2 texCoord;
out vec4 fragColor;
void main() {
if (Radius <=0.0) {
fragColor=texture(InSampler,texCoord);
return;
}
float sigma=max(Radius*0.333,0.5);
float twoSigmaSq=2.0*sigma*sigma;
float stepSize=Radius*0.16666667;
vec2 texel=Direction/InSize;
vec4 sum=texture(InSampler,texCoord);
float wSum=1.0;
for (int i=1;i <=6;i++) {
float d=float(i)*stepSize;
float w=exp(-(d*d)/twoSigmaSq);
vec2 off=texel*d;
sum +=(texture(InSampler,texCoord + off) + texture(InSampler,texCoord - off))*w;
wSum +=w*2.0;
}
fragColor=sum/wSum;
}