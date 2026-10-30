#version 150
in vec3 Position;
in vec4 Color;
uniform vec4 BoxBounds;
out vec2 FragCoord;
out vec4 FragColor;
void main() {
    vec2 normalized = vec2((Position.x + 1.0) * 0.5, (1.0 - Position.y) * 0.5);
    FragCoord = (normalized - BoxBounds.xy) / BoxBounds.zw;
    FragColor = Color;
    gl_Position = vec4(Position, 1.0);
}
