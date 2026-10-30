#version 150

in vec2 texCoord;
layout(std140) uniform WinterWeatherData {
    mat4 WeatherInverseViewProjection;
    vec4 WeatherParams[35];
};
#define InvViewProj WeatherInverseViewProjection
#define CamPos WeatherParams[1].xyz
#define Resolution WeatherParams[2].xy
#define Time WeatherParams[3].x
#define PixelWorld WeatherParams[4].x
#define SunDir WeatherParams[5].xyz
#define SunScreen WeatherParams[6].xyz
#define ViewDistance WeatherParams[7].x
#define GroundY WeatherParams[8].x
#define Kind int(WeatherParams[9].x)
#define Amount WeatherParams[10].x
#define Speed WeatherParams[11].x
#define Haze WeatherParams[12].x
#define Grade WeatherParams[13].x
#define Lights WeatherParams[14].x
#define Vignette WeatherParams[15].x
#define Rays WeatherParams[16].x
#define Shimmer WeatherParams[17].x
#define SkyMix WeatherParams[18].x
#define Chroma WeatherParams[19].x
#define Relight WeatherParams[20].x
#define Occlusion WeatherParams[21].x
#define SnowCover WeatherParams[22].x
#define SkyTop WeatherParams[23].xyz
#define SkyHorizon WeatherParams[24].xyz
#define SkySun WeatherParams[25].xyz
#define HazeColor WeatherParams[26].xyz
#define ParticleColor WeatherParams[27].xyz
#define ShadowTint WeatherParams[28].xyz
#define LightTint WeatherParams[29].xyz
#define LightDir WeatherParams[30].xyz
#define LightGain WeatherParams[31].xyz
#define GradeParams WeatherParams[32]
#define ReversedDepth int(WeatherParams[33].x)
#define ParticleOnly WeatherParams[34].x

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
float ndcDepth(float depth) { return ReversedDepth==1?depth:depth*2.0 - 1.0;}
bool depthIsFar(float depth) { return ReversedDepth==1?depth <=0.000001:depth >=0.999999;}
float farDepth() { return ReversedDepth==1?0.000001:0.999999;}
out vec4 OutColor;
const int PARTICLE_CELLS=30;
const vec2 POISSON[8]=vec2[8](
vec2(-0.326,-0.406),vec2(-0.840,-0.074),vec2(-0.696,0.457),vec2(-0.203,0.621),
vec2(0.962,-0.195),vec2(0.473,-0.480),vec2(0.519,0.767),vec2(0.185,-0.893)
);
float saturate(float v) { return clamp(v,0.0,1.0);}
vec3 saturate3(vec3 v) { return clamp(v,vec3(0.0),vec3(1.0));}
float luminance(vec3 c) { return dot(c,vec3(0.2126,0.7152,0.0722));}
float hash12(vec2 p) {
vec3 p3=fract(vec3(p.xyx)*0.1031);
p3 +=dot(p3,p3.yzx + vec3(33.33));
return fract((p3.x + p3.y)*p3.z);
}
float hash13(vec3 p3) {
p3=fract(p3*0.1031);
p3 +=dot(p3,p3.zyx + 31.32);
return fract((p3.x + p3.y)*p3.z);
}
vec3 hash33(vec3 p3) {
p3=fract(p3*vec3(0.1031,0.1030,0.0973));
p3 +=dot(p3,p3.yxz + 33.33);
return fract((p3.xxy + p3.yxx)*p3.zyx);
}
vec4 permute4(vec4 x) {
return mod(((x*34.0) + 1.0)*x,289.0);
}
vec4 taylorInvSqrt4(vec4 r) {
return 1.79284291400159 - 0.85373472095314*r;
}
float snoise(vec3 v) {
const vec2 C=vec2(1.0/6.0,1.0/3.0);
const vec4 D=vec4(0.0,0.5,1.0,2.0);
vec3 i=floor(v + dot(v,C.yyy));
vec3 x0=v - i + dot(i,C.xxx);
vec3 g=step(x0.yzx,x0.xyz);
vec3 l=1.0 - g;
vec3 i1=min(g.xyz,l.zxy);
vec3 i2=max(g.xyz,l.zxy);
vec3 x1=x0 - i1 + C.xxx;
vec3 x2=x0 - i2 + 2.0*C.xxx;
vec3 x3=x0 - 1.0 + 3.0*C.xxx;
i=mod(i,289.0);
vec4 p=permute4(permute4(permute4(
i.z + vec4(0.0,i1.z,i2.z,1.0))
+ i.y + vec4(0.0,i1.y,i2.y,1.0))
+ i.x + vec4(0.0,i1.x,i2.x,1.0));
float n_=1.0/7.0;
vec3 ns=n_*D.wyz - D.xzx;
vec4 j=p - 49.0*floor(p*ns.z*ns.z);
vec4 x_=floor(j*ns.z);
vec4 y_=floor(j - 7.0*x_);
vec4 x=x_*ns.x + ns.yyyy;
vec4 y=y_*ns.x + ns.yyyy;
vec4 h=1.0 - abs(x) - abs(y);
vec4 b0=vec4(x.xy,y.xy);
vec4 b1=vec4(x.zw,y.zw);
vec4 s0=floor(b0)*2.0 + 1.0;
vec4 s1=floor(b1)*2.0 + 1.0;
vec4 sh=-step(h,vec4(0.0));
vec4 a0=b0.xzyw + s0.xzyw*sh.xxyy;
vec4 a1=b1.xzyw + s1.xzyw*sh.zzww;
vec3 p0=vec3(a0.xy,h.x);
vec3 p1=vec3(a0.zw,h.y);
vec3 p2=vec3(a1.xy,h.z);
vec3 p3=vec3(a1.zw,h.w);
vec4 norm=taylorInvSqrt4(vec4(dot(p0,p0),dot(p1,p1),dot(p2,p2),dot(p3,p3)));
p0*=norm.x;
p1*=norm.y;
p2*=norm.z;
p3*=norm.w;
vec4 m=max(0.6 - vec4(dot(x0,x0),dot(x1,x1),dot(x2,x2),dot(x3,x3)),0.0);
m=m*m;
return 42.0*dot(m*m,vec4(dot(p0,x0),dot(p1,x1),dot(p2,x2),dot(p3,x3)));
}
float vnoise(vec2 p) {
vec2 i=floor(p);
vec2 f=p - i;
f=f*f*(3.0 - 2.0*f);
float a=hash12(i);
float b=hash12(i + vec2(1.0,0.0));
float c=hash12(i + vec2(0.0,1.0));
float d=hash12(i + vec2(1.0,1.0));
return mix(mix(a,b,f.x),mix(c,d,f.x),f.y)*2.0 - 1.0;
}
vec3 worldPosFromDepth(vec2 uv,float depth) {
vec4 clipSpace=vec4(uv*2.0 - 1.0,ndcDepth(depth),1.0);
vec4 relative=InvViewProj*clipSpace;
float invW=abs(relative.w) > 0.000001?1.0/relative.w:1.0;
return CamPos + relative.xyz*invW;
}
vec3 worldAt(vec2 uv) {
float depth=texture(Sampler1,uv).r;
return worldPosFromDepth(uv,depthIsFar(depth)?farDepth():depth);
}
float sceneDistance(vec2 uv) {
float depth=texture(Sampler1,uv).r;
if (depthIsFar(depth)) return 4000.0;
return length(worldPosFromDepth(uv,depth) - CamPos);
}
vec3 surfaceNormal(vec2 uv,vec3 pos) {
vec2 texel=1.0/max(Resolution,vec2(1.0));
bool hasRight=uv.x + texel.x < 1.0;
bool hasLeft=uv.x - texel.x > 0.0;
bool hasUp=uv.y + texel.y < 1.0;
bool hasDown=uv.y - texel.y > 0.0;
vec3 right=hasRight?worldAt(uv + vec2(texel.x,0.0)) - pos:vec3(0.0);
vec3 left=hasLeft?pos - worldAt(uv - vec2(texel.x,0.0)):vec3(0.0);
vec3 up=hasUp?worldAt(uv + vec2(0.0,texel.y)) - pos:vec3(0.0);
vec3 down=hasDown?pos - worldAt(uv - vec2(0.0,texel.y)):vec3(0.0);
vec3 dx=!hasLeft || (hasRight && dot(right,right) < dot(left,left))?right:left;
vec3 dy=!hasDown || (hasUp && dot(up,up) < dot(down,down))?up:down;
vec3 n=cross(dx,dy);
float len=length(n);
if (len <=0.0001*length(dx)*length(dy)) return vec3(0.0,1.0,0.0);
n/=len;
return dot(n,pos - CamPos) > 0.0?-n:n;
}
float ambientOcclusion(vec2 uv,vec3 pos,vec3 normal,float dist) {
float radiusPx=clamp(0.55/(dist*PixelWorld),3.0,26.0);
float angle=hash12(gl_FragCoord.xy + vec2(3.1,7.7))*6.2831853;
float ca=cos(angle);
float sa=sin(angle);
mat2 basis=mat2(ca,sa,-sa,ca);
vec2 px=vec2(radiusPx)/max(Resolution,vec2(1.0));
float occluded=0.0;
for (int i=0;i < 8;i++) {
vec2 s=clamp(uv + basis*POISSON[i]*px,vec2(0.001),vec2(0.999));
vec3 toTap=worldAt(s) - pos;
float reach=length(toTap);
float rise=dot(toTap,normal)/max(reach,0.0001);
occluded +=saturate((rise - 0.15)/0.5)*(1.0 - smoothstep(0.7,1.6,reach));
}
return occluded/8.0;
}
float driftNoise(vec2 xz) {
vec2 p=xz*2.2;
return vnoise(p)*0.65 + vnoise(p*2.3 + 7.1)*0.35;
}
float heightIntegral(vec3 ro,vec3 rd,float dist,float base,float thickness) {
float k=2.0/max(thickness,1.0);
float rdy=abs(rd.y) < 0.00002?(rd.y < 0.0?-0.00002:0.00002):rd.y;
float start=exp(-clamp(k*max(ro.y - base,0.0),0.0,60.0));
float travel=1.0 - exp(clamp(-k*rdy*dist,-60.0,60.0));
return clamp(start*travel/(k*rdy),0.0,dist);
}
float henyeyGreenstein(float mu,float g) {
float g2=g*g;
return (1.0 - g2)/pow(max(1.0 + g2 - 2.0*g*mu,0.0001),1.5);
}
float godRaysMask(vec2 uv,vec2 sunUv,float jitter) {
vec2 stepUv=(sunUv - uv)/16.0;
vec2 pos=uv + stepUv*jitter;
float sum=0.0;
float weight=1.0;
float total=0.0;
for (int i=0;i < 16;i++) {
vec2 s=clamp(pos,vec2(0.001),vec2(0.999));
sum +=(depthIsFar(texture(Sampler1,s).r)?1.0:0.0)*weight;
total +=weight;
weight*=0.925;
pos +=stepUv;
}
return sum/max(total,0.0001);
}


