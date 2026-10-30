#version 150
uniform sampler2D Sampler0;uniform vec2 Size;uniform vec4 Radius;uniform float Smoothness;uniform vec4 UvRect;uniform vec4 ColorModulator;in vec2 TexCoord;in vec2 FragCoord;in vec4 FragColor;out vec4 OutColor;
float rdist(vec2 pos,vec2 size,vec4 radius){float cr;if(pos.x>0)cr=(pos.y>0)?radius.x:radius.w;else cr=(pos.y>0)?radius.y:radius.z;vec2 v=abs(pos)-size+cr;return min(max(v.x,v.y),0)+length(max(v,0))-cr;}
float ralpha(vec2 size,vec2 coord,vec4 radius,float sm){vec2 center=size*.5;float f=max(sm,.001);float d=rdist(center-coord*size,max(center-1,vec2(0)),max(radius,vec4(0)));return 1-smoothstep(1-f,1,d);}
void main(){vec2 coord=clamp(FragCoord,vec2(0),vec2(1));float shapeAlpha=ralpha(max(Size,vec2(1)),coord,max(Radius,vec4(0)),Smoothness);vec2 sampleCoord=mix(UvRect.xy,UvRect.zw,TexCoord);vec4 color=texture(Sampler0,sampleCoord)*FragColor;color.a*=shapeAlpha;if(color.a<=.001)discard;OutColor=color*ColorModulator;}
