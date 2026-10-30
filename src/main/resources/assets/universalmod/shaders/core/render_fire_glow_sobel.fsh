#version 150
uniform sampler2D Sampler0; uniform vec2 OneTexel;
in vec2 TexCoord; out vec4 OutColor;
void main(){
 vec4 c=texture(Sampler0,TexCoord),l=texture(Sampler0,TexCoord-vec2(OneTexel.x,0)),r=texture(Sampler0,TexCoord+vec2(OneTexel.x,0)),u=texture(Sampler0,TexCoord-vec2(0,OneTexel.y)),d=texture(Sampler0,TexCoord+vec2(0,OneTexel.y));
 float total=clamp(abs(c.a-l.a)+abs(c.a-r.a)+abs(c.a-u.a)+abs(c.a-d.a),0.0,1.0);
 vec3 rgb=(c.rgb*c.a+l.rgb*l.a+r.rgb*r.a+u.rgb*u.a+d.rgb*d.a)*0.2;
 OutColor=vec4(rgb,total);
}
