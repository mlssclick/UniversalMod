#version 150

layout(std140) uniform AtmosphereSkyData { vec4 AtmosphereSkyParams[6]; };
#define SunDir AtmosphereSkyParams[0].xyz
#define Time AtmosphereSkyParams[0].w
#define SkyKind int(AtmosphereSkyParams[5].x)
#define PalSkyTop AtmosphereSkyParams[1].xyz
#define PalHorizonWarm AtmosphereSkyParams[2].xyz
#define PalRay AtmosphereSkyParams[3].xyz
#define PalHorizonCool AtmosphereSkyParams[4].xyz
#define SkyTop AtmosphereSkyParams[1].xyz
#define SkyHorizon AtmosphereSkyParams[2].xyz
#define SkySun AtmosphereSkyParams[3].xyz
#define Kind SkyKind
in vec3 vPos;
in vec4 FragColor;
out vec4 OutColor;

float saturate(float v) { return clamp(v,0.0,1.0);}

float luminance(vec3 c) { return dot(c,vec3(0.2126,0.7152,0.0722));}

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

float henyeyGreenstein(float mu,float g) {
float g2=g*g;
return (1.0 - g2)/pow(max(1.0 + g2 - 2.0*g*mu,0.0001),1.5);
}

float halloweenHash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 19.19);
    return fract((q.x + q.y) * q.z);
}
float halloweenCloudNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 w = fract(p);
    w = w*w*(3.0-2.0*w);
    return mix(
        mix(halloweenHash(cell), halloweenHash(cell+vec2(1.0,0.0)), w.x),
        mix(halloweenHash(cell+vec2(0.0,1.0)), halloweenHash(cell+vec2(1.0,1.0)), w.x), w.y);
}

float stormRandom(float n) { return fract(sin(n*127.1+78.233)*43758.5453); }
float thunderPulse(float time) {
    float u=mod(time,15.0);
    float flash=exp(-pow((u-0.07)*29.0,2.0));
    flash += 0.62*exp(-pow((u-0.19)*37.0,2.0));
    flash += 0.37*exp(-pow((u-0.31)*33.0,2.0));
    return clamp(flash,0.0,1.0);
}

float lunarFbm(vec2 p) {
    float n=0.0, amp=0.50;
    mat2 m=mat2(1.79,-1.14,1.14,1.79);
    for(int i=0;i<5;i++) {
        n+=halloweenCloudNoise(p)*amp;
        p=m*p+vec2(7.27,13.41);
        amp*=0.50;
    }
    return n/0.96875;
}
vec2 lunarCraterField(vec2 uv,float density) {

    vec2 p=uv*density;
    vec2 cell=floor(p+0.5);
    vec2 offset=vec2(halloweenHash(cell+5.83),halloweenHash(cell+53.11))-0.5;
    vec2 local=p-cell-offset*0.24;
    float size=mix(0.16,0.31,halloweenHash(cell+31.2));
    float d=length(local)/size;
    float erosion=0.62+0.38*halloweenHash(cell+17.2);
    float basin=1.0-smoothstep(0.42,0.85,d);
    float rim=exp(-pow((d-0.94)/0.13,2.0));
    return vec2(basin,rim)*erosion;
}

