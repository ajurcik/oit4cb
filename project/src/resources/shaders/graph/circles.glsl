#version 430 core

const uint INVALID_VERTEX = 0xffffffff;

uniform uint circleCount;

layout(std430) buffer Counts {
    uint totalCircleCount;
};

layout(std430) buffer Circles {
    uvec4 circles[][32];
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
    uint start[8];
    start[0] = 0;
    
    uint current = circles[index][0].x;
    for (uint j = 0; j < circleLength - 1; j++) {
        uint k;
        for (k = j + 1; k < circleLength; k++) {
            uint v0 = circles[index][k].x;
            uint v1 = circles[index][k].y;
            if (current == v0) {
                current = v1;
                break;
            } else if (current == v1) {
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
    uint circlesOffset = index * 32;
    uint polygonsOffset = index * 8;
    
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
