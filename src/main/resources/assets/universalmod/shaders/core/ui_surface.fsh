#version 150
in vec2 FragCoord;
uniform vec2 Size;
uniform vec4 Radius;
uniform float GlobalAlpha;
uniform float Smoothness;
uniform float AccentStrength;
uniform vec4 BaseColor;
uniform vec4 AccentColor;
out vec4 OutColor;
float rdist(vec2 pos,vec2 size,vec4 radius){float cr;if(pos.x>0.0)cr=(pos.y>0.0)?radius.x:radius.w;else cr=(pos.y>0.0)?radius.y:radius.z;vec2 v=abs(pos)-size+cr;return min(max(v.x,v.y),0.0)+length(max(v,0.0))-cr;}
float hash12(vec2 p){vec3 p3=fract(vec3(p.xyx)*0.1031);p3+=dot(p3,p3.yzx+33.33);return fract((p3.x+p3.y)*p3.z);}
void main(){
 vec4 radius=max(Radius,vec4(0.0)); vec2 size=max(Size,vec2(1.0)); float alpha=clamp(GlobalAlpha,0.0,1.0); float smoothness=max(Smoothness,0.45); float accentStrength=clamp(AccentStrength,0.0,1.0);
 vec2 localPx=FragCoord*size; vec2 center=size*0.5; vec2 halfSize=max(center-0.75,vec2(0.0)); float distanceField=rdist(center-localPx,halfSize,radius); float mask=1.0-smoothstep(1.0-smoothness,1.0,distanceField); if(mask<=0.001||alpha<=0.001)discard;
 vec2 uv=clamp(FragCoord,0.0,1.0); float topSheen=exp(-uv.y*15.0); float leftSheen=exp(-uv.x*18.0)*(1.0-uv.y*0.55); float bottomShade=smoothstep(0.35,1.0,uv.y);
 vec3 baseColor=clamp(BaseColor.rgb,0.0,1.0); vec3 accent=clamp(AccentColor.rgb,0.0,1.0); vec3 col=baseColor; col+=accent*(0.010*topSheen+0.005*leftSheen)*accentStrength; col*=1.0-0.025*bottomShade;
 float grain=hash12(floor(localPx))-0.5; col+=grain*0.0025; float border=1.0-smoothstep(0.0,0.72,abs(distanceField)); vec3 borderColor=mix(vec3(0.25),accent,0.18*accentStrength); col=mix(col,borderColor,border*(0.055+0.045*accentStrength));
 OutColor=vec4(clamp(col,0.0,1.0),alpha*mask*BaseColor.a);
}
