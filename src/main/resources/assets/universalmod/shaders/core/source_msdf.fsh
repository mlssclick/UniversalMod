#version 150
uniform sampler2D Sampler0;uniform float DistanceRange;uniform vec4 ColorModulator;in vec2 TexCoord;in vec4 VertexColor;out vec4 OutColor;
float median(float r,float g,float b){return max(min(r,g),min(max(r,g),b));}
float screenPxRange(){vec2 atlasSize=vec2(textureSize(Sampler0,0));vec2 unitRange=vec2(DistanceRange)/atlasSize;vec2 screenTexSize=vec2(1.0)/fwidth(TexCoord);return max(.5*dot(unitRange,screenTexSize),1.0);}
float msdfAlpha(vec2 uv,float pxRange){vec3 msd=texture(Sampler0,uv).rgb;float sd=median(msd.r,msd.g,msd.b);return clamp(pxRange*(sd-.5)+.5,0,1);}
void main(){float pxRange=screenPxRange();vec2 dx=dFdx(TexCoord)*.22;vec2 dy=dFdy(TexCoord)*.22;float alpha=msdfAlpha(TexCoord,pxRange)*.62;alpha+=msdfAlpha(TexCoord+dx,pxRange)*.095;alpha+=msdfAlpha(TexCoord-dx,pxRange)*.095;alpha+=msdfAlpha(TexCoord+dy,pxRange)*.095;alpha+=msdfAlpha(TexCoord-dy,pxRange)*.095;alpha=clamp((alpha-.5)*1.34+.5,0,1)*VertexColor.a;if(alpha<.004)discard;OutColor=vec4(VertexColor.rgb,alpha)*ColorModulator;}
