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
    // OBB
    vec2 faceVertices[3][4];
    int faceCount;
    // ray-casting
    vec4 objPos;
    vec4 camPos;
    vec4 lightPos;
    vec4 radii;
    vec4 visibilitySphere;
    vec4 plane1;
    vec4 plane2;
    flat uint operation;
    vec4 color;
    // rotation
    vec3 rotMatT0;
    vec3 rotMatT1;
    vec3 rotMatT2;
    // torus
    //flat uint index;
    flat uint label;
} vertex[];

out vec4 objPos;
out vec4 camPos;
out vec4 lightPos;
out vec4 radii;
out vec4 visibilitySphere;
out vec4 plane1;
out vec4 plane2;
out flat uint operation;
//out flat uint index;
out vec4 color;

out vec3 rotMatT0;
out vec3 rotMatT1;
out vec3 rotMatT2;

out flat uint label;

// area
out flat float area;

layout(points) in;
layout(triangle_strip, max_vertices = 12) out;

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
    radii = vertex[0].radii;
    visibilitySphere = vertex[0].visibilitySphere;
    plane1 = vertex[0].plane1;
    plane2 = vertex[0].plane2;
    operation = vertex[0].operation;
    color = vertex[0].color;
    rotMatT0 = vertex[0].rotMatT0;
    rotMatT1 = vertex[0].rotMatT1;
    rotMatT2 = vertex[0].rotMatT2;
    label = vertex[0].label;

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
        gl_Position = vec4(vertex[0].faceVertices[f][0], 0.0, 1.0);    
        EmitVertex();
        gl_Position = vec4(vertex[0].faceVertices[f][1], 0.0, 1.0);    
        EmitVertex();
        gl_Position = vec4(vertex[0].faceVertices[f][2], 0.0, 1.0);    
        EmitVertex();
        gl_Position = vec4(vertex[0].faceVertices[f][3], 0.0, 1.0);    
        EmitVertex();
        EndPrimitive();
    }
}
