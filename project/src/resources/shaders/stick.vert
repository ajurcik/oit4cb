#version 430 compatibility

const float width = 0.2;

uniform float size;

out vec3 pos;
out vec3 normal;
out vec4 color;

void main() {
    vec4 position = gl_Vertex;
    position.xyz *= width;
    position.y += sign(position.y) * (size / 2.0 - 0.5 * width);
    
    gl_Position = gl_ModelViewProjectionMatrix * position;
    
    pos = vec3(gl_ModelViewMatrix * position);
    normal = gl_NormalMatrix * gl_Normal;
    color = gl_Color;
}
