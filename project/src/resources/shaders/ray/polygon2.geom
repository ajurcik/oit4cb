#version 430 core

// clipping
uniform bool clipCavities;
uniform bool clipSurface;
uniform uint surfaceLabel;
uniform float threshold;

// coloring by cavity area
layout(shared) uniform MinMaxCavityArea {
    float minArea;
    float maxArea;
    float max2Area;
};

uniform sampler1D areasTex;

in VertexData {
    // ray-casting
    vec4 objPos;
    vec4 camPos;
    vec4 lightPos;
    float radius;
    float RR;
    vec4 color;
    // polygon
    flat uint index; // atom
    flat uint label;
    flat uint circleStart;
    flat uint circleEnd;
    flat vec4 plane;
} vertex[];

// ray-casting
out vec4 objPos;
out vec4 camPos;
out vec4 lightPos;
out float radius;
out float RR;
out vec4 color;
// polygon
out flat uint index; // atom
out flat uint label;
out flat uint circleStart;
out flat uint circleEnd;
// isolated torus plane
out flat vec4 plane;
// area
out flat float area;

layout(points) in;
layout(points, max_vertices = 1) out;

void main() {
    bool surface = vertex[0].label == surfaceLabel;
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
    index = vertex[0].index;
    label = vertex[0].label;
    circleStart = vertex[0].circleStart;
    circleEnd = vertex[0].circleEnd;
    plane = vertex[0].plane;
    
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

    gl_Position = gl_in[0].gl_Position;
    gl_PointSize = gl_in[0].gl_PointSize;
    
    EmitVertex();
    EndPrimitive();
}
