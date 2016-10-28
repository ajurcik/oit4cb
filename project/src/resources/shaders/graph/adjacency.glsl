#version 430 core

#define MAX_SPHERE_EDGES 64 // FIXME should be set by app

const uint INVALID_VERTEX = 0xffffffff;

uniform uint edgeCount;

layout(std430) buffer Edges {
    uvec4 edges[]; // TODO struct
};

layout(std430) buffer Adjacency {
    uint rows[][4];
};

layout(std430) buffer Circles {
    uvec4 circles[][MAX_SPHERE_EDGES]; // TODO struct
};

layout(std430) buffer CirclesLength {
    uint circlesLength[];
};

layout (local_size_x = 64) in;

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= edgeCount) {
        return;
    }

    uvec4 edge = edges[index];
    uint v0 = edge.x;
    uint v1 = edge.y;
    if (v0 == INVALID_VERTEX || v1 == INVALID_VERTEX) {
        return;
    }

    // set adjacency matrix
    uint v0Idx = atomicAdd(rows[v0][0], 1);
    uint v1Idx = atomicAdd(rows[v1][0], 1);
    rows[v0][v0Idx + 1] = v1;
    rows[v1][v1Idx + 1] = v0;
    
    // initialize circles
    uint edgeIdx0 = atomicAdd(circlesLength[edge.z], 1);
    uint edgeIdx1 = atomicAdd(circlesLength[edge.w], 1);
    circles[edge.z][edgeIdx0] = uvec4(edge.xy, index, edge.z);
    circles[edge.w][edgeIdx1] = uvec4(edge.xy, index, edge.w);
}
