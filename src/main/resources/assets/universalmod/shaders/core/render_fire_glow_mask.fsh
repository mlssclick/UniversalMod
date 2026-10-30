#version 150
uniform sampler2D Sampler0; uniform sampler2D Sampler1;
uniform float UseCustomColor; uniform vec3 CustomColor;
in vec2 TexCoord; out vec4 OutColor;
void main(){
 vec4 before=texture(Sampler0,TexCoord); vec4 after=texture(Sampler1,TexCoord);
 vec3 delta=abs(after.rgb-before.rgb); float a=clamp(max(max(delta.r,delta.g),delta.b)*3.0+abs(after.a-before.a),0.0,1.0);
 vec3 base=UseCustomColor>0.5?CustomColor:after.rgb;
 OutColor=vec4(base,a);
}
