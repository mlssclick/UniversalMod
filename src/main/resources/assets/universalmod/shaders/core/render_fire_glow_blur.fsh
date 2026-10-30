#version 150
uniform sampler2D Sampler0; uniform vec2 OneTexel; uniform vec2 BlurDir;
in vec2 TexCoord; out vec4 OutColor;
void main(){vec2 stepv=BlurDir*OneTexel;vec4 b=vec4(0.0);for(int i=-2;i<=2;i++)b+=texture(Sampler0,TexCoord+stepv*float(i));OutColor=vec4(b.rgb/5.0,b.a);}