vec3 moodSky(vec3 rd,float mu) {
if (Kind==1) {
    float up=saturate(rd.y);
    vec3 frost=mix(vec3(0.78,0.855,0.945),vec3(0.45,0.62,0.81),smoothstep(0.02,0.96,up));
    vec2 w=rd.xz/max(rd.y+0.24,0.12);
    vec2 drift=vec2(Time*0.009,-Time*0.003);
    float banks=halloweenCloudNoise(w*1.42+drift);
    float wisps=halloweenCloudNoise(w*3.2-drift*0.37+7.1);
    float clouds=smoothstep(0.46,0.69,banks*0.68+wisps*0.32)*smoothstep(0.01,0.16,rd.y);
    frost=mix(frost,vec3(0.90,0.94,0.99),clouds*0.51);
    float sunFacing=max(dot(rd,normalize(SunDir)),0.0);
    frost+=vec3(0.065,0.072,0.073)*pow(sunFacing,160.0);
    return clamp(frost,vec3(0.0),vec3(1.0));
}
if (Kind == 5) {

    float up = saturate(rd.y);
    vec3 horizon = vec3(0.999, 0.746, 0.815);
    vec3 rose = vec3(0.996, 0.861, 0.936);
    vec3 skyTop = vec3(0.617, 0.773, 0.985);
    vec3 sky = mix(horizon, mix(rose, skyTop, smoothstep(0.16, 0.97, up)), smoothstep(-0.03, 0.80, up));
    float gold = exp(-up*17.0);
    sky = mix(sky, vec3(1.0, 0.835, 0.778), gold*0.28);

    vec2 perspective = rd.xz/max(rd.y + 0.24, 0.12);
    vec2 wind = vec2(Time*0.0034, -Time*0.0018);
    float warp = halloweenCloudNoise(perspective*0.55 + wind*0.7)-0.5;
    vec2 domain = perspective + vec2(warp*0.43, -warp*0.24);

    float large = halloweenCloudNoise(domain*1.33+wind);
    float middle = halloweenCloudNoise(domain*3.24 - wind*0.46+11.7);
    float fine = halloweenCloudNoise(domain*7.1+wind*1.6+vec2(37.0,-9.0));
    float density = large*0.55 + middle*0.32 + fine*0.13;
    float nearMask = smoothstep(-0.04,0.17,rd.y)*(1.0-smoothstep(0.71,1.06,rd.y));
    float cloud = smoothstep(0.44,0.67,density)*nearMask;
    float layered = smoothstep(0.55,0.79,density);
    vec3 cloudShadow = vec3(0.912,0.737,0.836);
    vec3 cloudPearl = vec3(1.0,0.976,0.979);
    sky = mix(sky, mix(cloudShadow,cloudPearl,layered),cloud*0.76);
    float rim = smoothstep(0.48,0.59,density) - smoothstep(0.63,0.75,density);
    sky += vec3(0.16,0.111,0.128)*rim*cloud*0.24;

    vec2 highUV = rd.xz/max(rd.y + 0.46,0.26);
    float high = halloweenCloudNoise(highUV*4.7+vec2(-Time*0.0023,Time*0.0030)+vec2(17.0,8.0));
    float feather = smoothstep(0.55,0.79,high)*smoothstep(0.35,0.73,up);
    sky = mix(sky, vec3(0.99,0.956,0.994),feather*0.22);

    float sunAlignment = max(dot(rd,normalize(SunDir)),0.0);
    float glow = pow(sunAlignment,49.0);
    float halo = pow(sunAlignment,650.0);
    sky += vec3(0.125,0.082,0.069)*glow + vec3(0.20,0.138,0.077)*halo;
    return clamp(sky,vec3(0.0),vec3(1.0));
}
if (Kind==6) {
    float up=saturate(rd.y);
    vec3 sky=mix(vec3(0.205,0.224,0.255),vec3(0.095,0.119,0.152),smoothstep(0.02,0.84,up));
    vec2 uv=rd.xz/max(rd.y+0.28,0.15);
    vec2 flow=vec2(Time*0.009,Time*0.003);
    float warp=halloweenCloudNoise(uv*0.61+flow)-0.5;
    float mass=halloweenCloudNoise(uv*1.47+flow+warp*0.58);
    float ripples=halloweenCloudNoise(uv*3.2+flow*1.5+12.6);
    float shadow=smoothstep(0.39,0.70,mass*0.68+ripples*0.32);
    vec3 stormCloud=mix(vec3(0.085,0.100,0.126),vec3(0.235,0.248,0.286),shadow);
    sky=mix(sky,stormCloud,smoothstep(-0.04,0.17,rd.y)*0.79);
    float flash=thunderPulse(Time);
    sky+=vec3(0.23,0.27,0.36)*flash*(0.37+0.33*shadow);

    return clamp(sky,vec3(0.0),vec3(1.0));
}
if (Kind==7) {
    float up=saturate(rd.y);
    vec3 sky=mix(vec3(0.69,0.64,0.56),vec3(0.38,0.43,0.49),smoothstep(0.02,0.98,up));
    vec2 uv=rd.xz/max(rd.y+0.25,0.16);
    float coarse=halloweenCloudNoise(uv*1.05+vec2(Time*0.0028,Time*0.0015));
    float fine=halloweenCloudNoise(uv*2.8-vec2(Time*0.0032,Time*0.0012)+11.6);
    float bank=smoothstep(0.35,0.72,coarse*0.7+fine*0.3);
    sky=mix(sky,vec3(0.55,0.55,0.54),bank*0.64);
    float sunlight=pow(max(mu,0.0),38.0);
    sky+=vec3(0.13,0.090,0.041)*sunlight;
    return clamp(sky,vec3(0.0),vec3(1.0));
}
if (Kind==8) {
    float up=saturate(rd.y);
    vec3 sky=mix(vec3(0.77,0.88,0.98),vec3(0.23,0.48,0.81),smoothstep(0.0,0.93,up));
    vec2 uv=rd.xz/max(rd.y+0.33,0.16);
    vec2 wind=vec2(Time*0.0032,-Time*0.0014);
    float broad=halloweenCloudNoise(uv*1.72+wind);
    float fine=halloweenCloudNoise(uv*3.9-wind*1.6+12.7);
    float cloud=smoothstep(0.43,0.72,broad*0.69+fine*0.31);
    sky=mix(sky,vec3(0.94,0.965,0.995),cloud*0.74*smoothstep(-0.02,0.18,rd.y));
    float facing=max(dot(rd,normalize(SunDir)),0.0);
    float day=smoothstep(-0.10,0.12,SunDir.y);
    float twilight=exp(-abs(SunDir.y)*8.5);
    float horizonGlow=exp(-up*8.0);
    sky=mix(vec3(0.018,0.033,0.081),sky,0.13+day*0.87);
    sky+=vec3(0.38,0.125,0.038)*twilight*horizonGlow*0.55;
    float disc=1.0-smoothstep(0.99934,0.99970,facing);
    disc=1.0-disc;
    float corona=pow(facing,55.0);
    float inner=pow(facing,620.0);
    float flare=pow(facing,2900.0);
    float strands=0.88+0.12*cos(atan(rd.z,rd.x)*26.0+Time*0.013);
    sky+=day*(vec3(0.31,0.17,0.055)*corona + vec3(0.45,0.31,0.10)*inner*strands);
    sky=mix(sky,vec3(1.0,0.984,0.84),disc*day*0.93);
    sky+=vec3(0.34,0.25,0.10)*flare*day;
    return clamp(sky,vec3(0.0),vec3(1.0));
}
if (Kind==9) {
    float up=saturate(rd.y);
    vec3 sky=mix(vec3(0.17,0.145,0.173),vec3(0.028,0.031,0.056),smoothstep(0.03,0.94,up));
    vec2 uv=rd.xz/max(rd.y+0.28,0.18);
    float broad=halloweenCloudNoise(uv*1.4+vec2(Time*0.004,-Time*0.002));
    float fine=halloweenCloudNoise(uv*3.9+vec2(-Time*0.005,Time*0.002)+17.3);
    float cloud=smoothstep(0.39,0.72,broad*0.7+fine*0.3);
    sky=mix(sky,vec3(0.080,0.068,0.087),cloud*0.62);

    vec3 moonDir=normalize(vec3(-SunDir.x,0.38+abs(SunDir.y)*0.69,-0.40));
    vec3 tangent=normalize(cross(vec3(0.0,1.0,0.0),moonDir));
    vec3 bitangent=normalize(cross(moonDir,tangent));
    vec2 lunarUv=vec2(dot(rd,tangent),dot(rd,bitangent))/0.082;
    float radial=length(lunarUv);
    if (radial < 1.18) {
        float disc=1.0-smoothstep(0.985,1.012,radial);
        float sculpt=sqrt(max(0.0,1.0-radial*radial));
        vec2 warped=lunarUv+0.052*vec2(lunarFbm(lunarUv*3.1+2.3)-0.5,
                                        lunarFbm(lunarUv*3.1-8.8)-0.5);
        float plate=lunarFbm(warped*3.6+vec2(11.7,7.9));
        float land=lunarFbm(warped*9.7-vec2(2.1,15.8));
        float grain=lunarFbm(warped*28.0+vec2(13.4,-6.1));
        float maria=smoothstep(0.39,0.62,plate*0.79+land*0.21);
        vec2 largeCr=lunarCraterField(warped,4.6);
        vec2 smallCr=lunarCraterField(warped+vec2(0.31,-0.17),13.8);
        float basin=largeCr.x*0.26+smallCr.x*0.077;
        float rim=largeCr.y*0.19+smallCr.y*0.065;
        float detail=0.10*(land-0.5)+0.042*(grain-0.5);
        vec3 darkLava=vec3(0.235,0.021,0.039);
        vec3 highlands=vec3(0.91,0.255,0.205);
        vec3 albedo=mix(darkLava,highlands,maria*0.84+0.09);
        albedo*=clamp(0.93+detail-basin+rim,0.37,1.24);
        vec3 lunarNormal=normalize(vec3(lunarUv*0.86,sculpt));
        vec3 light=normalize(vec3(-0.54,0.38,0.75));
        float diffuse=0.27+0.73*max(dot(lunarNormal,light),0.0);
        float edge=pow(sculpt,0.22);
        albedo*=diffuse*(0.45+0.55*edge);
        albedo+=vec3(0.055,0.020,0.017)*rim;
        sky=mix(sky,clamp(albedo,vec3(0.0),vec3(1.0)),disc);

        float corona=exp(-pow(max(radial-1.0,0.0)*8.0,1.28));
        sky+=vec3(0.30,0.020,0.045)*corona*(1.0-disc)*0.16;
    }
    float outer=pow(max(dot(rd,moonDir),0.0),60.0);
    sky+=vec3(0.16,0.012,0.036)*outer*0.17;
    return clamp(sky,vec3(0.0),vec3(1.0));
}
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

void main() {
    vec3 rd = normalize(vPos);
    vec3 sun = normalize(SunDir);
    float mu = dot(rd, sun);
    vec3 skyColor = moodSky(rd, mu);

    OutColor = vec4(clamp(skyColor, vec3(0.0), vec3(1.0)), 1.0);
}
