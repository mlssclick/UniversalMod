#version 150
in vec3 Position;
in vec4 Color;
in vec2 Coord;
in float BatchIndex;
uniform mat4 Projection;
out vec2 FragCoord;
out vec4 FragColor;
flat out int QuadIndex;
void main() {
    gl_Position = Projection * vec4(Position, 1.0);
    FragCoord = Coord;
    FragColor = Color;
    QuadIndex = max(int(BatchIndex + 0.5), 0);
}
