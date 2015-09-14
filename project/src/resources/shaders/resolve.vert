#version 430 compatibility

void main() {
    gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);
}
