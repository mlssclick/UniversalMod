#version 150
uniform sampler2D Sampler0;uniform vec4 SourceRect;uniform vec4 HalfPixel;uniform vec4 FallbackColor;in vec2 TexCoord;out vec4 OutColor;
vec4 sampleSource(vec2 coord){vec4 color=texture(Sampler0,coord);float luminance=dot(color.rgb,vec3(.2126,.7152,.0722));if(color.a<.001&&luminance<.003)return vec4(FallbackColor.rgb,1);return color;}
void main(){vec2 sourceCoord=SourceRect.xy+TexCoord*SourceRect.zw;vec4 sum=sampleSource(sourceCoord)*4;sum+=sampleSource(sourceCoord-HalfPixel.xy);sum+=sampleSource(sourceCoord+HalfPixel.xy);sum+=sampleSource(sourceCoord+vec2(HalfPixel.x,-HalfPixel.y));sum+=sampleSource(sourceCoord-vec2(HalfPixel.x,-HalfPixel.y));OutColor=vec4((sum/8).rgb,1);}
