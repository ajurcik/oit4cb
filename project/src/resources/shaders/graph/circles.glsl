#version 430 core

#define MAX_SPHERE_EDGES 64 // FIXME should be set by app

const uint INVALID_VERTEX = 0xffffffff;

uniform uint circleCount;
uniform uint maxSphereEdges;
uniform uint maxSpherePolygons;

layout(std430) buffer Counts {
    uint totalCircleCount;
};

layout(std430) buffer Circles {
    uvec4 circles[][MAX_SPHERE_EDGES];
};

layout(std430) buffer CirclesCount {
    uint circlesCount[];
};

layout(std430) buffer CirclesLength {
    uint circlesLength[];
};

layout(std430) buffer CirclesStart {
    uint circlesStart[];
};

layout(std430) buffer SpherePolygons {
    uint polygons[];
};

layout (local_size_x = 64) in;

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= circleCount) {
        return;
    }

    uint circleLength = circlesLength[index];
    if (circleLength == 0) {
        return;
    }

    // find all circles
    uint count = 1;
    uint start[16]; // FIXME it seems that there are too many circles for atom 260 in 1AF6
    start[0] = 0;
    
    uint current = circles[index][0].y;
    for (uint j = 0; j < circleLength - 1; j++) {
        uint k;
        for (k = j + 1; k < circleLength; k++) {
            uint v0 = circles[index][k].x;
            uint v1 = circles[index][k].y;
            if (current == v0) {
                current = v1;
                break;
            } else if (current == v1) {
                circles[index][k].xy = ivec2(v1, v0);
                current = v0;
                break;
            }
        }
        if (k == circleLength) {
            // start another circle for sphere
            start[count] = j + 1;
            current = circles[index][j + 1].y;
            count++;
        }
        if (k < circleLength && k != j + 1) {
            // swap edges
            uvec4 tmpEdge = circles[index][j + 1];
            circles[index][j + 1] = circles[index][k];
            circles[index][k] = tmpEdge;
        }
    }
    
    start[count] = circleLength;

    // mark circle starts
    uint circlesOffset = index * maxSphereEdges;
    uint polygonsOffset = index * maxSpherePolygons;
    
    circlesCount[index] = count;    
    
    // write starts, lengths and polygons for spheres
    circlesStart[index] = circlesOffset;
    circlesLength[index] = start[1];
    polygons[polygonsOffset] = index;
    for (uint i = 1; i < count; i++) {
        uint circleIdx = atomicAdd(totalCircleCount, 1);
        circlesStart[circleIdx] = circlesOffset + start[i];
        circlesLength[circleIdx] = start[i + 1] - start[i];
        polygons[polygonsOffset + i] = circleIdx;
    }
}
