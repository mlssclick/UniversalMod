#version 150

layout(std140) uniform MoodWeatherData {
    mat4 MoodInverseViewProjection;
    vec4 MoodWeatherParams[35];
    vec4 HalloweenPumpkins[20];
};
#define InvViewProj MoodInverseViewProjection
#define CamPos MoodWeatherParams[1].xyz
#define Resolution MoodWeatherParams[2].xy
#define Time MoodWeatherParams[3].x
#define PixelWorld MoodWeatherParams[4].x
#define SunDir MoodWeatherParams[5].xyz
#define SunScreen MoodWeatherParams[6].xyz
#define ViewDistance MoodWeatherParams[7].x
#define GroundY MoodWeatherParams[8].x
#define Kind int(MoodWeatherParams[9].x)
#define Amount MoodWeatherParams[10].x
#define Speed MoodWeatherParams[11].x
#define Haze MoodWeatherParams[12].x
#define Grade MoodWeatherParams[13].x
#define Lights MoodWeatherParams[14].x
#define Vignette MoodWeatherParams[15].x
#define Rays MoodWeatherParams[16].x
#define Shimmer MoodWeatherParams[17].x
#define SkyMix MoodWeatherParams[18].x
#define Chroma MoodWeatherParams[19].x
#define Relight MoodWeatherParams[20].x
#define Occlusion MoodWeatherParams[21].x
#define SnowCover MoodWeatherParams[22].x
#define SkyTop MoodWeatherParams[23].xyz
#define SkyHorizon MoodWeatherParams[24].xyz
#define SkySun MoodWeatherParams[25].xyz
#define HazeColor MoodWeatherParams[26].xyz
#define ParticleColor MoodWeatherParams[27].xyz
#define ShadowTint MoodWeatherParams[28].xyz
#define LightTint MoodWeatherParams[29].xyz
#define LightDir MoodWeatherParams[30].xyz
#define LightGain MoodWeatherParams[31].xyz
#define GradeParams MoodWeatherParams[32]
#define ReversedDepth int(MoodWeatherParams[33].x)
#define SkyPassApplied MoodWeatherParams[34].x
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
    for (int y=-1; y<=1; ++y) {
        for (int x=-1; x<=1; ++x) {
            vec2 uv = centerUv + vec2(float(x), float(y)) * texel * 4.0;
            float w = 1.0 / (1.0 + 0.2 * float(x*x + y*y));
            visible += (depthIsFar(texture(Sampler1, clamp(uv, vec2(0.001), vec2(0.999))).r)
                ? 1.0 : 0.0) * w;
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
if (Kind == 5) {
    float up=saturate(rd.y);
    vec3 sky=mix(vec3(0.994,0.770,0.831),vec3(0.620,0.778,0.985),smoothstep(0.02,0.96,up));
    float warm=pow(max(mu,0.0),29.0);
    float swirl=vnoise(rd.xz/max(rd.y+0.42,0.15)*2.7+vec2(Time*0.006,-Time*0.002));
    float bank=smoothstep(0.06,0.57,swirl)*smoothstep(-0.04,0.25,rd.y);
    sky=mix(sky,vec3(1.0,0.963,0.966),bank*0.40);
    return saturate3(sky+vec3(0.075,0.038,0.028)*warm);
}
if (Kind==6) return mix(vec3(0.19,0.22,0.26),vec3(0.09,0.115,0.155),saturate(rd.y));
if (Kind==7) return mix(vec3(0.69,0.62,0.53),vec3(0.37,0.41,0.48),saturate(rd.y));
if (Kind==8) return mix(vec3(0.77,0.88,0.98),vec3(0.23,0.48,0.81),saturate(rd.y));
if (Kind==9) return mix(vec3(0.19,0.13,0.16),vec3(0.04,0.043,0.07),saturate(rd.y));
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

float halloweenBlood(vec3 p) {
    vec2 block = floor(p.xz);
    vec2 f = fract(p.xz);
    float pattern = hash12(block + vec2(47.13, 18.71));

    if (pattern < 0.040) return 0.0;

    vec2 px = (floor(f * 16.0) + 0.5) / 16.0;
    vec2 jitter = vec2(hash12(block + 4.17), hash12(block + 13.91));
    vec2 center = vec2(0.10) + jitter * 0.80;
    vec2 q = px - center;

    vec2 stretch = mix(vec2(0.83,1.67),vec2(1.66,0.81),pattern);
    vec2 d = q * stretch;
    vec2 join = (vec2(jitter.y,jitter.x) - 0.5)*0.56;
    float lobe = min(dot(d,d), dot((q-join)*stretch*1.06,(q-join)*stretch*1.06));
    float size = mix(0.040,0.185,hash12(block+vec2(21.13,8.29)));

    vec2 fleckCenter = vec2(0.12) + vec2(hash12(block+31.41),hash12(block+53.87))*0.76;
    float lobe2 = dot(px-fleckCenter,px-fleckCenter);
    float radius = min(lobe,mix(0.018,0.065,jitter.x)+lobe2*1.35);

    vec2 pixel = floor(p.xz * 16.0);
    float grain = hash12(pixel + vec2(31.7, 82.1));
    float edgeR = size + (grain-0.48)*0.041;
    float pool = 1.0 - smoothstep(edgeR-0.017,edgeR+0.017,radius);

    float drop = step(0.91,grain) * step(0.035, radius) *
                 (1.0 - smoothstep(0.20,0.83,length(q)));
    float fleck = step(0.964,hash12(pixel+block*3.47)) *
                  (1.0 - smoothstep(0.35,0.99,length(px-fleckCenter)));
    float torn = mix(0.74,1.0,step(0.14,grain));
    return max(pool*torn,max(drop*0.85,fleck*0.93));
}

vec3 halloweenPumpkinLighting(vec3 surface, vec3 normal) {
    vec3 light = vec3(0.0);
    float groundFacing = clamp(normal.y*0.73+0.27, 0.0, 1.0);
    for (int i=0; i<20; ++i) {
        vec4 pumpkin = HalloweenPumpkins[i];

        if (pumpkin.w <= 0.005) break;
        vec3 delta = surface - pumpkin.xyz;
        if (abs(delta.x) > 4.3 || abs(delta.z) > 4.3 || abs(delta.y) > 3.0) continue;
        float d2 = dot(delta.xz,delta.xz);
        if (d2 > 18.5) continue;
        float pool = exp(-d2*0.31 - abs(delta.y+0.25)*0.86);
        float flicker = 0.92 + 0.08*sin(Time*2.15+float(i)*4.87);
        light += vec3(1.0,0.40,0.075) * (pool*0.64*pumpkin.w*flicker*groundFacing);
    }
    return light;
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
float thickness=Kind==5?33.0:(Kind==4?21.0:(Kind==2?10.0:26.0));
float integ=heightIntegral(ro,rd,min(dist,400.0),GroundY - 2.0,thickness);
float turb=1.0;
if (Kind==2) {
vec3 probe=ro + rd*clamp(dist*0.5,4.0,60.0);
vec3 drift=vec3(Time*Speed*0.6,Time*0.2,Time*0.3);
float n=snoise(probe*0.06 + drift*0.3)*0.6 + snoise(probe*0.16 - drift*0.5)*0.4;
turb=mix(0.5,1.6,saturate(n*0.5 + 0.5));
}
float sigma=Haze*(Kind==5?0.019:(Kind==4?0.028:0.012))*turb;
float fogAmount=saturate(1.0 - exp(-sigma*integ));
fogAmount*=mix(1.0,1.0 - 0.85*pow(up,0.6),skyMask);
if (Kind==4) fogAmount *= (1.0 - skyMask) * 0.74;
if (Kind==5) fogAmount *= (1.0 - skyMask) * 0.86;

fogAmount *= 1.0 - skyMask*SkyPassApplied;
float farHaze=smoothstep(ViewDistance*0.48,ViewDistance*0.94,dist)*(1.0 - skyMask) * (Kind==4?0.68:(Kind==5?0.28:1.0));
float halloweenFogBank=0.0;
if (Kind==4) {
vec3 mistProbe=ro + rd*clamp(dist*0.38,5.0,44.0);
float lowAltitude=1.0 - smoothstep(GroundY + 8.0,GroundY + 22.0,mistProbe.y);
vec2 mistUv=mistProbe.xz*0.052 + vec2(Time*0.020,-Time*0.013);
float broadBank=vnoise(mistUv);
float detailBank=vnoise(mistUv*2.2 + 7.3);
float bankField=broadBank*0.68 + detailBank*0.32;
halloweenFogBank=smoothstep(-0.12,0.40,bankField);
float bankWeight=lowAltitude*halloweenFogBank*(1.0 - skyMask);
fogAmount=saturate(fogAmount*1.18 + bankWeight*0.30);
farHaze=saturate(farHaze + bankWeight*0.22);
}
vec2 sampleUv=uv;
if (Shimmer > 0.0 && !isSky) {
float low=1.0 - smoothstep(-0.08,0.35,rd.y);
float farness=smoothstep(3.0,28.0,dist);
float n=snoise(vec3(uv.x*16.0*aspect,uv.y*16.0 - Time*Speed*0.8,Time*0.3));
vec2 shifted=uv + vec2(n*0.3,n)*(0.007*Shimmer*low*farness);
if (sceneDistance(shifted) > dist*0.7) sampleUv=shifted;
}
float softPx=Kind==4?0.0:5.0*saturate(fogAmount*1.4 + farHaze)*(1.0 - skyMask);
vec3 scene=sampleScene(sampleUv,softPx);
vec3 halloweenNormal=vec3(0.0,1.0,0.0);
if (Kind==4) {

    halloweenNormal=normalize(cross(dFdx(hit),dFdy(hit)));
    if (dot(halloweenNormal,hit-ro)>0.0) halloweenNormal=-halloweenNormal;
}
if (Kind==4 && !isSky && dist < 46.0) {

    vec3 groundNormal = halloweenNormal;
    float top = smoothstep(0.68, 0.89, groundNormal.y);
    if (top > 0.03) {
        float blood = halloweenBlood(hit) * top;
        if (blood > 0.003) {
            float fleck = hash12(floor(hit.xz*16.0) + vec2(9.23,16.1));
            vec3 driedBlood = mix(vec3(0.080,0.005,0.011),vec3(0.325,0.014,0.026),fleck);

            scene = mix(scene, scene*0.19 + driedBlood*0.81, blood*0.94);
        }
    }
    vec3 warmth = halloweenPumpkinLighting(hit, groundNormal);
    scene += warmth;
    scene *= vec3(0.91,0.88,1.0);
}
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
vec3 color=mix(scene,skyCol,skyMask*SkyMix*(1.0 - SkyPassApplied));
vec3 glow=vec3(0.0);
if (Lights>0.001) glow=texture(Sampler2,uv).rgb*Lights;
vec3 hazeTint=HazeColor*(0.85 + 0.15*saturate(rd.y + 0.5));
hazeTint +=SkySun*henyeyGreenstein(mu,0.7)*0.035*sunOn;
hazeTint +=glow*1.5;
if (Kind==5) {

    vec3 samplePoint = ro + rd*clamp(dist*0.42,4.0,48.0);
    float fogSheets = vnoise(samplePoint.xz*0.042+vec2(Time*0.012,-Time*0.006));
    float cloudlet = vnoise(samplePoint.xz*0.11+8.5);
    float mist = smoothstep(-0.38,0.65,fogSheets*0.77 + cloudlet*0.23);
    hazeTint = mix(vec3(0.94,0.748,0.816),vec3(0.997,0.864,0.860),mist*0.53);
    hazeTint += vec3(0.06,0.034,0.036)*pow(max(mu,0.0),14.0);
    fogAmount = saturate(fogAmount + mist*(1.0-skyMask)*smoothstep(13.0,74.0,dist)*0.15);
}
if (Kind==4) {
vec3 baseFog=mix(vec3(0.13,0.04,0.11),vec3(0.46,0.17,0.07),1.0 - saturate(rd.y*0.95 + 0.24));
vec3 darkBank=vec3(0.045,0.012,0.070);
hazeTint=mix(baseFog,darkBank,halloweenFogBank*0.55);
hazeTint += vec3(0.12,0.05,0.03)*halloweenFogBank*(1.0 - skyMask)*0.20;
hazeTint += glow*0.95;
}
color=mix(color,hazeTint,fogAmount);
color=mix(color,mix(skyCol,hazeTint,0.35),farHaze*0.6);
float shaft=1.0;
float raysOnScreen=SunScreen.z
*(1.0 - smoothstep(0.55,0.8,max(abs(SunScreen.x - 0.5),abs(SunScreen.y - 0.5))));
float sunOnScreen=0.0;
if (Rays>0.001 || Kind==0) {
    sunOnScreen=SunScreen.z*solarDiscVisibility(SunScreen.xy)
        *(1.0-smoothstep(0.55,0.8,max(abs(SunScreen.x-0.5),abs(SunScreen.y-0.5))));
}
if (Rays > 0.001 && raysOnScreen > 0.001 && sunOn > 0.0) {
float jitter=hash12(gl_FragCoord.yx + vec2(17.0,59.0));
float mask=godRaysMask(uv,SunScreen.xy,jitter);
float angular=pow(saturate(mu*0.5 + 0.5),3.0);
float texture3=0.75 + 0.25*snoise(vec3(rd*6.0 + vec3(0.0,Time*0.1,0.0)));
float rays=mask*angular*raysOnScreen*Rays*texture3;
rays*=0.45 + 0.55*saturate(fogAmount + skyMask*0.85 + farHaze);
shaft=mix(1.0,0.35 + 0.9*mask*angular,raysOnScreen);
float dy=rd.y - sun.y;
float angSq=max(2.0*(1.0 - mu),0.0);
float disc=exp(-angSq*2600.0);
float dx2=max(angSq - dy*dy,0.0);
float streak=exp(-(dy*dy*850.0 + dx2*34.0));
float canvas=saturate(skyMask + farHaze);
vec3 sunGlow=SkySun*(disc*canvas + streak*0.35*mix(0.25,1.0,canvas));
sunGlow*=sunOnScreen*Rays;
color=screenBlend(color,SkySun*rays*0.8 + sunGlow);
}

if (Kind==9 && !isSky && dist<43.0) {
    vec3 surf=normalize(cross(dFdx(hit),dFdy(hit)));
    if (dot(surf,hit-ro)>0.0) surf=-surf;
    float upSurface=smoothstep(0.75,0.91,surf.y);
    vec2 cell=floor(hit.xz);
    if (hash12(cell+vec2(17.3,19.1))>0.956) {
        float stain=halloweenBlood(hit);
        vec3 blood=vec3(0.26,0.011,0.028);
        color=mix(color,color*0.29+blood*0.71,stain*upSurface*0.84);
    }
}
if (Kind==6) {
    float flashPhase=mod(Time,15.0);
    float flash=exp(-pow((flashPhase-0.07)*29.0,2.0))+0.62*exp(-pow((flashPhase-0.19)*37.0,2.0));
    color+=vec3(0.24,0.28,0.40)*clamp(flash,0.0,1.0)*(isSky?0.0:0.35);
}

if ((Kind==8 || Kind==5) && SunScreen.z>0.0 && SunDir.y>-0.065) {
    vec2 sunUv=SunScreen.xy;
    vec2 lens=(uv-sunUv)*vec2(aspect,1.0);
    float radius=length(lens);
    float visibility=solarDiscVisibility(sunUv);
    float brilliance=visibility*smoothstep(-0.045,0.12,SunDir.y);
    if (brilliance>0.003) {
        float halo=exp(-radius*radius*48.0);
        float core=exp(-radius*radius*1200.0);
        float veil=exp(-radius*radius*8.0);
        vec2 angle=normalize(lens+vec2(0.000001));
        float star=pow(abs(angle.x),17.0)+pow(abs(angle.y),17.0);
        float radialRays=pow(max(0.0,0.5+0.5*cos(atan(angle.y,angle.x)*16.0)),10.0);
        vec3 sunTint=Kind==5?vec3(1.0,0.81,0.80):vec3(1.0,0.88,0.60);
        float boost=Kind==5?0.57:1.0;
        color=screenBlend(color,sunTint*brilliance*boost*(halo*0.38+core*0.90+veil*0.095+star*halo*0.13+radialRays*halo*0.06));
        vec2 axis=uv-(1.0-sunUv);
        float ghost=exp(-dot(axis*vec2(aspect,1.0),axis*vec2(aspect,1.0))*260.0);
        color+=sunTint*ghost*brilliance*boost*0.085;
    }
}
color=mix(color,gradeColor(color),Grade);
if (Kind==5 && !isSky) {

    float brightness=saturate(luminance(color));
    vec3 reflected=vec3(0.023,0.007,0.020)*(0.26+0.74*brightness);
    color += reflected*(1.0-saturate(dist/75.0));
}
if (Lights > 0.001) color=screenBlend(color,glow);
if (Amount > 0.001 && Kind <= 2) {
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
float grainAmount=Kind==4?0.009:(Kind==2?0.024:0.012);
float grain=hash12(gl_FragCoord.xy + vec2(fract(Time*13.7)*311.0,74.3)) - 0.5;
color +=grain*grainAmount*(1.2 - saturate(luminance(color)));
float outLum=luminance(color);
color/=1.0 + max(outLum - 0.985,0.0)*1.6;
OutColor=vec4(saturate3(color),1.0);
}
