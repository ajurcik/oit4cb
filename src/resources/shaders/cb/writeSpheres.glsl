#version 430 core

struct sphere {
    vec4 position;
    uint index;
    uint label;
    uint circleStart;
    uint circleLength;
    vec4 plane;
};

uniform uint atomCount;
uniform uint circleCount;

uniform samplerBuffer atomsTex;
uniform usamplerBuffer circlesTex;
uniform usamplerBuffer circlesLengthTex;
uniform usamplerBuffer circlesStartTex;
uniform usamplerBuffer labelsTex;
uniform samplerBuffer polygonsPlanesTex;

layout(std430) buffer Spheres {
    sphere spheres[];
};

layout(std430) buffer CountersBuffer {
    uint polygonCount;
};

layout (local_size_x = 64) in;

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= circleCount) {
        return;
    }

    uint len = texelFetch(circlesLengthTex, int(index)).x;
    if (len > 0) {
        // get sphere index
        uint start = texelFetch(circlesStartTex, int(index)).x;
        uvec4 edge = texelFetch(circlesTex, int(start));
        // write polygon
        uint atomIdx = edge.w;
        uint polygonIdx = atomicAdd(polygonCount, 1);
        // position, atom and circle
        spheres[polygonIdx].position = texelFetch(atomsTex, int(atomIdx));
        spheres[polygonIdx].index = atomIdx;
        spheres[polygonIdx].label = texelFetch(labelsTex, int(edge.x)).r; // v0
        spheres[polygonIdx].circleStart = start;
        spheres[polygonIdx].circleLength = len;
        // isolated torus clipping plane
        spheres[polygonIdx].plane = texelFetch(polygonsPlanesTex, int(index));
    }
}
