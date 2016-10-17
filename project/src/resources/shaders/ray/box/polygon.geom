#version 430 core

// clipping
uniform bool clipCavities;
uniform bool clipSurface;
uniform uint outerLabel;
uniform float threshold;

// coloring by cavity area
layout(shared) uniform MinMaxCavityArea {
    float minArea;
    float maxArea;
    float max2Area;
};

uniform sampler1D areasTex;

in VertexData {
    // OBB
    vec3 faceVertices[3][4];
    int faceCount;
    // ray-casting
    vec4 objPos;
    vec4 camPos;
    vec4 lightPos;
    float radius;
    float RR;
    vec4 color;
    vec4 plane;
    // polygon
    flat uint index; // atom
    flat uint label;
    flat uint circleStart;
    flat uint circleEnd;
    flat uint orientation;
} vertex[];

// ray-casting
out vec4 objPos;
out vec4 camPos;
out vec4 lightPos;
out float radius;
out float RR;
out vec4 color;
out vec4 plane;
// polygon
out flat uint index; // atom
out flat uint label;
out flat uint circleStart;
out flat uint circleEnd;
out flat uint orientation;
// area
out flat float area;

layout(points) in;
layout(triangle_strip, max_vertices = 12) out;

void main() {
    if (vertex[0].faceCount > 3) {
        // discard invalid boxes
        return;
    }

    bool surface = vertex[0].label == outerLabel;
    if (clipSurface && surface) {
        return;
    }
    if (clipCavities && !surface) {
        return;
    }

    objPos = vertex[0].objPos;
    camPos = vertex[0].camPos;
    lightPos = vertex[0].lightPos;
    radius = vertex[0].radius;
    RR = vertex[0].RR;
    color = vertex[0].color;
    plane = vertex[0].plane;
    index = vertex[0].index;
    label = vertex[0].label;
    circleStart = vertex[0].circleStart;
    circleEnd = vertex[0].circleEnd;
    orientation = vertex[0].orientation;
    
    // area
    area = texelFetch(areasTex, int(label - 1), 0).r;
    // cavity thresholding
    if (area < threshold) {
        return;
    }
    if (abs(max2Area - minArea) > 0.005) {
        area = (area - minArea) / (max2Area - minArea);
    } else {
        area = 1.0;
    }

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
