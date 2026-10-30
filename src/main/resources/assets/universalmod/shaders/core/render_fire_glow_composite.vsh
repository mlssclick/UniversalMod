#version 150
in vec3 Position; in vec4 Color; out vec2 TexCoord;
void main(){ gl_Position = vec4(Position, 1.0);TexCoord = Position.xy * 0.5 + 0.5; }
