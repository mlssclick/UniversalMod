#version 150

uniform mat4 LyricsViewMat;
uniform mat4 LyricsProjMat;

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 TexCoord;
out vec4 VertexColor;

void main() {
    gl_Position = LyricsProjMat * LyricsViewMat * vec4(Position, 1.0);
    TexCoord = UV0;
    VertexColor = Color;
}
