#version 150
uniform sampler2D Sampler0; in vec2 TexCoord; out vec4 OutColor;
void main(){vec4 c=texture(Sampler0,TexCoord);if(c.a<=1.0/255.0)discard;OutColor=c;}
