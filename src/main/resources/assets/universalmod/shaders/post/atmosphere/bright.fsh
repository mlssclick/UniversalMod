#version 150
layout(std140) uniform BrightData { vec4 BrightParams[2]; };
#define OutSize BrightParams[0].xy
#define Threshold BrightParams[0].z
#define Intensity BrightParams[0].w
uniform sampler2D InSampler;
in vec2 texCoord;
out vec4 fragColor;
void main() {
vec2 o=0.75/OutSize;
vec3 color=(texture(InSampler,texCoord + vec2(-o.x,-o.y)).rgb
+ texture(InSampler,texCoord + vec2( o.x,-o.y)).rgb
+ texture(InSampler,texCoord + vec2(-o.x,o.y)).rgb
+ texture(InSampler,texCoord + vec2( o.x,o.y)).rgb)*0.25;
float lum=max(color.r,max(color.g,color.b));
float knee=0.2;
float soft=clamp(lum - Threshold + knee,0.0,2.0*knee);
soft=soft*soft/(4.0*knee + 0.00001);
float contribution=max(soft,lum - Threshold);
contribution/=max(lum,0.00001);
vec3 boosted=color*contribution;
boosted*=1.0 + boosted*1.2;
fragColor=vec4(boosted*Intensity,1.0);
}