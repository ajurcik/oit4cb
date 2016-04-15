#version 430 compatibility

uniform float radius;

out vec3 pos;
out vec3 normal;
out vec4 color;

void main() {
    vec4 position = gl_Vertex;
    position.xyz *= radius;
    
    gl_Position = gl_ModelViewProjectionMatrix * position;
    
    pos = vec3(gl_ModelViewMatrix * position);
    normal = gl_NormalMatrix * gl_Normal;
    color = gl_Color;
}
