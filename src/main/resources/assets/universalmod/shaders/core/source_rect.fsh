#version 150
in vec2 FragCoord;
uniform vec2 Size;uniform vec4 Radius;uniform float Smoothness;
uniform vec4 TopLeft;uniform vec4 TopRight;uniform vec4 BottomRight;uniform vec4 BottomLeft;
uniform float VerticalSplit;uniform vec4 SplitColor;
out vec4 OutColor;
float rdist(vec2 pos,vec2 size,vec4 radius){float cr;if(pos.x>0.0)cr=(pos.y>0.0)?radius.x:radius.w;else cr=(pos.y>0.0)?radius.y:radius.z;vec2 v=abs(pos)-size+cr;return min(max(v.x,v.y),0.0)+length(max(v,0.0))-cr;}
float ralpha(vec2 size,vec2 coord,vec4 radius,float smoothness){vec2 center=size*.5;float feather=max(smoothness,.001);float dist=rdist(center-coord*size,max(center-1.0,vec2(0.0)),max(radius,vec4(0.0)));return 1.0-smoothstep(1.0-feather,1.0,dist);}
vec4 rectangleColor(vec2 coord,vec4 tl,vec4 tr,vec4 br,vec4 bl){vec4 top=mix(tl,tr,coord.x);vec4 bottom=mix(bl,br,coord.x);return mix(top,bottom,coord.y);}
void main(){vec2 coord=clamp(FragCoord,vec2(0.0),vec2(1.0));vec2 size=max(Size,vec2(1.0));float alpha=ralpha(size,coord,max(Radius,vec4(0.0)),Smoothness);vec4 color=rectangleColor(coord,TopLeft,TopRight,BottomRight,BottomLeft);if(VerticalSplit>=0.0&&coord.y*size.y<VerticalSplit)color=SplitColor;color.a*=alpha;if(color.a<=.001)discard;OutColor=color;}
