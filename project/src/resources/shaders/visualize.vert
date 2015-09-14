#version 430 compatibility

out flat uvec3 index;

void main()
{
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
    index = uvec3(gl_Vertex);
}