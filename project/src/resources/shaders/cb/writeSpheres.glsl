#version 430 core

struct sphere {
    vec4 position;
    uint index;
    uint label;
    uint circleStart;
    uint circleLength;
};

uniform uint atomCount;
uniform uint circleCount;
uniform uint maxSphereCavityCount;
uniform uint outerLabel;

uniform samplerBuffer atomsTex;
uniform usamplerBuffer circlesTex;
uniform usamplerBuffer circlesLengthTex;
uniform usamplerBuffer circlesStartTex;
uniform usamplerBuffer labelsTex;

layout(std430) buffer Spheres {
    sphere spheres[];
};

layout(std430) buffer CountersBuffer {
    uint polygonCount;
    uint cavityCount;
};

layout(std430) buffer CavityCounts {
    uint cavityCounts[];
};

layout(std430) buffer CavityCircles {
    uvec2 cavityCircles[];
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
        uint label = texelFetch(labelsTex, int(edge.x)).r; // v0
        uint polygonIdx = atomicAdd(polygonCount, 1);
        // position, atom and circle
        spheres[polygonIdx].position = texelFetch(atomsTex, int(atomIdx));
        spheres[polygonIdx].index = atomIdx;
        spheres[polygonIdx].label = label;
        spheres[polygonIdx].circleStart = start;
        spheres[polygonIdx].circleLength = len;
        // write cap if cavity patch
        if (label != outerLabel) {
            uint cavityIdx = atomicAdd(cavityCounts[atomIdx], 1);
            cavityCircles[atomIdx * maxSphereCavityCount + cavityIdx] = uvec2(start, len);
            atomicAdd(cavityCount, 1);
        }
    }
}
