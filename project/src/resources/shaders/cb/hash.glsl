#version 430 core

uniform uint sphereCount;
uniform uint maxNumSpheres;
uniform uint gridSize;
uniform float cellSize;

layout(std430) buffer Spheres { 
    vec4 positions[];
};

layout(std430) buffer Counts {
    uint counts[];
};

layout(std430) buffer Indices {
    uint indices[];
};

layout(local_size_x = 64) in;

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= sphereCount) {
        return;
    }

    vec3 pos = positions[index].xyz;
    uvec3 gridPos = uvec3(floor(pos / cellSize));
    uint hash = gridSize * gridSize * gridPos.x + gridSize * gridPos.y + gridPos.z;

    uint count = atomicAdd(counts[hash], 1);
    if (count >= maxNumSpheres) {
        return;
    }
    indices[hash * maxNumSpheres + count] = index;
}