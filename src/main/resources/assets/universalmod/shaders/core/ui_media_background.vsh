#version 150
in vec3 Position;
in vec4 Color;
out vec2 FragCoord;
out vec4 FragColor;
void main() {
    FragCoord = Position.xy * 0.5 + 0.5;
    FragColor = Color;
    gl_Position = vec4(Position, 1.0);
}
