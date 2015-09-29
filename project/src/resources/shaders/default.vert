#version 430 compatibility

out vec4 color;
out float depth;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    color = gl_Color;
    depth = -(gl_ModelViewMatrix * gl_Vertex).z;
}
