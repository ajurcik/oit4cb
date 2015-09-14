#version 430 compatibility

in vec4 position;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * vec4(position.xyz, 1.0);
}
