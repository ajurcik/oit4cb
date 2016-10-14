#version 430 core

// clipping
/*uniform bool clipCavities;
uniform bool clipSurface;
uniform uint surfaceLabel;
uniform float threshold;

// coloring by cavity area
layout(shared) uniform MinMaxCavityArea {
    float minArea;
    float maxArea;
    float max2Area;
};

uniform sampler1D areasTex;*/

in VertexData {
    // OBB
    vec3 faceVertices[3][4];
    int faceCount;
    // sphere
    flat vec4 objPos;
    flat vec4 camPos;
    flat vec4 lightPos;
    float radius;
    float RR;
    vec4 color;
    flat uint index;
    // triangle
    vec3 atomPos1;
    vec3 atomPos2;
    vec3 atomPos3;
    vec4 plane1;
    vec4 plane2;
    vec4 plane3;
} vertex[];

// sphere
out flat vec4 objPos;
out flat vec4 camPos;
out flat vec4 lightPos;
out float radius;
out float RR;
out vec4 color;
out flat uint index;
// triangle
out vec3 atomPos1;
out vec3 atomPos2;
out vec3 atomPos3;
out vec4 plane1;
out vec4 plane2;
out vec4 plane3;

layout(points) in;
layout(triangle_strip, max_vertices = 12) out;

void main() {
    if (vertex[0].faceCount > 3) {
        // discard invalid boxes
        return;
    }

    objPos = vertex[0].objPos;
    camPos = vertex[0].camPos;
    lightPos = vertex[0].lightPos;
    radius = vertex[0].radius;
    RR = vertex[0].RR;
    color = vertex[0].color;
    index = vertex[0].index;
    atomPos1 = vertex[0].atomPos1;
    atomPos2 = vertex[0].atomPos2;
    atomPos3 = vertex[0].atomPos3;
    plane1 = vertex[0].plane1;
    plane2 = vertex[0].plane2;
    plane3 = vertex[0].plane3;

    for (int f = 0; f < vertex[0].faceCount; f++) {
        gl_Position = vec4(vertex[0].faceVertices[f][0], 1.0);
        EmitVertex();
        gl_Position = vec4(vertex[0].faceVertices[f][1], 1.0);
        EmitVertex();
        gl_Position = vec4(vertex[0].faceVertices[f][2], 1.0);
        EmitVertex();
        gl_Position = vec4(vertex[0].faceVertices[f][3], 1.0);
        EmitVertex();
        EndPrimitive();
    }
}
