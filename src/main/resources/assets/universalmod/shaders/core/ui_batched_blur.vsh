#version 150
in vec3 Position;in vec4 Color;uniform mat4 ModelViewMat;uniform mat4 ProjMat;out vec2 FragCoord;out vec4 FragColor;
const vec2[4] RC = vec2[](vec2(0, 0), vec2(0, 1), vec2(1, 1), vec2(1, 0));
void main(){ gl_Position = ProjMat * ModelViewMat * vec4(Position, 1);FragCoord = RC[gl_VertexID % 4];FragColor = Color; }
