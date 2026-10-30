#version 150
in vec3 Position;
in vec2 Coord;
in float ShapeIndex;
uniform mat4 ProjectionMatrix;
out vec2 FragCoord;
flat out int QuadIndex;
void main() {
    FragCoord = Coord;
    QuadIndex = int(ShapeIndex + 0.5);
    gl_Position = ProjectionMatrix * vec4(Position, 1.0);
}
