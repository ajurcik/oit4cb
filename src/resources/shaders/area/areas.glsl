#version 430 core

struct triangle {
    vec4 probePos;
    vec4 atomVec1;
    vec4 atomVec2;
    vec4 atomVec3;
};

uniform uint triangleCount;

layout(std430) buffer Triangles { 
    triangle triangles[];
};

/*layout(std430) buffer Areas { 
    uint totalAreas[];
};*/

uniform usamplerBuffer labelsTex;
uniform layout(r32ui) uimage1D areasImg;

layout(local_size_x = 256) in;

shared float areas[gl_WorkGroupSize.x];
shared uint labels[gl_WorkGroupSize.x];
shared float sumAreas[gl_WorkGroupSize.x];

void main() {
    uint index = gl_GlobalInvocationID.x;
    uint threadID = gl_LocalInvocationIndex;
    
    if (index < triangleCount) {
        vec3 A = normalize(triangles[index].atomVec1.xyz);
        vec3 B = normalize(triangles[index].atomVec2.xyz);
        vec3 C = normalize(triangles[index].atomVec3.xyz);
        vec3 nCA = cross(C, A);
        vec3 nBA = cross(B, A);
        vec3 nCB = cross(C, B);
        float angA = dot(nBA, nCA);
        float angB = dot(-nBA, nCB);
        float angC = dot(-nCA, -nCB);
        float triangleArea = acos(angA) + acos(angB) + acos(angC) - 3.1415926;
        uint labelIdx = texelFetch(labelsTex, int(index)).r;
        areas[threadID] = triangleArea;
        labels[threadID] = labelIdx;
    } else {
        labels[threadID] = 0; // mask off threads without triangles
    }

    barrier();
    memoryBarrierShared(); // sync areas and labels

    for (uint label = 1; label <= 64; label++) {
        if (labels[threadID] == label) {
            sumAreas[threadID] = areas[threadID];
        } else {
            sumAreas[threadID] = 0.0;
        }

        barrier();
        memoryBarrierShared(); // sync sumAreas

        // parallel reduce - compute sum
        for (uint s = gl_WorkGroupSize.x / 2; s > 0; s >>= 1) {
            if (threadID < s) {
                sumAreas[threadID] += sumAreas[threadID + s];
            }
            barrier();
            memoryBarrierShared(); // sync sumAreas
        }

        if (threadID == 0 && sumAreas[0] > 0.0) {
            //uint old = totalAreas[label - 1];
            uint old = imageLoad(areasImg, int(label - 1)).r;
            uint assumed;
            do {
                assumed = old;
                float area = uintBitsToFloat(assumed) + sumAreas[0];
                //old = atomicCompSwap(totalAreas[label - 1], assumed, floatBitsToUint(area));
                old = imageAtomicCompSwap(areasImg, int(label - 1), assumed, floatBitsToUint(area));
            } while (assumed != old);
        }
    }
}