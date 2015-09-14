#version 430 core

const uint UNLABELLED = 0;
const uint INVALID_VERTEX = 0xffffffff;

uniform uint vertexCount;

shared bool done;
shared uint start;
shared uint iteration;
shared uint label;
shared uint indices[64];

layout(std430) buffer Adjacency {
    uint rows[][4];
};

layout(std430) buffer Verts {
    uvec2 vertices[];
};

layout(std430) buffer VertsLabel {
    uint verticesLabel[];
};

layout(std430) buffer Labels {
    uint largest;
    uint labels[];
};

layout (local_size_x = 64) in;

void connectedComponent(uint label);
void unlabelledVertex();

void main() {
    uint threadID = gl_LocalInvocationID.x; // only local ID!
    if (threadID >= vertexCount) {
        return;
    }

    // initialization
    if (threadID == 0) {
        start = 0;
        iteration = 0;
        label = 1;
        vertices[start] = uvec2(iteration, label); // start vertex
    }

    // wait for control thread
    barrier();
    memoryBarrierBuffer(); // sync vertices buffer
    memoryBarrierShared(); // sync iteration, label variables

    do {
        connectedComponent(label);
        unlabelledVertex();
        if (start != INVALID_VERTEX) {
            if (threadID == 0) {
                label++;
                vertices[start] = uvec2(iteration, label);
            }
            
            // wait for control thread
            barrier();
            memoryBarrierBuffer(); // sync vertices buffer
            memoryBarrierShared(); // sync iteration, label variables
        }
    } while (start != INVALID_VERTEX);

    // count labels
    uint index = threadID;
    while (index < vertexCount) {
        uint label = vertices[index].y;
        verticesLabel[index] = label;
        atomicAdd(labels[label - 1], 1);
        index += gl_WorkGroupSize.x;
    }

    // find maximum
    barrier();
    memoryBarrierBuffer();

    if (threadID < 64) {
        indices[threadID] = threadID;
    }

    barrier();
    memoryBarrierShared();

    for (uint s = 64/2; s > 0; s >>= 1) {
        if (threadID < s) {
            if (labels[threadID] < labels[threadID + s]) {
                labels[threadID] = labels[threadID + s];
                indices[threadID] = indices[threadID + s];
            }
        }
        barrier();
        memoryBarrierBuffer();
    }

    if (threadID == 0) {
        largest = indices[0] + 1;
    }

    // write labels
    //verticesLabel[index] = label; /*vertices[index].y*/ // bug???
}

void connectedComponent(uint label) {
    uint threadID = gl_LocalInvocationID.x; // only local ID!
    do {
        // wait for other threads to go through the while condition
        barrier();
        memoryBarrierShared(); // sync iteration variable

        if (threadID == 0) {
            done = true;
        }

        // wait for control thread to set done
        barrier();

        uint index = threadID;
        while (index < vertexCount) {
            if (vertices[index].x == iteration) {
                uint v0 = rows[index][1];
                uint v1 = rows[index][2];
                uint v2 = rows[index][3];
                if (vertices[v0].y == UNLABELLED) {
                    vertices[v0] = uvec2(iteration + 1, label);
                    done = false;
                }
                if (vertices[v1].y == UNLABELLED) {
                    vertices[v1] = uvec2(iteration + 1, label);
                    done = false;
                }
                if (vertices[v2].y == UNLABELLED) {
                    vertices[v2] = uvec2(iteration + 1, label);
                    done = false;
                }
            }
            index += gl_WorkGroupSize.x;
        }

        // wait for all threads to do iteration
        barrier();
        memoryBarrierBuffer(); // sync vertices buffer
        memoryBarrierShared(); // sync done variable
        
        if (threadID == 0) {
            iteration++;
        }
    } while (!done);
}

void unlabelledVertex() {
    uint index = gl_LocalInvocationID.x; // only local ID!
    
    if (index == 0) {
        start = INVALID_VERTEX;
    }

    // wait for control thread to reset start variable
    barrier();

    // find next start vertex
    while (index < vertexCount) {
        if (vertices[index].y == UNLABELLED) {
            start = index;
        }
        index += gl_WorkGroupSize.x;
    }

    // wait for all threads to scan vertices
    barrier();
    memoryBarrierShared(); // sync start variable
}