float solarDiscVisibility(vec2 centerUv) {
    if (any(lessThan(centerUv, vec2(0.0))) ||
        any(greaterThan(centerUv, vec2(1.0)))) return 0.0;
    vec2 texel = 1.0 / max(Resolution, vec2(1.0));
    float visible = 0.0;
    float weight = 0.0;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec2 u = centerUv + vec2(float(x), float(y)) * texel * 4.0;
            float w = 1.0 / (1.0 + 0.2 * float(x*x + y*y));
            visible += (depthIsFar(texture(Sampler1, clamp(u, vec2(0.001), vec2(0.999))).r) ? 1.0 : 0.0) * w;
            weight += w;
        }
    }
    return visible / max(weight, 0.00001);
}
vec3 sampleSceneSoft(vec2 uv,float radiusPx) {
vec3 center=texture(Sampler0,uv).rgb;
if (radiusPx < 0.5) return center;
float angle=hash12(gl_FragCoord.xy)*6.2831853;
float ca=cos(angle);
float sa=sin(angle);
mat2 basis=mat2(ca,sa,-sa,ca);
vec2 px=vec2(radiusPx)/max(Resolution,vec2(1.0));
vec3 acc=center*0.2;
for (int i=0;i < 8;i++) {
vec2 s=clamp(uv + basis*POISSON[i]*px,vec2(0.001),vec2(0.999));
acc +=texture(Sampler0,s).rgb*0.1;
}
return acc;
}
vec3 moodSky(vec3 rd,float mu) {
float up=saturate(rd.y);
float sunOn=saturate(luminance(SkySun)*4.0);
vec3 grad=mix(SkyHorizon,SkyTop,pow(up,0.5));
float band=pow(1.0 - up,3.0);
float sunNear=pow(saturate(mu*0.5 + 0.5),5.0);
grad=mix(grad,SkySun,sunOn*sunNear*band*0.5);
grad +=SkySun*henyeyGreenstein(mu,0.75)*0.012*sunOn;
float angSq=max(2.0*(1.0 - mu),0.0);
grad +=SkySun*exp(-angSq*3500.0)*1.3*sunOn;
grad=mix(grad,SkyHorizon*0.55,saturate(-rd.y)*0.85);
if (Kind==1 && rd.y > 0.0) {
vec2 p=rd.xz/(rd.y + 0.18)*1.1 + vec2(Time*0.02,Time*0.007);
float c=snoise(vec3(p,Time*0.03))*0.6 + snoise(vec3(p*2.6,5.0 - Time*0.04))*0.4;
float cloud=smoothstep(-0.35,0.55,c)*smoothstep(0.0,0.12,rd.y);
grad=mix(grad,grad*vec3(0.80,0.82,0.86),cloud*0.55);
}
return grad;
}
vec3 particles(vec3 ro,vec3 rd,float sceneDist,vec3 sun,float lit,float shaft) {
float cell,reach,radius,density,alpha,jitter;
vec3 wind;
if (Kind==1) {
cell=0.8;reach=18.0;radius=0.02;jitter=0.5;
density=0.45;alpha=0.85;wind=vec3(0.4,-1.3,0.2);
} else if (Kind==2) {
cell=0.75;reach=14.0;radius=0.016;jitter=0.4;
density=0.16;alpha=1.4;wind=vec3(0.25,0.9,0.15);
} else {
cell=0.36;reach=9.0;radius=0.008;jitter=0.6;
density=0.3;alpha=0.5;wind=vec3(0.06,0.03,0.04);
}
density*=Amount*1.6;
vec3 gust=vec3(sin(Time*0.31)*1.6 + sin(Time*0.83)*0.5,sin(Time*0.47)*0.3,
cos(Time*0.27)*1.2)*(Kind==1?1.0:0.15);
vec3 shift=wind*Time*Speed + gust*Speed;
vec3 gustDir=vec3(0.0);
float streak=0.0;
if (Kind==0) {
const float w=0.2618;
const float peak=1.8;
gustDir=normalize(vec3(0.83,0.0,0.55));
float s=sin(Time*w);
float blow=s*s*s*s;
float travel=0.375*Time - sin(2.0*w*Time)/(4.0*w) + sin(4.0*w*Time)/(32.0*w);
shift +=gustDir*(peak*travel*Speed);
gustDir -=rd*dot(gustDir,rd);
float across3=length(gustDir);
gustDir/=max(across3,0.001);
streak=peak*blow*Speed*0.01*across3;
}
float forward=pow(saturate(dot(rd,sun)),6.0);
float limit=min(reach,sceneDist + cell);
vec3 across=normalize(cross(rd,abs(rd.y) < 0.99?vec3(0.0,1.0,0.0):vec3(1.0,0.0,0.0)));
vec3 upward=cross(across,rd);
vec3 origin=(ro - shift)/cell;
vec3 c=floor(origin);
vec3 safeRd=vec3(abs(rd.x) < 0.00001?0.00001:rd.x,abs(rd.y) < 0.00001?0.00001:rd.y,
abs(rd.z) < 0.00001?0.00001:rd.z);
vec3 stepDir=sign(safeRd);
vec3 tDelta=abs(cell/safeRd);
vec3 tMax=((c + max(stepDir,vec3(0.0))) - origin)*cell/safeRd;
vec3 sum=vec3(0.0);
float t=0.0;
for (int i=0;i < PARTICLE_CELLS;++i) {
if (t > limit) break;
float h=hash13(c);
if (h < density) {
vec3 h3=hash33(c + 7.0);
vec3 center=(c + 0.5 + (h3 - 0.5)*jitter)*cell + shift;
if (Kind==2) {
center.xz +=vec2(sin(Time*1.3 + h*7.0),cos(Time*1.1 + h*5.0))*(0.25*cell);
} else {
float w=Kind==1?0.2:0.12;
center +=vec3(sin(Time*0.7 + h*6.3),cos(Time*0.5 + h*4.1),
sin(Time*0.6 + h*9.7))*(w*cell);
}
float low=Kind==2?1.0 - smoothstep(GroundY + 3.0,GroundY + 9.0,center.y):1.0;
float along=dot(center - ro,rd);
vec3 offset=ro + rd*along - center;
float d=length(offset);
if (low > 0.001 && along > 0.05 && along < sceneDist + 0.2) {
float size=radius*(Kind==0?0.5 + h3.x:(Kind==1?0.6 + h3.x*0.9:1.0));
float r=max(size,1.3*along*PixelWorld);
float pixels=r/max(along*PixelWorld,0.000001);
if (Kind==0 && streak > 0.0005) {
float run=clamp(dot(offset,gustDir),-streak,streak);
d=length(offset - gustDir*run);
}
if (Kind==1) {
float shaped=smoothstep(2.5,7.0,pixels);
vec2 o2=vec2(dot(offset,across),dot(offset,upward));
float tumble=Time*(0.6 + h3.x*0.8) + h*9.0;
o2.x/=mix(1.0,mix(0.3,1.0,abs(cos(tumble))),shaped);
float theta=atan(o2.y,o2.x);
float spin=Time*(0.4 + h3.y) + h*6.2831;
float star=mix(1.0,0.78 + 0.22*cos(6.0*(theta + spin)),shaped);
d=length(o2)/star;
}
float body;
if (Kind==2) {
body=exp(-(d*d)/(r*r))*1.2 + exp(-(d*d)/(r*r*9.0))*0.25;
} else {
float inner=mix(0.35,0.05,smoothstep(4.0,22.0,pixels));
body=1.0 - smoothstep(r*inner,r,d);
body*=mix(1.0,0.5,smoothstep(6.0,26.0,pixels));
if (Kind==0) body*=r/(r + streak*0.7);
}
float fade=1.0 - smoothstep(reach*0.3,reach,along)*0.85;
fade*=smoothstep(0.7,1.6,along)*(1.0 - smoothstep(28.0,50.0,pixels));
float occluded=1.0 - smoothstep(sceneDist - 0.4,sceneDist + 0.1,along);
vec3 shade=ParticleColor;
float bright;
if (Kind==2) {
float flicker=0.55 + 0.45*sin(Time*(6.0 + 8.0*h3.x) + h*40.0);
shade=mix(ParticleColor,vec3(1.0,0.9,0.6),h3.y*0.4);
bright=flicker*(0.7 + 0.6*h3.z);
} else if (Kind==1) {
shade=mix(ParticleColor,HazeColor,smoothstep(6.0,20.0,along)*0.5);
bright=lit*(1.0 + 1.5*forward);
} else {
float glint=0.65 + 0.35*sin(Time*(2.0 + 3.0*h3.x) + h*30.0);
bright=lit*glint*(1.0 + 3.0*forward)*shaft;
}
sum +=shade*(body*bright*alpha*fade*occluded*low);
}
}
if (tMax.x < tMax.y && tMax.x < tMax.z) {
t=tMax.x;tMax.x +=tDelta.x;c.x +=stepDir.x;
} else if (tMax.y < tMax.z) {
t=tMax.y;tMax.y +=tDelta.y;c.y +=stepDir.y;
} else {
t=tMax.z;tMax.z +=tDelta.z;c.z +=stepDir.z;
}
}
return sum;
}
float lensSpecks(vec2 p,float fill) {
vec2 id=floor(p);
vec3 h=hash33(vec3(id,5.0));
if (h.x > fill) return 0.0;
float r=0.12 + 0.24*hash12(id + 11.0);
vec2 at=0.5 + (h.yz - 0.5)*(1.0 - 2.0*r);
float d=length(p - id - at)/r;
return smoothstep(1.0,0.75,d)*(0.45 + 0.55*smoothstep(0.45,0.9,d));
}
const float GHOST_AT[4]=float[4](0.55,0.2,-0.3,-0.7);
const float GHOST_R[4]=float[4](0.045,0.08,0.035,0.11);
const float GHOST_GAIN[4]=float[4](0.35,0.15,0.28,0.12);
const vec3 GHOST_TINT[4]=vec3[4](
vec3(1.0,0.8,0.55),vec3(0.7,0.95,0.75),vec3(1.0,0.7,0.8),vec3(0.65,0.8,1.0)
);
const vec2 SUN_TAPS[5]=vec2[5](
vec2(0.0),vec2(0.02,0.02),vec2(-0.02,0.02),vec2(0.02,-0.02),vec2(-0.02,-0.02)
);
vec3 sampleScene(vec2 uv,float softPx) {
vec3 c=sampleSceneSoft(uv,softPx);
if (Chroma <=0.0) return c;
vec2 offset=uv - 0.5;
vec2 pull=offset*(0.012*Chroma*dot(offset,offset));
return vec3(texture(Sampler0,uv + pull).r,c.g,texture(Sampler0,uv - pull).b);
}
vec3 gradeColor(vec3 c) {
c*=GradeParams.x;
float l=luminance(c);
c=mix(vec3(l),c,GradeParams.z);
c=max((c - 0.5)*GradeParams.y + 0.5,0.0);
l=luminance(c);
vec3 tint=mix(ShadowTint,LightTint,smoothstep(0.08,0.85,l));
c*=tint/max(luminance(tint),0.05);
c +=ShadowTint*GradeParams.w*(1.0 - smoothstep(0.0,0.5,l));
return c;
}
vec3 screenBlend(vec3 base,vec3 light) {
return 1.0 - (1.0 - saturate3(base))*(1.0 - saturate3(light));
}
void main() {
vec2 uv=gl_FragCoord.xy/max(Resolution,vec2(1.0));
float aspect=Resolution.x/max(Resolution.y,1.0);
float depth=texture(Sampler1,uv).r;
bool isSky=depthIsFar(depth);
vec3 ro=CamPos;
vec3 farPoint=worldPosFromDepth(uv,farDepth());
vec3 rd=normalize(farPoint - ro + vec3(0.0,0.0000001,0.0));
vec3 hit=isSky?farPoint:worldPosFromDepth(uv,depth);
float skyMask=isSky?1.0:0.0;
float dist=isSky?4000.0:length(hit - ro);
float up=saturate(rd.y);
vec3 sun=normalize(SunDir);
float mu=dot(rd,sun);
float sunOn=saturate(luminance(SkySun)*4.0);
if (ParticleOnly > 0.5) {
    vec3 flakeColor = Amount > 0.001 ? particles(ro, rd, dist, sun, 0.76, 1.0) : vec3(0.0);
    float alpha = clamp(max(max(flakeColor.r, flakeColor.g), flakeColor.b), 0.0, 1.0);
    OutColor = vec4(flakeColor, alpha);
    return;
}
float thickness=Kind==2?10.0:26.0;
float integ=heightIntegral(ro,rd,min(dist,400.0),GroundY - 2.0,thickness);
float turb=1.0;
if (Kind==2) {
vec3 probe=ro + rd*clamp(dist*0.5,4.0,60.0);
vec3 drift=vec3(Time*Speed*0.6,Time*0.2,Time*0.3);
float n=snoise(probe*0.06 + drift*0.3)*0.6 + snoise(probe*0.16 - drift*0.5)*0.4;
turb=mix(0.5,1.6,saturate(n*0.5 + 0.5));
}
float sigma=Haze*0.012*turb;
float fogAmount=saturate(1.0 - exp(-sigma*integ));
fogAmount*=mix(1.0,1.0 - 0.85*pow(up,0.6),skyMask);
float farHaze=smoothstep(ViewDistance*0.55,ViewDistance*0.98,dist)*(1.0 - skyMask);
vec2 sampleUv=uv;
if (Shimmer > 0.0 && !isSky) {
float low=1.0 - smoothstep(-0.08,0.35,rd.y);
float farness=smoothstep(3.0,28.0,dist);
float n=snoise(vec3(uv.x*16.0*aspect,uv.y*16.0 - Time*Speed*0.8,Time*0.3));
vec2 shifted=uv + vec2(n*0.3,n)*(0.007*Shimmer*low*farness);
if (sceneDistance(shifted) > dist*0.7) sampleUv=shifted;
}
float softPx=5.0*saturate(fogAmount*1.4 + farHaze)*(1.0 - skyMask);
vec3 scene=sampleScene(sampleUv,softPx);
float sceneLum=luminance(scene);
if (!isSky && (Relight > 0.0 || Occlusion > 0.0 || SnowCover > 0.0)) {
vec3 normal=surfaceNormal(uv,hit);
float near=1.0 - smoothstep(ViewDistance*0.5,ViewDistance*0.9,dist);
float facing=max(dot(normal,normalize(LightDir)),0.0);
vec3 light=1.0 + Relight*near*(LightGain*facing - 0.30*(1.0 - facing));
scene*=light;
if (Occlusion > 0.0) {
float occluded=ambientOcclusion(uv,hit,normal,dist);
scene*=1.0 - Occlusion*near*occluded*0.75;
}
if (SnowCover > 0.0) {
float level=smoothstep(0.02,0.5,sceneLum);
vec3 snowWhite=mix(vec3(0.22,0.25,0.33),vec3(0.93,0.95,1.0),level);
float hC=driftNoise(hit.xz);
float hX=driftNoise(hit.xz + vec2(0.07,0.0));
float hZ=driftNoise(hit.xz + vec2(0.0,0.07));
vec3 drift=normalize(vec3((hC - hX)*4.5,1.0,(hC - hZ)*4.5));
float driftLight=0.5 + 0.5*max(dot(drift,normalize(vec3(0.35,1.0,0.25))),0.0);
driftLight=mix(driftLight,0.9,smoothstep(18.0,50.0,dist));
vec3 snow=snowWhite*mix(vec3(0.78,0.84,1.0),vec3(1.0),driftLight)
*(0.9 + 0.1*driftLight);
snow*=1.0 + Relight*0.18*near;
vec3 grain=floor(hit*14.0);
float pick=hash13(grain);
float twinkle=pow(0.5 + 0.5*sin(Time*(2.0 + pick*5.0) + pick*60.0),10.0);
snow +=vec3(0.6)*step(0.975,pick)*twinkle*level*(1.0 - smoothstep(12.0,40.0,dist));
float upFace=smoothstep(0.55,0.85,normal.y);
float cover=upFace*saturate((0.9 + 0.1*hC)*1.25 - 0.25)*SnowCover;
float sideFace=1.0 - smoothstep(0.2,0.5,abs(normal.y));
float below=1.0 - fract(hit.y);
float thick=0.13 + 0.06*vnoise(hit.xz*3.0 + hit.y*0.5);
float lip=sideFace*(1.0 - smoothstep(thick*0.75,thick,below));
if (lip > 0.001) {
float edgeY=ceil(hit.y - 0.0005);
vec2 upUv=uv + vec2(0.0,0.2/max(dist*PixelWorld,0.00001)/max(Resolution.y,1.0));
vec3 aboveEdge=worldAt(clamp(upUv,vec2(0.001),vec2(0.999)));
if (aboveEdge.y > edgeY + 0.03 && distance(aboveEdge.xz,hit.xz) < 0.2) lip=0.0;
float crest=1.0 - below/max(thick,0.001);
vec3 lipSnow=snowWhite*mix(vec3(0.62,0.70,0.9),vec3(1.05),smoothstep(0.0,0.7,crest));
scene=mix(scene,lipSnow,lip*SnowCover);
}
scene=mix(scene,snow,cover);
sceneLum=luminance(scene);
}
}
vec3 skyCol=moodSky(rd,mu);
vec3 color=mix(scene,skyCol,skyMask*SkyMix);
vec3 glow=texture(Sampler2,uv).rgb*Lights;
vec3 hazeTint=HazeColor*(0.85 + 0.15*saturate(rd.y + 0.5));
hazeTint +=SkySun*henyeyGreenstein(mu,0.7)*0.035*sunOn;
hazeTint +=glow*1.5;
color=mix(color,hazeTint,fogAmount);
color=mix(color,mix(skyCol,hazeTint,0.35),farHaze*0.6);
float shaft=1.0;
float sunOnScreen=SunScreen.z
*(1.0 - smoothstep(0.55,0.8,max(abs(SunScreen.x - 0.5),abs(SunScreen.y - 0.5))));
if (Rays > 0.001 && sunOnScreen > 0.001 && sunOn > 0.0) {
float jitter=hash12(gl_FragCoord.yx + vec2(17.0,59.0));
float mask=godRaysMask(uv,SunScreen.xy,jitter);
float angular=pow(saturate(mu*0.5 + 0.5),3.0);
float texture3=0.75 + 0.25*snoise(vec3(rd*6.0 + vec3(0.0,Time*0.1,0.0)));
float rays=mask*angular*sunOnScreen*Rays*texture3;
rays*=0.45 + 0.55*saturate(fogAmount + skyMask*0.85 + farHaze);
shaft=mix(1.0,0.35 + 0.9*mask*angular,sunOnScreen);
float dy=rd.y - sun.y;
float angSq=max(2.0*(1.0 - mu),0.0);
float disc=exp(-angSq*2600.0);
float dx2=max(angSq - dy*dy,0.0);
float streak=exp(-(dy*dy*850.0 + dx2*34.0));
float canvas=saturate(skyMask + farHaze);
vec3 sunGlow=SkySun*(disc*canvas + streak*0.35*mix(0.25,1.0,canvas));
sunGlow*=sunOnScreen*solarDiscVisibility(SunScreen.xy)*Rays;
color=screenBlend(color,SkySun*rays*0.8 + sunGlow);
}
color=mix(color,gradeColor(color),Grade);
if (Lights > 0.001) color=screenBlend(color,glow);
if (Amount > 0.001) {
float lit=0.3 + 0.7*sceneLum;
color +=particles(ro,rd,dist,sun,lit,shaft);
}
vec2 corner=(uv - 0.5)*vec2(aspect,1.0);
if (Kind==1) {
float reach=saturate(Vignette*2.2)*(0.9 + 0.1*sin(Time*0.21));
float edge=smoothstep(0.42,1.05,length(corner))*reach;
if (edge > 0.0) {
float fn=snoise(vec3(corner*14.0,3.7))*0.5 + snoise(vec3(corner*38.0,9.1))*0.5;
float frost=smoothstep(0.35,0.9,saturate(edge*1.4 - 0.25 + fn*0.5*reach));
vec3 frosted=mix(color,vec3(0.9,0.94,1.0),0.65) + vec3(fn*0.08);
color=mix(color,frosted,frost*0.9);
}
} else if (Kind==0 && sunOnScreen > 0.001 && sunOn > 0.0) {
float seen=0.0;
for (int i=0;i < 5;++i) {
vec2 s=clamp(SunScreen.xy + SUN_TAPS[i],vec2(0.001),vec2(0.999));
seen +=depthIsFar(texture(Sampler1,s).r)?0.2:0.0;
}
float glare=sunOnScreen*seen*saturate(Vignette*2.2);
if (glare > 0.001) {
vec2 sunC=(SunScreen.xy - 0.5)*vec2(aspect,1.0);
vec3 flare=vec3(0.0);
for (int i=0;i < 4;++i) {
float dg=length(corner - sunC*GHOST_AT[i])/GHOST_R[i];
float disc=smoothstep(1.0,0.7,dg)*(0.6 + 0.4*smoothstep(0.5,0.95,dg));
flare +=SkySun*GHOST_TINT[i]*(disc*GHOST_GAIN[i]);
}
float nearSun=pow(saturate(mu*0.5 + 0.5),24.0);
vec2 glass=uv*vec2(aspect,1.0);
float specks=lensSpecks(glass*22.0,0.3) + lensSpecks(glass*57.0 + 3.7,0.2)*0.6;
flare +=SkySun*(specks*nearSun*0.6);
color=mix(color,color*0.85 + SkySun*0.15,glare*0.25);
color +=flare*glare;
}
}
color*=1.0 - Vignette*smoothstep(0.4,1.25,length(corner)*1.35);
float grainAmount=Kind==2?0.024:0.012;
float grain=hash12(gl_FragCoord.xy + vec2(fract(Time*13.7)*311.0,74.3)) - 0.5;
color +=grain*grainAmount*(1.2 - saturate(luminance(color)));
float outLum=luminance(color);
color/=1.0 + max(outLum - 0.985,0.0)*1.6;
OutColor=vec4(saturate3(color),1.0);
}