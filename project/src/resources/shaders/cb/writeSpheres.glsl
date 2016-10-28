#version 430 core

#define WRITTEN 1

struct sphere {
    vec4 position;
    uint index;
    uint label;
    uint circleStart;
    uint circleLength;
};

struct cap {
    vec4 position;
    vec4 plane;
    uint atomIdx;
    uint label;
    uint padding0; // std430 aligns structs using biggest element
    uint padding1;
};

uniform uint atomCount;
uniform uint circleCount;
uniform uint outerLabel;

uniform samplerBuffer atomsTex;
uniform usamplerBuffer circlesTex;
uniform usamplerBuffer circlesLengthTex;
uniform usamplerBuffer circlesStartTex;
uniform usamplerBuffer labelsTex;

layout(std430) buffer Spheres {
    sphere spheres[];
};

layout(std430) buffer Caps {
    cap caps[];
};

layout(std430) buffer PatchCounts {
    uint patchCounts[];
};

layout(std430) buffer CountersBuffer {
    uint sphereCount;
    uint capCount;
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
        uint label = texelFetch(labelsTex, int(edge.x)).r; // v0
        
        // remove duplicate outer polygons
        uint atomIdx = edge.w;
        if (label == outerLabel) {
            //uint status = atomicExchange(patchCounts[atomIdx], WRITTEN);
            uint status = atomicAdd(patchCounts[atomIdx], 1);
            if (status >= WRITTEN) {
                // polygon was already written
                return;
            }
        }

        if (label == outerLabel) {
            // write polygon
            uint sphereIdx = atomicAdd(sphereCount, 1);
            // position, atom, label and circle
            spheres[sphereIdx].position = texelFetch(atomsTex, int(atomIdx));
            spheres[sphereIdx].index = atomIdx;
            spheres[sphereIdx].label = label;
            spheres[sphereIdx].circleStart = start;
            spheres[sphereIdx].circleLength = len;
        } else {
            // write cap (cavity patch)
            uint capIdx = atomicAdd(capCount, 1);
            // position, cap start+len, atom and label
            caps[capIdx].position = texelFetch(atomsTex, int(atomIdx));
            caps[capIdx].plane.x = uintBitsToFloat(start);
            caps[capIdx].plane.y = uintBitsToFloat(len);
            caps[capIdx].atomIdx = atomIdx;
            caps[capIdx].label = label;
        }
    }
}
