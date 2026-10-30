#version 150
layout(std140) uniform MediaShadowData { vec4 MediaShadowParams[4]; };
in vec2 texCoord;
out vec4 OutColor;
float erfApprox(float value){
    float signValue=value<0.0?-1.0:1.0;
    float x=abs(value);float t=1.0/(1.0+0.3275911*x);
    float poly=(((((1.061405429*t-1.453152027)*t)+1.421413741)*t-0.284496736)*t+0.254829592)*t;
    return signValue*(1.0-poly*exp(-x*x));
}
float roundedBoxSDF(vec2 p,vec2 h,float r){
    vec2 q=abs(p)-h+vec2(r);
    return min(max(q.x,q.y),0.0)+length(max(q,0.0))-r;
}
void main(){
    vec2 resolution=MediaShadowParams[0].xy;
    vec2 origin=MediaShadowParams[0].zw;
    float side=MediaShadowParams[1].x;
    float radius=MediaShadowParams[1].y;
    float sigma=MediaShadowParams[1].z;
    float alpha=MediaShadowParams[1].w;
    vec2 point=vec2(texCoord.x*resolution.x,(1.0-texCoord.y)*resolution.y);
    vec2 mid=origin+vec2(side*0.5);
    float distance=roundedBoxSDF(point-mid,vec2(side*0.5),radius);
    float coverage=0.5-0.5*erfApprox(distance/(1.41421356237*max(0.5,sigma)));
    float fade=clamp(coverage*alpha,0.0,1.0);
    if(fade<0.0001)discard;
    OutColor=vec4(0.0,0.0,0.0,fade);
}
